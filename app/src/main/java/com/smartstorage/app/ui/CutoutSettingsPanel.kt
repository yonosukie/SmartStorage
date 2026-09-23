package com.smartstorage.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.smartstorage.app.StorageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun CutoutSettingsPanel(vm: StorageViewModel, onChanged: () -> Unit = {}) {
    val settings = vm.repository.cutoutSettings
    var revision by remember { mutableIntStateOf(0) }
    val configured = remember(revision) { settings.readKey().isNotBlank() }
    var automatic by remember(revision) { mutableStateOf(settings.automatic) }
    var dialog by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsState()
    FormSection("自动抠图 · remove.bg") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("拍照或选图后自动抠图", Modifier.weight(1f))
            Switch(automatic && configured, { automatic = it; settings.automatic = it; onChanged() }, enabled = configured && !busy)
        }
        Text("开启后，新照片会发送至 remove.bg 处理，结果以透明 PNG 保存在本机。失败时保留原图。使用预览尺寸，消耗服务账户的对应额度。", style = MaterialTheme.typography.bodySmall)
        Text(if (configured) "密钥已加密保存，仅限本机使用" else "首次使用请填写 API Key", style = MaterialTheme.typography.labelMedium)
        Row {
            TextButton({ key = ""; dialog = true }, enabled = !busy) { Text(if (configured) "更换密钥" else "配置密钥") }
            if (configured) TextButton({ vm.work({ revision++; onChanged() }) { withContext(Dispatchers.IO) { settings.clear() } } }, enabled = !busy) { Text("清除配置") }
        }
    }
    if (dialog) AlertDialog({ if (!busy) { key = ""; dialog = false } }, title = { Text("配置 remove.bg") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), singleLine = true, enabled = !busy)
            Text("密钥使用 Android Keystore 加密，不写入物品备份。保存后开启自动抠图。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        TextButton({ vm.work({ key = ""; dialog = false; revision++; onChanged() }) { withContext(Dispatchers.IO) { settings.saveKey(key) } } }, enabled = key.isNotBlank() && !busy) { Text("保存并开启") }
    }, dismissButton = { TextButton({ key = ""; dialog = false }, enabled = !busy) { Text("取消") } })
}
