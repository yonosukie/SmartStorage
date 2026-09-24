package com.smartstorage.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import java.io.File

@Composable fun ItemPhoto(photo: String?, photos: File, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        if (photo == null) PhotoPlaceholder()
        else SubcomposeAsyncImage(
            model = File(photos, photo), contentDescription = "物品照片", modifier = Modifier.fillMaxSize(),
            contentScale = if (photo.endsWith(".png", true)) ContentScale.Fit else ContentScale.Crop,
            loading = { PhotoPlaceholder() }, error = { PhotoPlaceholder() })
    }
}

@Composable private fun PhotoPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Inventory2, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable fun CategoryBadge(category: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(category, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable fun CollapsibleSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(onClick = { expanded = !expanded }, shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    if (expanded) "收起$title" else "展开$title")
            }
        }
        if (expanded) content()
    }
}

@Composable fun QuantityField(value: String, onChange: (String) -> Unit) {
    val count = value.toIntOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("数量 *", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedIconButton({ count?.let { onChange((it - 1).toString()) } },
                enabled = count != null && count > 1, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Remove, "减少数量")
            }
            OutlinedTextField(value, onChange, Modifier.weight(1f), singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = count == null || count < 1,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            OutlinedIconButton({ count?.let { onChange((it + 1).toString()) } },
                enabled = count != null && count in 1 until Int.MAX_VALUE, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Add, "增加数量")
            }
        }
    }
}

@Composable fun ActionFooter(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}