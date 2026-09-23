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

@Composable fun RecognitionSettingsPanel(vm: StorageViewModel, disabled: Boolean = false) {
    val settings = vm.repository.recognitionSettings
    var revision by remember { mutableIntStateOf(0) }
    val configured = remember(revision) { settings.readKey().isNotBlank() }
    var automatic by remember(revision) { mutableStateOf(settings.automatic) }
    var dialog by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(settings.model) }
    val working by vm.busy.collectAsState()
    val busy = working || disabled
    FormSection("AI 识别 · OpenRouter") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("拍照或选图后自动填写", Modifier.weight(1f))
            Switch(automatic && configured, { automatic = it; settings.automatic = it }, enabled = configured && !busy)
        }
        Text("启用后，所选照片及分类选项会发送至 OpenRouter 和其模型服务商。只填写建议，保存前请核对。免费服务可能限流。", style = MaterialTheme.typography.bodySmall)
        Text(if (configured) "已配置 · ${settings.model}" else "请先配置 API Key，再使用识别", style = MaterialTheme.typography.labelMedium)
        Row {
            TextButton({ key = ""; model = settings.model; dialog = true }, enabled = !busy) { Text(if (configured) "修改识别配置" else "配置识别") }
            if (configured) TextButton({ vm.work({ revision++ }) { withContext(Dispatchers.IO) { settings.clear() } } }, enabled = !busy) { Text("清除识别配置") }
        }
    }
    if (dialog) AlertDialog({ if (!busy) { key = ""; dialog = false } }, title = { Text("配置 OpenRouter") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("在 openrouter.ai/keys 创建 API Key。密钥使用 Android Keystore 加密，仅存本机，不进入物品备份。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                singleLine = true, enabled = !busy)
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("免费视觉模型") }, singleLine = true, enabled = !busy)
            Text("默认 openrouter/free；也可填写支持图片的 :free 模型。不会切换付费模型。保存即同意上传后续所选照片并开启自动识别。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        TextButton({ vm.work({ key = ""; dialog = false; revision++ }) { withContext(Dispatchers.IO) { settings.save(key, model) } } }, enabled = key.isNotBlank() && model.isNotBlank() && !busy) { Text("同意并开启") }
    }, dismissButton = { TextButton({ key = ""; dialog = false }, enabled = !busy) { Text("取消") } })
}
