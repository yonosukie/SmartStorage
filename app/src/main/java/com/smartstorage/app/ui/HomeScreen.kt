package com.smartstorage.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartstorage.core.*

@Composable fun HomeScreen(s: Inventory, openItem: (String) -> Unit, onFilter: (Filter) -> Unit, stats: () -> Unit, templates: () -> Unit) {
    var house by remember { mutableStateOf("") }
    val today = LocalToday.current
    val rows = s.filtered(Filter(place = house.ifBlank { null }), today)
    val due = rows.filter { it.due(s.preferences.leadDays, today) }; val expired = rows.filter { it.expired(today) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("让生活，井井有条。", "记得每一件好物，也记得好好使用它。") }
        item { Choice("查看范围", house, listOf("" to "全部房子") + s.places.filter { it.kind == "HOUSE" }.map { it.id to it.name }) { house = it } }
        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("家里的好物", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                Text("${rows.map { it.item.id }.distinct().size} 种", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("已知库存价值  ¥${money(rows.sumOf { it.value })}")
                Text("${rows.filter { it.batch.price == null }.map { it.item.id }.distinct().size} 种物品有未填写价格的库存", style = MaterialTheme.typography.bodySmall)
            }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("${due.map { it.batch.id }.distinct().size}", "临期批次", Modifier.weight(1f))
            Metric("${expired.map { it.batch.id }.distinct().size}", "过期批次", Modifier.weight(1f))
            Metric("${s.rows().filter { it.balance.placeId == UNPLACED && it.balance.quantity > 0 }.map { it.item.id }.distinct().size}", "待归位物品", Modifier.weight(1f))
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(stats, Modifier.weight(1f)) { Icon(Icons.Outlined.PieChart, null); Spacer(Modifier.width(6.dp)); Text("物品统计") }
            FilledTonalButton(templates, Modifier.weight(1f)) { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("收纳方案") }
        } }
        item { OutlinedButton({ onFilter(Filter()) }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Search, null); Text("找一件物品", Modifier.padding(8.dp)) } }
        item { Row { SectionTitle("保质期备忘"); Spacer(Modifier.weight(1f)); TextButton({ onFilter(Filter(place = house.ifBlank { null }, expiry = "即将到期")) }) { Text("查看全部") } } }
        if (due.isEmpty() && expired.isEmpty()) item { EmptyState("暂时没有需要提醒的物品", "填写批次到期日，临期和过期物品会显示在这里。") }
        items((expired + due).sortedBy { it.batch.expires }.groupBy { it.item.id }.values.take(5)) { ItemCard(s, it, { openItem(it.first().item.id) }) }
        item { SectionTitle("最近记录", "一点点记录，让家变得更好找") }
        if (rows.isEmpty()) item { EmptyState("从第一件物品开始", "食品、护肤品、衣物……把身边的物品记下来。") }
        items(rows.groupBy { it.item.id }.values.sortedByDescending { it.first().item.createdAt }.take(5)) { ItemCard(s, it, { openItem(it.first().item.id) }) }
    }
}
