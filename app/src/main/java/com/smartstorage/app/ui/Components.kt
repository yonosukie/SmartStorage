package com.smartstorage.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartstorage.core.*
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.Instant

val Green = Color(0xFF236B50)
val LocalToday = compositionLocalOf { java.time.LocalDate.now() }
@Composable fun StorageTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF92D4AD), secondary = Color(0xFFE3C18E))
        else lightColorScheme(primary = Green, onPrimary = Color.White, primaryContainer = Color(0xFFDFEEE2),
            secondary = Color(0xFF8B6938), secondaryContainer = Color(0xFFF0E6D4), background = Color(0xFFF8F8F2),
            surface = Color(0xFFF8F8F2), surfaceContainer = Color(0xFFF0F1E9), outlineVariant = Color(0xFFDCE3D9))
    MaterialTheme(colorScheme = colors, shapes = Shapes(medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), content = content)
}
@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
    }
}
@Composable fun EmptyState(title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let { FilledTonalButton(onAction) { Text(it) } }
    } }
}
@Composable fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), shape = RoundedCornerShape(16.dp))
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Choice(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, options.firstOrNull { it.first == selected }?.second ?: selected.ifBlank { "请选择" }, { open = true })
    if (open) {
        var search by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { open = false }, title = { Text("选择$label") }, text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (options.size > 8) OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索选项") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val matches = options.filter { it.second.contains(search.trim(), ignoreCase = true) }
                    if (matches.isEmpty()) item { Text("没有匹配的选项", Modifier.padding(16.dp)) }
                    items(matches, key = { it.first }) { (id, title) ->
                        Surface(selected = id == selected, onClick = { onSelect(id); open = false },
                            shape = RoundedCornerShape(16.dp), color = if (id == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(title, Modifier.weight(1f).padding(8.dp)); RadioButton(id == selected, onClick = null)
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton({ open = false }) { Text("取消") } })
    }
}

@Composable fun EditableChoice(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    Choice(label, selected, (options + selected).filter { it.isNotBlank() }.distinct().map { it to it } + ("__custom__" to "自定义…")) {
        if (it == "__custom__") { value = selected; custom = true } else onSelect(it)
    }
    if (custom) AlertDialog({ custom = false }, title = { Text("自定义$label") }, text = {
        Field(label, value, { value = it })
    }, confirmButton = { TextButton({ onSelect(value.trim()); custom = false }, enabled = value.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton({ custom = false }) { Text("取消") } })
}

@Composable private fun PickerField(label: String, value: String, onClick: () -> Unit, calendar: Boolean = false) {
    OutlinedCard(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(if (calendar) Icons.Outlined.CalendarMonth else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateField(label: String, value: String, onChange: (String) -> Unit, minDate: String? = null, maxDate: String? = null) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, value.ifBlank { "未设置 · 点击选择日期" }, { open = true }, calendar = true)
    if (open) {
        val minimum = minDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
        val maximum = maxDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
        fun allowed(date: LocalDate) = (minimum == null || date >= minimum) && (maximum == null || date <= maximum)
        val initial = value.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }?.takeIf { it.year in 1..9999 && allowed(it) }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(), yearRange = 1..9999,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = allowed(Instant.ofEpochMilli(utcTimeMillis).atOffset(ZoneOffset.UTC).toLocalDate())
            })
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton({ state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate().toString()); open = false } }, enabled = state.selectedDateMillis != null) { Text("确定") }
        }, dismissButton = {
            Row { TextButton({ onChange(""); open = false }) { Text("清除") }; TextButton({ open = false }) { Text("取消") } }
        }) { DatePicker(state = state, title = { Text(label, Modifier.padding(start = 24.dp, top = 20.dp)) }) }
    }
}

@Composable fun FormSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(title, subtitle)
            content()
        }
    }
}
@Composable fun PlaceChoice(s: Inventory, selected: String, onSelect: (String) -> Unit, includeHouses: Boolean = false, includeAll: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    PickerField("位置", if (selected.isBlank()) "全部位置" else s.path(selected), { open = true })
    if (open) {
        var cursor by remember { mutableStateOf(s.places.firstOrNull { it.id == selected && !it.archived && it.kind != "SYSTEM" }?.id) }
        val current = s.places.firstOrNull { it.id == cursor }
        val children = s.places.filter { !it.archived && it.parentId == cursor }
            .sortedWith(compareBy<Place> { it.layerNumber ?: 0 }.thenBy { it.order }.thenBy { it.name })
        AlertDialog({ open = false }, title = { Text("选择存放位置") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (current == null) "全部空间" else s.path(current.id), style = MaterialTheme.typography.titleSmall)
                if (current != null) TextButton({ cursor = current.parentId }) { Text("返回上一级") }
                Text("逐级打开空间，再确认存放位置", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(children, key = { it.id }) { place ->
                        OutlinedCard({ cursor = place.id }, Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(placeIcon(place), null, Modifier.size(24.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(place.name); Text(if (place.kind == "SYSTEM") "待归位" else placeType(place.kind), style = MaterialTheme.typography.labelSmall)
                                }
                                Icon(Icons.Outlined.ChevronRight, null)
                            }
                        }
                    }
                    if (children.isEmpty()) item { Text("已到达最末级，可选择此位置。") }
                }
            }
        }, confirmButton = {
            TextButton({ onSelect(current?.id ?: ""); open = false },
                enabled = if (current == null) includeAll else !current.archived && (includeHouses || current.kind != "HOUSE")) {
                Text(if (current == null) "选择全部位置" else "选择此位置")
            }
        }, dismissButton = { TextButton({ open = false }) { Text("取消") } })
    }
}
@Composable fun Chips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { FilterChip(it == selected, { onSelect(it) }, label = { Text(it) }) }
    }
}
@Composable fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp)) { Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun Confirm(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismiss, title = { Text(title) }, text = { Text(body) }, confirmButton = { TextButton(onConfirm) { Text("确认") } }, dismissButton = { TextButton(onDismiss) { Text("取消") } })
}
