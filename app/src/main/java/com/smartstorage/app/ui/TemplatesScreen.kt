package com.smartstorage.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartstorage.app.StorageViewModel
import com.smartstorage.core.*

@Composable fun TemplatesScreen(s: Inventory, vm: StorageViewModel) {
    var selected by remember { mutableStateOf<StorageTemplate?>(null) }
    var deleting by remember { mutableStateOf<Packing?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("给整理一点灵感", "应用方案创建分区，不会生成虚构库存。") }
        items(templates) { template -> Card({ selected = template }, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(template.name, style = MaterialTheme.typography.titleLarge); Text(template.description)
                Text(template.sections.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                Text("使用方案 →", color = MaterialTheme.colorScheme.primary)
            }
        } }
        if (s.packing.isNotEmpty()) item { SectionTitle("我的打包清单", "勾选不扣减库存，也不会自动改变存放位置。") }
        items(s.packing, key = { it.id }) { list -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Row { Text(list.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); TextButton({ deleting = list }) { Text("删除") } }
            list.entries.forEach { entry -> Row {
                Checkbox(entry.done, { checked -> vm.update { latest -> latest.copy(packing = latest.packing.map { p -> if(p.id != list.id) p else p.copy(entries = p.entries.map { if(it.id == entry.id) it.copy(done = checked) else it }) }) } })
                Text(entry.title, Modifier.padding(top = 12.dp))
            } }
        } } }
    }
    selected?.let { template -> TemplateDialog(s, template, vm, { selected = null }) }
    deleting?.let { list -> Confirm("删除打包清单？", "只删除“${list.title}”的检查项，不影响实际库存。", { deleting = null }, { vm.update({ deleting = null }) { it.copy(packing = it.packing.filterNot { p -> p.id == list.id }) } }) }
}

@Composable private fun TemplateDialog(s: Inventory, template: StorageTemplate, vm: StorageViewModel, dismiss: () -> Unit) {
    var parent by remember { mutableStateOf(s.places.firstOrNull { it.kind in listOf("ROOM", "CONTAINER") }?.id ?: "") }
    var sections by remember { mutableStateOf(template.sections) }; var title by remember { mutableStateOf(template.name) }
    var reuse by remember { mutableStateOf(false) }
    AlertDialog(dismiss, title = { Text("应用${template.name}") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (template.packing) Field("清单名称", title, { title = it })
        else Choice("目标空间", parent, s.places.filter { it.kind in listOf("ROOM", "CONTAINER") }.map { it.id to s.path(it.id) }) { parent = it }
        Text("预览：可改名，清空名称则跳过此项。")
        sections.forEachIndexed { i, name -> Field("${i + 1}", name, { value -> sections = sections.mapIndexed { index, old -> if (i == index) value else old } }) }
        if (!template.packing) Row { Checkbox(reuse, { reuse = it }); Text("同名分区复用已有位置") }
    } }, confirmButton = { TextButton({ vm.update(dismiss) { state ->
        val names = sections.map { it.trim() }.filter { it.isNotEmpty() }; require(names.isNotEmpty()) { "请保留至少一个分区或检查项" }
        if (template.packing) { require(title.isNotBlank()) { "请填写清单名称" }; state.copy(packing = state.packing + Packing(title = title.trim(), entries = names.map { CheckEntry(title = it) })) }
        else {
            require(parent.isNotEmpty()) { "请先创建房间或容器" }
            names.fold(state) { current, name ->
                val existing = current.places.firstOrNull { it.parentId == parent && it.name.equals(name, true) }
                require(existing == null || reuse) { "存在同名分区“$name”，请改名或勾选复用" }
                if (existing != null) current else InventoryRules.savePlace(current, Place(parentId = parent, kind = "CONTAINER", name = name))
            }
        }
    } }) { Text("确认应用") } }, dismissButton = { TextButton(dismiss) { Text("取消") } })
}
