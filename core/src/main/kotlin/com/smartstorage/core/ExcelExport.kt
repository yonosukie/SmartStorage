package com.smartstorage.core

import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal standards-based OOXML workbook. User strings are always inlineStr, never formulas. */
object ExcelExport {
    fun write(s: Inventory, output: OutputStream, filter: Filter? = null) {
        val rows = if (filter == null) s.rows() else s.filtered(filter)
        val ids = rows.map { it.item.id }.toSet(); val labels = s.labels.associate { it.id to it.name }
        val sheets: List<Pair<String, List<List<Any?>>>> = listOf(
            "物品汇总" to (listOf(listOf("物品 ID", "名称", "分类", "标签", "当前数量", "已知成本总额（元）", "缺价数量", "贵重物品", "位置")) +
                s.items.filter { it.id in ids }.map { item -> val matches = rows.filter { it.item.id == item.id }
                    listOf(item.id, item.name, item.category, item.tags.mapNotNull { labels[it] }.joinToString("、"),
                        matches.sumOf { it.balance.quantity.toLong() }, matches.sumOf { it.value }.toBigDecimal().movePointLeft(2),
                        matches.filter { it.batch.price == null }.sumOf { it.balance.quantity.toLong() }, if (item.valuable) "是" else "否",
                        matches.map { s.path(it.balance.placeId) }.distinct().joinToString("；")) }),
            "批次与位置" to (listOf(listOf("批次 ID", "物品 ID", "名称", "位置", "数量", "单价（元）", "购买日期", "到期日", "状态")) + rows.map {
                listOf(it.batch.id, it.item.id, it.item.name, s.path(it.balance.placeId), it.balance.quantity,
                    it.batch.price?.toBigDecimal()?.movePointLeft(2), it.batch.purchased, it.batch.expires,
                    if (it.expired()) "已过期" else if (it.due(s.preferences.leadDays)) "即将到期" else if (it.batch.expires == null) "未设置" else "未到期") }),
            "库存流水" to (listOf(listOf("操作 ID", "时间", "批次 ID", "类型", "数量", "来源", "目标", "备注")) +
                s.movements.filter { op -> filter == null || rows.any { it.batch.id == op.batchId } }.map {
                    listOf(it.id, Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDateTime().toString(), it.batchId,
                        it.type, it.quantity, s.path(it.from), s.path(it.to), it.reason) }),
            "统计口径" to listOf(listOf("项目", "说明"), listOf("币种", "人民币 CNY"),
                listOf("库存价值", "当前数量 × 购入单价，仅包含已知价格部分；不是累计消费或二手估值"),
                listOf("缺价", "空单价表示未知，0 表示明确免费"), listOf("有效期", "到期当天仍有效，翌日为已过期"),
                listOf("导出范围", if (filter == null) "全部数据" else "当前筛选匹配批次"), listOf("恢复", "Excel 仅用于阅读分析，完整恢复请使用 .ssb 备份"))
        )
        ZipOutputStream(output).use { zip ->
            fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>${sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }}</Types>""")
            put("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${sheets.mapIndexed { i, sheet -> "<sheet name=\"${sheet.first}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("")}</sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheets.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" }}</Relationships>""")
            sheets.forEachIndexed { index, sheet ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet${index + 1}.xml"))
                val writer = zip.writer(Charsets.UTF_8)
                writer.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" state=\"frozen\"/></sheetView></sheetViews><sheetData>")
                sheet.second.forEachIndexed { rowNum, cells ->
                    writer.write("<row r=\"${rowNum + 1}\">")
                    cells.forEach { cell ->
                        if (cell is Number) writer.write("<c><v>$cell</v></c>")
                        else writer.write("<c t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(cell?.toString() ?: "")}</t></is></c>")
                    }; writer.write("</row>")
                }
                writer.write("</sheetData></worksheet>"); writer.flush(); zip.closeEntry()
            }
        }
    }
    private fun escape(s: String) = s.filter { it == '\n' || it == '\r' || it == '\t' || it >= ' ' }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
