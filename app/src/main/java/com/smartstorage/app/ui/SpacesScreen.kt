package com.smartstorage.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartstorage.app.StorageViewModel
import com.smartstorage.core.*

fun placeType(kind: String) = when (kind) { "HOUSE" -> "房子"; "ROOM" -> "房间"; "LAYER" -> "分层"; else -> "容器" }
fun placeIcon(place: Place): ImageVector = when {
    place.kind == "HOUSE" -> Icons.Outlined.Home
    place.kind == "LAYER" -> Icons.Outlined.Layers
    place.name.contains("客厅") -> Icons.Outlined.Weekend
    place.name.contains("卧室") -> Icons.Outlined.Bed
    place.name.contains("厨房") -> Icons.Outlined.Kitchen
    place.kind == "ROOM" -> Icons.Outlined.MeetingRoom
    else -> Icons.Outlined.Inventory2
}

@Composable fun SpacesScreen(
    s: Inventory, vm: StorageViewModel, currentPlaceId: String?, onPlaceChange: (String?) -> Unit,
    openItems: (String) -> Unit, openLayout: (String) -> Unit, openItem: (String) -> Unit,
) {
    val parent = s.places.firstOrNull { it.id == currentPlaceId }
    var editing by remember { mutableStateOf<Place?>(null) }
    var deleting by remember { mutableStateOf<Place?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    val children = s.places.filter { it.parentId == parent?.id && it.kind != "SYSTEM" && (showArchived || !it.archived) }
        .sortedWith(compareBy<Place> { if (it.kind == "LAYER") 0 else 1 }.thenBy { it.layerNumber ?: it.order }.thenBy { it.name })
    val rows = s.rows().filter { it.balance.placeId == (parent?.id ?: UNPLACED) && it.balance.quantity > 0 }
    val targets = remember { mutableStateMapOf<String, Rect>() }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var move by remember { mutableStateOf<Pair<StockRow, String>?>(null) }
    val allRows = remember(s) { s.rows().filter { it.balance.quantity > 0 } }
    val scopeIds = parent?.let { s.descendants(it.id) }
    val count = allRows.filter { scopeIds == null || it.balance.placeId in scopeIds }.map { it.item.id }.distinct().size
    val busy by vm.busy.collectAsState()
    BackHandler(parent != null) { onPlaceChange(parent?.parentId) }
    LaunchedEffect(currentPlaceId) { targets.clear(); dragPosition = null; dragging = null }
    LazyVerticalGrid(columns = GridCells.Adaptive(152.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ onPlaceChange(null) }) { Text("全部空间") }
                    val ancestors = generateSequence(parent) { p -> s.places.firstOrNull { it.id == p.parentId } }.toList().asReversed()
                    ancestors.forEach { p ->
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.align(Alignment.CenterVertically).size(16.dp))
                        TextButton({ onPlaceChange(p.id) }) { Text(p.name, fontWeight = if (p.id == parent?.id) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(28.dp)) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(parent?.name ?: "家里的每个角落", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(if (parent == null) "为每件好物，找到合适的位置" else "${placeType(parent.kind)} · ${if(parent.archived) "已归档" else "$count 种好物，妥善收纳"}", style = MaterialTheme.typography.bodyMedium)
                            Text("${children.size} 个下级空间  ·  $count 种物品", style = MaterialTheme.typography.labelMedium)
                        }
                        Icon(parent?.let(::placeIcon) ?: Icons.Outlined.Home, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ editing = Place(parentId = parent?.id, kind = when(parent?.kind) { null -> "HOUSE"; "HOUSE" -> "ROOM"; else -> "CONTAINER" }, name = "") }, enabled = parent?.archived != true && !busy) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                        Text(when(parent?.kind) { null -> "添加房子"; "HOUSE" -> "添加房间"; else -> "添加容器" })
                    }
                    if (parent?.kind == "ROOM") FilledTonalButton({ openLayout(parent.id) }) { Icon(Icons.Outlined.GridOn, null, Modifier.size(18.dp)); Text(" 房间布局") }
                    if (parent?.kind == "CONTAINER") OutlinedButton({ editing = parent }) { Text("设置层数") }
                    if (parent != null) TextButton({ openItems(parent.id) }) { Text("查看全部物品") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when(parent?.kind) { null -> "我的房子"; "HOUSE" -> "房间一览"; "CONTAINER" -> "分层与子容器"; else -> "收纳容器" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FilterChip(showArchived, { showArchived = !showArchived }, label = { Text("含归档") })
                }
            }
        }
        if (children.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyState("给这里安排一个位置", if (parent?.kind == "CONTAINER") "可以设置层数，或添加内部的抽屉与收纳盒。" else "点击上方添加按钮，开始搭建你的收纳空间。")
        }
        items(children, key = { it.id }) { child ->
            val hovered = !child.archived && child.kind != "HOUSE" && dragPosition?.let { targets[child.id]?.contains(it) } == true
            DisposableEffect(child.id) { onDispose { targets.remove(child.id) } }
            val childIds = s.descendants(child.id)
            PlaceCard(child, allRows.filter { it.balance.placeId in childIds }.map { it.item.id }.distinct().size,
                s.layers(child.id).size, Modifier.onGloballyPositioned { targets[child.id] = it.boundsInRoot() }, hovered,
                onClick = { if (dragging == null) onPlaceChange(child.id) }, onEdit = { editing = child }, onDelete = { deleting = child })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(if (parent == null) "待归位的好物" else if (parent.kind == "LAYER") "这一层的物品" else "直接存放的物品",
                if (dragging != null) "正在移动 $dragging · 拖到目标卡片后松手" else "${rows.map { it.item.id }.distinct().size} 种 · 长按物品可拖到上方容器或分层")
        }
        items(rows, key = { it.balance.id }) { row ->
            var bounds by remember { mutableStateOf(Rect.Zero) }
            Box(Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }.pointerInput(row.balance.id, currentPlaceId) {
                detectDragGesturesAfterLongPress(onDragStart = { dragging = row.item.name; dragPosition = bounds.topLeft + it },
                    onDragCancel = { dragPosition = null; dragging = null }, onDragEnd = {
                        val target = targets.entries.firstOrNull { (_, rect) -> dragPosition?.let(rect::contains) == true }?.key
                        if (target != null && s.places.any { it.id == target && it.kind != "HOUSE" && !it.archived }) move = row to target
                        else vm.notify("未选中可存放的位置，物品没有移动")
                        dragPosition = null; dragging = null
                    }, onDrag = { change, amount -> change.consume(); dragPosition = dragPosition?.plus(amount) })
            }) { ItemCard(s, listOf(row), { openItem(row.item.id) }, vm.repository.photos, compact = true) }
        }
        if (rows.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("这里还没有物品", "录入时选择这里，或从物品详情将已有物品移入。") }
    }
    editing?.let { PlaceDialog(s, it, vm, { editing = null }) }
    deleting?.let { p -> Confirm("删除 ${p.name}？", "有关联库存或历史的位置不能删除，可归档保留记录。", { deleting = null }, { vm.update({ deleting = null }) { InventoryRules.deletePlace(it, p.id) } }) }
    move?.let { (row, target) -> StockDialog(s, row, "移动", vm, { move = null }, target) }
}

