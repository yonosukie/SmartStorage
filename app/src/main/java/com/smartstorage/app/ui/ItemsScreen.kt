package com.smartstorage.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartstorage.app.StorageViewModel
import com.smartstorage.core.*
import java.io.File

@Composable fun ItemCard(s: Inventory, rows: List<StockRow>, onClick: () -> Unit, photoRoot: File? = null, compact: Boolean = false) {
    val thing = rows.first().item
    val images = photoRoot ?: File(LocalContext.current.filesDir, "photos")
    val today = LocalToday.current
    val quantity = rows.sumOf { it.balance.quantity.toLong() }
    val active = rows.filter { it.balance.quantity > 0 }
    val expiry = active.mapNotNull { it.batch.expires }.minOrNull()
    val expired = active.any { it.expired(today) }
    val due = active.any { it.due(s.preferences.leadDays, today) }
    val locations = (active.ifEmpty { rows }).map { s.path(it.balance.placeId).replace(" / ", " › ") }.distinct()
    @Composable fun Information() {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(thing.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            CategoryBadge(thing.category)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(locations.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("数量 $quantity", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expiry != null) Text("${if (expired) "已过期" else if (due) "即将到期" else "到期"} · $expiry",
                style = MaterialTheme.typography.labelSmall,
                color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Card(onClick, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        if (compact) Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ItemPhoto(thing.photo, images, Modifier.fillMaxWidth().height(120.dp))
            Information()
        } else Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemPhoto(thing.photo, images, Modifier.size(88.dp))
            Box(Modifier.weight(1f)) { Information() }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun ItemsScreen(s: Inventory, vm: StorageViewModel, filter: Filter, onFilter: (Filter) -> Unit, openItem: (String) -> Unit) {
    var showFilters by remember { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
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
    val categoryOptions = remember(s, filter.category) { (s.items.map { it.category } + categories + listOfNotNull(filter.category)).distinct() }
    LazyVerticalGrid(columns = if (grid) GridCells.Adaptive(152.dp) else GridCells.Fixed(1), modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(filter.query, { onFilter(filter.copy(query = it)) }, Modifier.fillMaxWidth(),
                placeholder = { Text("搜索物品名称、位置") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (filter.query.isNotEmpty()) IconButton({ onFilter(filter.copy(query = "")) }) { Icon(Icons.Outlined.Close, "清除搜索") } },
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                singleLine = true, shape = MaterialTheme.shapes.medium)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<String?>(null) + categoryOptions).forEach { category ->
                    FilterChip(selected = filter.category == category, onClick = { onFilter(filter.copy(category = category)) },
                        label = { Text(category ?: "全部") }, shape = MaterialTheme.shapes.large,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${grouped.size} 种物品", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ showFilters = true }) { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("筛选") }
                    IconButton({ grid = !grid }) { Icon(if (grid) Icons.Outlined.ViewList else Icons.Outlined.GridView, if (grid) "切换列表视图" else "切换网格视图") }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "更多操作") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("导出筛选清单") }, onClick = { menu = false; export.launch("SmartStorage-筛选清单.xlsx") })
                        }
                    }
                }
                if (filter != Filter()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(listOfNotNull(filter.category, filter.place?.let { s.path(it) },
                        filter.expiry.takeIf { it != "全部" }, filter.sort.takeIf { it != "最近添加" })
                        .joinToString(" · ").ifBlank { "已应用筛选" }, Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    TextButton({ onFilter(Filter()) }) { Text("清空筛选") }
                }
            }
        }
        if (grouped.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyState(if (filter == Filter()) "添加你的第一件物品" else "没有找到匹配的物品",
                if (filter == Filter()) "点击右下角 +，记录物品和存放位置。" else "试试其他关键词，或清除筛选条件。",
                if (filter != Filter()) "清空筛选" else null, { onFilter(Filter()) })
        }
        items(grouped, key = { it.first().item.id }) { rows ->
            ItemCard(s, rows, { openItem(rows.first().item.id) }, vm.repository.photos, compact = grid)
        }
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
