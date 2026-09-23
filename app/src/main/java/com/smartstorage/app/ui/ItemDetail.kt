package com.smartstorage.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartstorage.app.StorageViewModel
import com.smartstorage.core.*
import java.time.Instant
import java.time.ZoneId

@Composable fun ItemDetail(s: Inventory, vm: StorageViewModel, itemId: String?, onEdit: () -> Unit, onRestock: () -> Unit) {
    val item = s.items.firstOrNull { it.id == itemId }
    val today = LocalToday.current
    if (item == null) { EmptyState("物品不存在", "可能已从备份恢复了其他数据，请返回列表。"); return }
    val rows = s.rows().filter { it.item.id == itemId }.sortedWith(compareBy<StockRow> { it.batch.expires ?: "9999" }.thenBy { it.batch.purchased ?: "9999" }.thenBy { it.batch.createdAt })
    var selected by remember { mutableStateOf<StockRow?>(null) }
    var type by remember { mutableStateOf("消耗") }
    var editBatch by remember { mutableStateOf<Batch?>(null) }
    val busy by vm.busy.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(item.name, "${item.category}${if (item.valuable) " · 贵重物品" else ""}") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("${rows.sumOf { it.balance.quantity.toLong() }}", "当前库存", Modifier.weight(1f))
            Metric("¥${money(rows.sumOf { it.value })}", "已知成本", Modifier.weight(1f))
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onRestock, Modifier.weight(1f)) { Text("补货") }; OutlinedButton(onEdit, Modifier.weight(1f)) { Text("编辑资料") }
        }
            FilledTonalButton({ rows.firstOrNull { it.balance.quantity > 0 }?.let { selected = it; type = "消耗" } }, Modifier.fillMaxWidth(), enabled = rows.any { it.balance.quantity > 0 }) { Text("消耗一件 · 优先最早到期批次") }
            if (item.tags.isNotEmpty()) Text("标签：" + s.labels.filter { it.id in item.tags }.joinToString("、") { it.name })
            if (item.notes.isNotBlank()) Text(item.notes)
        }
        item { SectionTitle("库存批次与位置", "同一批次可以分散存放；到期不会自动扣减。") }
        items(rows, key = { it.balance.id }) { row -> OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.path(row.balance.placeId), fontWeight = FontWeight.Bold)
                Text("${row.balance.quantity} · 单价 ${row.batch.price?.let { "¥${money(it)}" } ?: "未填写"}")
                Text("购买：${row.batch.purchased ?: "未填写"}   到期：${row.batch.expires ?: "未设置"}", style = MaterialTheme.typography.bodySmall)
                if (row.expired(today) && row.balance.quantity > 0) Text("已过期，请及时处理", color = MaterialTheme.colorScheme.error)
                Row {
                    TextButton({ selected = row; type = "消耗" }, enabled = row.balance.quantity > 0 && !busy) { Text("消耗") }
                    TextButton({ selected = row; type = "移动" }, enabled = row.balance.quantity > 0 && !busy) { Text("移动") }
                    TextButton({ selected = row; type = "盘点增加" }, enabled = !busy) { Text("调整") }
                    TextButton({ editBatch = row.batch }, enabled = !busy) { Text("批次") }
                }
            }
        } }
        item { SectionTitle("库存记录") }
        items(s.movements.filter { op -> rows.any { it.batch.id == op.batchId } }.sortedByDescending { it.at }.take(100), key = { it.id }) { op ->
            ListItem(headlineContent = { Text("${op.type} ${op.quantity}${if(op.reason.isNotBlank()) " · ${op.reason}" else ""}") },
                supportingContent = { Text("${Instant.ofEpochMilli(op.at).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace('T',' ')}\n${s.path(op.from)} → ${s.path(op.to)}") },
                trailingContent = { if (op.type != "撤销" && s.movements.none { it.reversedId == op.id }) TextButton({ vm.update { InventoryRules.undo(it, op.id) } }, enabled = !busy) { Text("撤销") } })
        }
        if (rows.all { it.balance.quantity == 0 }) item { OutlinedButton({ vm.update { it.copy(items = it.items.map { entry -> if (entry.id == item.id) entry.copy(archived = !entry.archived) else entry }) } }) { Text(if(item.archived) "取消归档" else "归档物品") } }
    }
    selected?.let { row -> StockDialog(s, row, type, vm, { selected = null }) }
    editBatch?.let { batch -> BatchDialog(batch, vm, { editBatch = null }) }
}