@Composable fun PlaceCard(place: Place, count: Int, layers: Int, modifier: Modifier = Modifier, highlighted: Boolean = false,
    onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick, modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if(highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(18.dp), color = if(place.kind == "ROOM") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) { Icon(placeIcon(place), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.weight(1f))
                Box { IconButton({ menu = true }, Modifier.size(40.dp)) { Icon(Icons.Outlined.MoreHoriz, "管理${place.name}") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("编辑${placeType(place.kind)}") }, onClick = { menu = false; onEdit() })
                        if (place.kind != "LAYER") DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if(highlighted) "松手移入此处" else "$count 种物品", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            Text(if(place.archived) "已归档" else placeType(place.kind) + if(layers > 0) " · $layers 层" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun PlaceDialog(s: Inventory, original: Place, vm: StorageViewModel, dismiss: () -> Unit) {
    var name by remember { mutableStateOf(original.name) }
    var parent by remember { mutableStateOf(original.parentId ?: "") }
    var archived by remember { mutableStateOf(original.archived) }
    var layerCount by remember { mutableIntStateOf(s.layers(original.id).size) }
    val forbidden = s.descendants(original.id)
    val parents = s.places.filter { !it.archived && it.id !in forbidden && when(original.kind) {
        "ROOM" -> it.kind == "HOUSE"; "CONTAINER" -> it.kind in listOf("ROOM", "CONTAINER", "LAYER"); else -> false
    } }
    val busy by vm.busy.collectAsState()
    AlertDialog(dismiss, title = { Text(if (original.name.isEmpty()) "新建${placeType(original.kind)}" else "编辑${placeType(original.kind)}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Field("名称", name, { name = it })
            if (original.kind == "ROOM" || original.kind == "CONTAINER") {
                val suggestions = if (original.kind == "ROOM") listOf("客厅", "卧室", "厨房", "卫生间", "书房") else listOf("衣柜", "收纳箱", "抽屉", "柜子")
                Choice("常用名称", if (name in suggestions) name else "", listOf("" to "自定义名称") + suggestions.map { it to it }) { if(it.isNotEmpty()) name = it }
                Choice("上级空间", parent, parents.map { it.id to s.path(it.id) }) { parent = it }
            }
            if (original.kind == "CONTAINER" && !archived) {
                Choice("层数", layerCount.toString(), (0..20).map { it.toString() to if(it == 0) "不分层" else "$it 层" }) { layerCount = it.toInt() }
                Text("第 1 层为最上层，依次向下编号。已有物品不会自动分层；减少层数前需先移走对应层的物品。", style = MaterialTheme.typography.bodySmall)
            }
            if (original.kind == "LAYER") Text("${s.path(original.parentId)} · 第 ${original.layerNumber} 层\n可修改层名；层数请在上级容器中设置。", style = MaterialTheme.typography.bodySmall)
            if (s.places.any { it.id == original.id } && original.kind != "LAYER") Row(verticalAlignment = Alignment.CenterVertically) { Switch(archived, { archived = it }); Text("归档空空间", Modifier.padding(start = 12.dp)) }
        }
    }, confirmButton = { TextButton({ vm.update(dismiss) {
        InventoryRules.savePlace(it, original.copy(name = name, parentId = parent.ifEmpty { null }, archived = archived),
            if (original.kind == "CONTAINER" && !archived) layerCount else null)
    } }, enabled = !busy && name.isNotBlank()) { Text("保存") } }, dismissButton = { TextButton(dismiss) { Text("取消") } })
}
