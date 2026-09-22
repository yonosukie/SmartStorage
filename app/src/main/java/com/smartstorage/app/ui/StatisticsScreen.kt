package com.smartstorage.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartstorage.core.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable fun StatisticsScreen(s: Inventory, onFilter: (Filter) -> Unit) {
    var house by remember { mutableStateOf("") }; var dimension by remember { mutableStateOf("分类") }
    val today = LocalToday.current
    val rows = s.filtered(Filter(place = house.ifBlank { null }), today)
    val valuable = rows.filter { it.item.valuable }; val expired = rows.filter { it.expired(today) }
    val groups = when(dimension) {
        "房间" -> rows.groupBy { row -> s.places.firstOrNull { it.kind == "ROOM" && row.balance.placeId in s.descendants(it.id) }?.let { s.path(it.id) } ?: "待归位" }
        "标签" -> s.labels.associate { label -> label.name to rows.filter { label.id in it.item.tags } }
        else -> rows.groupBy { it.item.category }
    }
    val held = rows.groupBy { row -> row.batch.purchased?.let {
        val days = ChronoUnit.DAYS.between(LocalDate.parse(it), today)
        when { days < 0 -> "未来购买日期"; days < 30 -> "不足 30 天"; days < 180 -> "30–179 天"; days < 365 -> "180–364 天"; else -> "365 天以上" }
    } ?: "购买日期未知" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Choice("查看范围", house, listOf("" to "全部房子") + s.places.filter { it.kind == "HOUSE" }.map { it.id to it.name }) { house = it } }
        item { Metric("¥${money(rows.sumOf { it.value })}", "当前库存价值 · 已知购入成本", Modifier.fillMaxWidth())
            Text("${rows.filter { it.batch.price == null }.map { it.item.id }.distinct().size} 种物品有缺价库存；不计入上面的金额。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        item { SectionTitle("库存概览"); Text("${rows.map { it.item.id }.distinct().size} 种在库物品")
            Text(rows.groupBy { it.item.unit }.map { (unit, group) -> "${group.sumOf { it.balance.quantity.toLong() }} $unit" }.joinToString(" · ").ifEmpty { "暂无库存" }) }
        item { Chips(listOf("分类", "房间", "标签"), dimension) { dimension = it }
            if (dimension == "标签") Text("同一物品可有多个标签，各标签价值不可相加。", style = MaterialTheme.typography.bodySmall) }
        groups.entries.sortedByDescending { it.value.sumOf { row -> row.value } }.forEach { (name, group) -> item(key = "group-$name") {
            val amount = group.sumOf { it.value }; val total = rows.sumOf { it.value }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row { Text(name, Modifier.weight(1f)); Text("¥${money(amount)}") }
                LinearProgressIndicator(progress = { if (total == 0L) 0f else (amount.toFloat()/total).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth())
                Text("${group.map { it.item.id }.distinct().size} 种物品", style = MaterialTheme.typography.bodySmall)
                TextButton({
                    val base = Filter(place = house.ifBlank { null })
                    onFilter(when(dimension) {
                        "分类" -> base.copy(category = name)
                        "标签" -> base.copy(tags = s.labels.filter { it.name == name }.map { it.id }.toSet())
                        else -> base.copy(place = s.places.firstOrNull { s.path(it.id) == name }?.id ?: UNPLACED)
                    })
                }) { Text("查看明细") }
            }
        } }
        item { SectionTitle("贵重物品"); Text("${valuable.map { it.item.id }.distinct().size} 种 · 已知成本 ¥${money(valuable.sumOf { it.value })}")
            TextButton({ onFilter(Filter(place = house.ifBlank { null }, valuable = true)) }) { Text("查看贵重物品") } }
        item { SectionTitle("拥有时间", "按当前仍在库的购买批次统计") }
        held.forEach { (name, group) -> item(key = "held-$name") { Text("$name · ${group.map { it.batch.id }.distinct().size} 个批次 · ¥${money(group.sumOf { it.value })}") } }
        item { SectionTitle("过期物品"); Text("${expired.map { it.batch.id }.distinct().size} 个批次 · 已知成本 ¥${money(expired.sumOf { it.value })}")
            Text(expired.groupBy { it.item.unit }.map { (unit, group) -> "${group.sumOf { it.balance.quantity.toLong() }} $unit" }.joinToString(" · "))
            TextButton({ onFilter(Filter(place = house.ifBlank { null }, expiry = "已过期")) }) { Text("查看并处理") }
            val allowed = house.ifBlank { null }?.let(s::descendants)
            val loss = s.movements.filter { it.reason == "过期丢弃" && s.movements.none { rev -> rev.reversedId == it.id } && (allowed == null || it.from in allowed) }
            Text("历史过期丢弃：已知成本 ¥${money(loss.sumOf { (it.price ?: 0) * it.quantity })}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
