package com.smartstorage.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.smartstorage.app.StorageViewModel
import com.smartstorage.core.*
import kotlin.math.roundToInt

@Composable fun LayoutScreen(s: Inventory, vm: StorageViewModel, roomId: String?, onContainer: (String) -> Unit) {
    val room = s.places.firstOrNull { it.id == roomId && it.kind == "ROOM" }
    if (room == null) { EmptyState("房间不存在", "请返回空间列表选择一个房间。"); return }
    val original = s.boxes.filter { it.roomId == room.id }.map(LayoutGrid::snap)
    var boxes by remember(original) { mutableStateOf(original) }
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    val undo = remember { mutableStateListOf<List<LayoutBox>>() }; val redo = remember { mutableStateListOf<List<LayoutBox>>() }
    var add by remember { mutableStateOf(false) }
    fun rememberChange() { undo.add(boxes); redo.clear() }
    val density = LocalDensity.current
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(room.name, if (editing) "拖拽自动吸附参考线 · 每格为房间的 1/10" else "点击柜子或收纳盒，查看分层与物品。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!editing) Button({ editing = true }) { Text("编辑布局") }
            else {
                Button({ vm.update({ editing = false; undo.clear(); redo.clear() }) { latest -> latest.copy(boxes = latest.boxes.filterNot { it.roomId == room.id } + boxes) } }) { Text("保存") }
                OutlinedButton({ boxes = original; editing = false; undo.clear(); redo.clear() }) { Text("放弃") }
                TextButton({ add = true }) { Text("添加容器") }
            }
        }
        Surface(Modifier.fillMaxWidth().aspectRatio(1f), shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) {
            Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
                Canvas(Modifier.fillMaxSize()) {
                    for (i in 0..LayoutGrid.CELLS) { val x = (this.size.width * i / LayoutGrid.CELLS).roundToInt().toFloat(); val y = (this.size.height * i / LayoutGrid.CELLS).roundToInt().toFloat()
                        drawLine(grid, Offset(x, 0f), Offset(x, this.size.height)); drawLine(grid, Offset(0f, y), Offset(this.size.width, y)) }
                }
                boxes.forEach { box -> key(box.id) {
                    val modifier = Modifier.offset { IntOffset((box.x * size.width).roundToInt(), (box.y * size.height).roundToInt()) }
                        .size(with(density) { ((box.x * size.width + box.width * size.width).roundToInt() - (box.x * size.width).roundToInt()).toDp() }, with(density) { ((box.y * size.height + box.height * size.height).roundToInt() - (box.y * size.height).roundToInt()).toDp() })
                        .then(if (editing) Modifier.pointerInput(box.id, size, editing) {
                            var start = box
                            var delta = Offset.Zero
                            detectDragGestures(onDragStart = { selected = box.id; start = boxes.first { it.id == box.id }; delta = Offset.Zero; rememberChange() }, onDrag = { change, amount ->
                                change.consume(); delta += amount
                                boxes = boxes.map { current -> if (current.id != box.id) current else LayoutGrid.snap(start.copy(
                                    x = start.x + delta.x / size.width,
                                    y = start.y + delta.y / size.height)) }
                            }, onDragCancel = { if (undo.isNotEmpty()) boxes = undo.removeAt(undo.lastIndex) })
                        } else Modifier)
                    Card({ if (editing) selected = box.id else onContainer(box.id) }, modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = CardDefaults.cardColors(containerColor = if (selected == box.id && editing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(8.dp)) {
                            Text(s.places.firstOrNull { it.id == box.id }?.name ?: "容器", maxLines = 2, style = MaterialTheme.typography.labelLarge)
                            Text("${s.rows().filter { it.balance.placeId in s.descendants(box.id) && it.balance.quantity > 0 }.map { it.item.id }.distinct().size} 种", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } }
            }
        }
        if (editing) {
            Row { TextButton({ redo.add(boxes); boxes = undo.removeAt(undo.lastIndex) }, enabled = undo.isNotEmpty()) { Text("撤销") }
                TextButton({ undo.add(boxes); boxes = redo.removeAt(redo.lastIndex) }, enabled = redo.isNotEmpty()) { Text("重做") }
            }
            boxes.firstOrNull { it.id == selected }?.let { box ->
                Text("选中：${s.path(box.id)}")
                FormSection("矩形尺寸", "宽和高可独立调整；每格为房间边长的 1/10") {
                    val widthCells = (box.width * LayoutGrid.CELLS).roundToInt()
                    val heightCells = (box.height * LayoutGrid.CELLS).roundToInt()
                    Choice("宽度", widthCells.toString(), (1..LayoutGrid.CELLS).map { it.toString() to "$it 格" }) { value ->
                        rememberChange(); boxes = boxes.map { if (it.id == box.id) LayoutGrid.resize(it, value.toInt(), heightCells) else it }
                    }
                    Choice("高度", heightCells.toString(), (1..LayoutGrid.CELLS).map { it.toString() to "$it 格" }) { value ->
                        rememberChange(); boxes = boxes.map { if (it.id == box.id) LayoutGrid.resize(it, widthCells, value.toInt()) else it }
                    }
                    Text("当前：$widthCells × $heightCells 格", style = MaterialTheme.typography.labelLarge)
                }
                Row {
                    TextButton({ rememberChange(); boxes = boxes.map { if (it.id == box.id) LayoutGrid.snap(it.copy(width = it.width + .1f, height = it.height + .1f)) else it } }) { Text("放大") }
                    TextButton({ rememberChange(); boxes = boxes.map { if (it.id == box.id) LayoutGrid.snap(it.copy(width = it.width - .1f, height = it.height - .1f)) else it } }) { Text("缩小") }
                    TextButton({ rememberChange(); boxes = boxes.map { if (it.id == box.id) LayoutGrid.snap(it.copy(width = it.height, height = it.width, rotation = (it.rotation + 90) % 360)) else it } }) { Text("旋转") }
                    TextButton({ rememberChange(); boxes = boxes.filterNot { it.id == box.id }; selected = null }) { Text("移除") }
                }
            }
        }
        if (boxes.isEmpty()) EmptyState("画出你的收纳空间", "先在空间页添加容器，再进入编辑布局关联它们。")
        Text("布局表示相对位置，移除图形不会删除容器或物品。", style = MaterialTheme.typography.bodySmall)
        s.places.filter { it.id in s.descendants(room.id) && it.kind == "CONTAINER" }.forEach { p -> TextButton({ if (editing) selected = p.id else onContainer(p.id) }) { Text(p.name + if (boxes.none { it.id == p.id }) " · 待布局" else "") } }
    }
    if (add) AlertDialog({ add = false }, title = { Text("关联容器") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        val available = s.places.filter { it.kind == "CONTAINER" && it.id in s.descendants(room.id) && boxes.none { b -> b.id == it.id } }
        if (available.isEmpty()) Text("没有可关联的容器，请先在空间页创建。")
        available.forEach { p -> TextButton({ rememberChange(); boxes = boxes + LayoutBox(p.id, room.id); selected = p.id; add = false }) { Text(s.path(p.id)) } }
    } }, confirmButton = { TextButton({ add = false }) { Text("关闭") } })
}
