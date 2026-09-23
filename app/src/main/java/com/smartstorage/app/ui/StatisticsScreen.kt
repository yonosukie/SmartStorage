package com.smartstorage.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartstorage.core.*

@Composable fun StatisticsScreen(s: Inventory, onFilter: (Filter) -> Unit) {
    var house by remember { mutableStateOf("") }
    var measure by remember { mutableStateOf("数量") }
    val today = LocalToday.current
    val scope = Filter(place = house.ifBlank { null })
    val rows = s.filtered(scope, today)
    val categories = rows.groupBy { it.item.category }
    val rooms = s.places.filter { it.kind == "ROOM" }.associate { it.id to s.descendants(it.id) }
    val locations = rows.groupBy { row -> rooms.entries.firstOrNull { row.balance.placeId in it.value }?.key ?: row.balance.placeId }
    val statuses = rows.distinctBy { it.batch.id }.groupingBy { row -> when {
        row.expired(today) -> "已过期"
        row.batch.expires == null -> "未设置"
        row.due(s.preferences.leadDays, today) -> "即将到期"
        else -> "有效期内"
    } }.eachCount()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Choice("查看范围", house, listOf("" to "全部房子") + s.places.filter { it.kind == "HOUSE" }.map { it.id to it.name }) { house = it } }
        item { ExpiryStatusChart(statuses) { onFilter(scope.copy(expiry = it)) } }
        item {
            Chips(listOf("数量", "价值"), measure) { measure = it }
            Spacer(Modifier.height(8.dp))
            if (measure == "数量") QuantityColumns(categories.map { it.key to it.value.sumOf { row -> row.balance.quantity.toLong() } }) {
                onFilter(scope.copy(category = it))
            } else DistributionChart("分类价值分布", categories.map { it.key to it.value.sumOf { row -> row.value } }, currency = true,
                subtitle = "按已知购入成本统计，未填价格不计入。", onDetails = { onFilter(scope.copy(category = it)) })
        }
        item { RoomBars(locations.map { (id, stock) -> RoomChartEntry(id, s.path(id), stock.sumOf { it.balance.quantity.toLong() }) }) {
            onFilter(scope.copy(place = it))
        } }
    }
}