@Composable fun StockDialog(s: Inventory, row: StockRow, initialType: String, vm: StorageViewModel, dismiss: () -> Unit, initialTarget: String = UNPLACED) {
    var quantity by remember { mutableStateOf("1") }; var target by remember { mutableStateOf(initialTarget) }
    var type by remember { mutableStateOf(initialType) }; var allowExpired by remember { mutableStateOf(false) }
    val operation = remember { newId() }; val busy by vm.busy.collectAsState()
    AlertDialog(dismiss, title = { Text("$type · ${row.item.name}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("来源：${s.path(row.balance.placeId)}\n可用：${row.balance.quantity}")
            if (initialType != "移动") Choice("操作", type, listOf("消耗", "丢弃", "盘点增加", "盘点减少").map { it to it }) { type = it }
            Field("数量", quantity, { quantity = it })
            if (type == "移动") PlaceChoice(s, target, { target = it })
            if (type == "消耗" && row.expired()) Row { Checkbox(allowExpired, { allowExpired = it }); Text("我已知此批次过期，仍记录消耗") }
        }
    }, confirmButton = { TextButton({ vm.update(dismiss) { InventoryRules.change(it, row.balance.id,
        quantity.toIntOrNull() ?: throw IllegalArgumentException("请输入整数数量"), type, if (type == "移动") target else null, operation, allowExpired) } }, enabled = !busy) { Text("确认") } }, dismissButton = { TextButton(dismiss) { Text("取消") } })
}

@Composable private fun BatchDialog(batch: Batch, vm: StorageViewModel, dismiss: () -> Unit) {
    var price by remember { mutableStateOf(batch.price?.let(::money) ?: "") }
    var purchased by remember { mutableStateOf(batch.purchased ?: "") }; var expires by remember { mutableStateOf(batch.expires ?: "") }
    var leadDays by remember { mutableStateOf(batch.leadDays?.toString() ?: "") }
    var reminderEnabled by remember { mutableStateOf(batch.reminderEnabled) }
    AlertDialog(dismiss, title = { Text("修改批次资料") }, text = { Column {
        Field("单价（元）", price, { price = it }); DateField("购买日期", purchased, { purchased = it }, maxDate = expires); DateField("到期日期", expires, { expires = it }, minDate = purchased)
        Field("临期天数（留空跟随全局）", leadDays, { leadDays = it })
        Row { Checkbox(reminderEnabled, { reminderEnabled = it }); Text("允许此批次通知", Modifier.padding(top = 12.dp)) }
        Text("修改会应用到此批次的所有存放位置。", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton({ vm.update(dismiss) { s ->
        val lead = if (leadDays.isBlank()) null else leadDays.toIntOrNull() ?: throw IllegalArgumentException("请输入有效临期天数")
        val edited = batch.copy(price = parsePrice(price), purchased = parseDate(purchased), expires = parseDate(expires), leadDays = lead, reminderEnabled = reminderEnabled)
        val bought = edited.purchased; val expiry = edited.expires
        require(bought == null || expiry == null || bought <= expiry) { "到期日不能早于购买日期" }
        s.copy(batches = s.batches.map { if (it.id == batch.id) edited else it })
    } }) { Text("保存") } }, dismissButton = { TextButton(dismiss) { Text("取消") } })
}
