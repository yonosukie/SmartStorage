package com.smartstorage.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smartstorage.app.StorageViewModel
import com.smartstorage.core.*
import java.io.File

@Composable fun ItemCard(s: Inventory, rows: List<StockRow>, onClick: () -> Unit, photoRoot: File? = null) {
    val thing = rows.first().item
    val photo = thing.photo
    val today = LocalToday.current
    val images = photoRoot ?: File(LocalContext.current.filesDir, "photos")
    val quantity = rows.sumOf { it.balance.quantity.toLong() }
    val expiry = rows.filter { it.balance.quantity > 0 }.mapNotNull { it.batch.expires }.minOrNull()
    val expired = rows.any { it.balance.quantity > 0 && it.expired(today) }
    Card(onClick, Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                if (photo != null) AsyncImage(File(images, photo), thing.name, Modifier.fillMaxWidth().height(104.dp), contentScale = if (photo.endsWith(".png", true)) ContentScale.Fit else ContentScale.Crop)
                else Box(Modifier.fillMaxWidth().height(104.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Inventory2, null, tint = MaterialTheme.colorScheme.primary) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(thing.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(rows.map { s.path(it.balance.placeId) }.distinct().joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                if (expiry != null) Text("${if(expired) "已过期" else "到期"} · $expiry", style = MaterialTheme.typography.labelSmall,
                    color = if(expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                else Text(thing.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("数量 $quantity", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable fun ItemsScreen(s: Inventory, vm: StorageViewModel, filter: Filter, onFilter: (Filter) -> Unit, openItem: (String) -> Unit) {
    var showFilters by remember { mutableStateOf(false) }
    val today = LocalToday.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let { vm.work({ vm.notify("当前筛选清单已导出") }) { vm.repository.export(it, true, "", filter) } }
    }
    val grouped = remember(s, filter, today) {
        val groups = s.filtered(filter, today).groupBy { it.item.id }.values.toList()
        when (filter.sort) {
            "名称" -> groups.sortedBy { it.first().item.name }
            "最早到期" -> groups.sortedWith(compareBy(nullsLast()) { it.mapNotNull { row -> row.batch.expires }.minOrNull() })
            "价值最高" -> groups.sortedByDescending { it.sumOf { row -> row.value } }
            else -> groups.sortedByDescending { it.first().item.createdAt }
        }
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(152.dp), modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { OutlinedTextField(filter.query, { onFilter(filter.copy(query = it)) }, Modifier.fillMaxWidth(), placeholder = { Text("搜索物品、标签或位置") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { IconButton({ showFilters = true }) { Icon(Icons.Outlined.Tune, "筛选") } }, singleLine = true, shape = MaterialTheme.shapes.large) }
        item(span = { GridItemSpan(maxLineSpan) }) { Chips(listOf("全部", "即将到期", "已过期", "未设置"), filter.expiry) { onFilter(filter.copy(expiry = it)) } }
        item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${grouped.size} 种物品", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton({ showFilters = true }) { Text("筛选与排序") }
            TextButton({ export.launch("SmartStorage-筛选清单.xlsx") }) { Text("导出") }
            if (filter != Filter()) TextButton({ onFilter(Filter()) }) { Text("清空") }
        }
            if (filter.place != null || filter.category != null || filter.tags.isNotEmpty()) Text(
                listOfNotNull(filter.place?.let { s.path(it) }, filter.category,
                    filter.tags.takeIf { it.isNotEmpty() }?.let { tags -> s.labels.filter { it.id in tags }.joinToString("、") { it.name } }).joinToString(" · "), style = MaterialTheme.typography.labelMedium)
        }
        if (grouped.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("这里还没有物品", "点击下方“录入物品”，或者清除筛选后再看看。") }
        items(grouped, key = { it.first().item.id }) { rows -> ItemCard(s, rows, { openItem(rows.first().item.id) }, vm.repository.photos) }
    }
    if (showFilters) FilterDialog(s, filter, { showFilters = false }, { onFilter(it); showFilters = false })
}

@Composable private fun FilterDialog(s: Inventory, original: Filter, dismiss: () -> Unit, apply: (Filter) -> Unit) {
    var filter by remember { mutableStateOf(original) }
    var min by remember { mutableStateOf(original.minPrice?.let(::money) ?: "") }; var max by remember { mutableStateOf(original.maxPrice?.let(::money) ?: "") }
    var from by remember { mutableStateOf(original.dateFrom ?: "") }; var to by remember { mutableStateOf(original.dateTo ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(dismiss, title = { Text("筛选与排序") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("分类", filter.category ?: "", listOf("" to "全部分类") + (categories + s.items.map { it.category }).distinct().map { it to it }) { filter = filter.copy(category = it.ifEmpty { null }) }
            PlaceChoice(s, filter.place ?: "", { filter = filter.copy(place = it.ifEmpty { null }) }, true, true)
            Choice("有效期", filter.expiry, listOf("全部", "即将到期", "已过期", "未到期", "有效期内", "未设置").map { it to it }) { filter = filter.copy(expiry = it) }
            DateField("到期起始日期（可选）", from, { from = it }, maxDate = to); DateField("到期结束日期（可选）", to, { to = it }, minDate = from)
            Field("最低单价（元）", min, { min = it }); Field("最高单价（元）", max, { max = it })
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(filter.unknownPrice, { filter = filter.copy(unknownPrice = it) }); Text("仅未填写价格") }
            if (s.labels.isNotEmpty()) {
                Text("标签")
                s.labels.forEach { tag -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(tag.id in filter.tags, { checked -> filter = filter.copy(tags = if (checked) filter.tags + tag.id else filter.tags - tag.id) }); Text(tag.name) } }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(filter.allTags, { filter = filter.copy(allTags = it) }); Text("同时匹配所有标签", Modifier.padding(start = 8.dp)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(filter.includeEmpty, { filter = filter.copy(includeEmpty = it) }); Text("包含零库存及归档") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(filter.valuable, { filter = filter.copy(valuable = it) }); Text("仅贵重物品") }
            Choice("排序", filter.sort, listOf("最近添加", "名称", "最早到期", "价值最高").map { it to it }) { filter = filter.copy(sort = it) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({ runCatching {
        val low = parsePrice(min); val high = parsePrice(max); require(low == null || high == null || low <= high) { "最低价格不能大于最高价格" }
        val start = parseDate(from); val end = parseDate(to); require(start == null || end == null || start <= end) { "起始日期不能晚于结束日期" }
        require(!filter.unknownPrice || (low == null && high == null)) { "未填写价格不能同时限定价格区间" }
        apply(filter.copy(minPrice = low, maxPrice = high, dateFrom = start, dateTo = end))
    }.onFailure { error = it.message } }) { Text("应用") } }, dismissButton = { TextButton(dismiss) { Text("取消") } })
}
