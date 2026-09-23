package com.smartstorage.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartstorage.core.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable fun StatisticsScreen(s: Inventory, onFilter: (Filter) -> Unit) {
    var house by remember { mutableStateOf("") }
    var dimension by remember { mutableStateOf("分类") }
    val today = LocalToday.current
    val scope = Filter(place = house.ifBlank { null })
    val rows = s.filtered(scope, today)
    val batches = rows.distinctBy { it.batch.id }
    val groups = when (dimension) {
        "房间" -> rows.groupBy { row -> s.places.firstOrNull { it.kind == "ROOM" && row.balance.placeId in s.descendants(it.id) }?.let { s.path(it.id) } ?: "待归位" }
        "标签" -> s.labels.associate { label -> label.name to rows.filter { label.id in it.item.tags } }
        else -> rows.groupBy { it.item.category }
    }
    fun detail(name: String) { onFilter(when (dimension) {
        "分类" -> scope.copy(category = name)
        "标签" -> scope.copy(tags = s.labels.filter { it.name == name }.map { it.id }.toSet())
        else -> scope.copy(place = s.places.firstOrNull { s.path(it.id) == name }?.id ?: UNPLACED)
    }) }
    val overlap = if (dimension == "标签") "同一物品可属于多个标签，图中合计为标签累计值，不等于库存总计。" else "仅统计当前在库物品。"
    val held = rows.groupBy { row -> row.batch.purchased?.let {
        val days = ChronoUnit.DAYS.between(LocalDate.parse(it), today)
        when { days < 0 -> "未来购买日期"; days < 30 -> "不足 30 天"; days < 180 -> "30–179 天"; days < 365 -> "180–364 天"; else -> "365 天以上" }
    } ?: "购买日期未知" }
    val statuses = batches.groupBy { row -> when {
        row.expired(today) -> "已过期"
        row.batch.expires == null -> "未设置有效期"
        row.due(s.preferences.leadDays, today) -> "即将到期"
        else -> "有效期内"
    } }
    val valuables = rows.groupBy { if (it.item.valuable) "贵重物品" else "普通物品" }
    val expired = rows.filter { it.expired(today) }
    val allowed = scope.place?.let(s::descendants)
    val reversals = s.movements.mapNotNull { it.reversedId }.toSet()
    val losses = s.movements.filter { it.reason == "过期丢弃" && it.id !in reversals && (allowed == null || it.from in allowed) }
        .groupBy { Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate().toString().take(7) }.toSortedMap()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Choice("查看范围", house, listOf("" to "全部房子") + s.places.filter { it.kind == "HOUSE" }.map { it.id to it.name }) { house = it } }
        item { Chips(listOf("分类", "房间", "标签"), dimension) { dimension = it } }
        item { DistributionChart("${dimension}价值分布", groups.map { it.key to it.value.sumOf { row -> row.value } }, currency = true,
            donut = dimension != "标签", subtitle = "$overlap 金额为已知购入成本，缺价不计入。", onDetails = ::detail) }
        item { DistributionChart("${dimension}库存数量", groups.map { it.key to it.value.sumOf { row -> row.balance.quantity.toLong() } },
            donut = false, countLabel = "", subtitle = overlap, onDetails = ::detail) }
        item { DistributionChart("${dimension}物品种类", groups.map { it.key to it.value.map { row -> row.item.id }.distinct().size.toLong() },
            countLabel = "种物品", donut = dimension != "标签", subtitle = overlap, onDetails = ::detail) }
        item { DistributionChart("价格填写情况", batches.groupBy { if (it.batch.price == null) "未填写单价" else "已填写单价" }.map { it.key to it.value.size.toLong() },
            subtitle = "按唯一在库批次统计；单价为 0 表示明确免费。") }
        item { DistributionChart("库存有效期分布", statuses.map { it.key to it.value.size.toLong() }, onDetails = { name ->
            onFilter(scope.copy(expiry = when (name) { "未设置有效期" -> "未设置"; else -> name }))
        }, subtitle = "按唯一在库批次统计；有效期内不包含即将到期。") }
        item { DistributionChart("贵重物品占比", valuables.map { it.key to it.value.map { row -> row.item.id }.distinct().size.toLong() }, countLabel = "种物品") }
        item { DistributionChart("贵重物品价值", valuables.map { it.key to it.value.sumOf { row -> row.value } }, currency = true) }
        item { DistributionChart("拥有时间分布", held.map { it.key to it.value.map { row -> row.batch.id }.distinct().size.toLong() }, donut = false,
            subtitle = "当前在库购买批次；同一批次分放多个位置仅计一次。") }
        item { DistributionChart("拥有时间价值", held.map { it.key to it.value.sumOf { row -> row.value } }, currency = true, donut = false) }
        item { DistributionChart("过期物品分类", expired.groupBy { it.item.category }.map { it.key to it.value.map { row -> row.batch.id }.distinct().size.toLong() },
            onDetails = { onFilter(scope.copy(category = it, expiry = "已过期")) }) }
        item { DistributionChart("过期库存价值", expired.groupBy { it.item.category }.map { it.key to it.value.sumOf { row -> row.value } }, currency = true,
            onDetails = { onFilter(scope.copy(category = it, expiry = "已过期")) }) }
        item { DistributionChart("历史过期损耗", losses.map { it.key to it.value.sumOf { row -> (row.price ?: 0) * row.quantity } }, currency = true, donut = false,
            subtitle = "按丢弃月份统计已知成本；已撤销操作与缺价金额不计入。", preserveOrder = true, groupLimit = 0) }
    }
}
