package com.smartstorage.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.smartstorage.app.ExpiryWorker
import com.smartstorage.app.StorageViewModel
import com.smartstorage.core.*
import java.time.LocalDate

@Composable fun SettingsScreen(s: Inventory, vm: StorageViewModel) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsState()
    var password by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<RestoredArchive?>(null) }
    var tag by remember { mutableStateOf<Label?>(null) }
    var backupStatus by remember { mutableStateOf("") }
    var lead by remember(s.preferences.leadDays) { mutableStateOf(s.preferences.leadDays.toString()) }
    var notificationAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationAllowed = granted
        vm.update({ if (granted) WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ExpiryWorker>().build()) }) { it.copy(preferences = it.preferences.copy(reminderEnabled = granted)) }
        if (!granted) vm.notify("通知未开启，仍可在首页查看临期物品")
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { vm.work({ backupStatus = "备份成功 · ${LocalDate.now()}"; vm.notify("备份已保存") }) { vm.repository.export(it, false, password) } }
    }
    val excel = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let { vm.work({ vm.notify("Excel 清单已导出") }) { vm.repository.export(it, true, "") } }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.work { pendingRestore = vm.repository.previewRestore(it, password) } }
    }
    val safetyExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let {
        vm.work({ vm.notify("恢复前安全快照已导出") }) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.contentResolver.openOutputStream(it, "wt")?.use { out -> vm.repository.safetyBackup.inputStream().use { input -> input.copyTo(out) } } ?: error("无法保存文件")
        } }
    } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("安心记录，自在整理", "数据保存在这台设备上，无需账号即可使用。") }
        item { SectionTitle("保质期提醒")
            Row { Switch(s.preferences.reminderEnabled, { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notification.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.update({ if (enabled) WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ExpiryWorker>().build()) }) { it.copy(preferences = it.preferences.copy(reminderEnabled = enabled)) }
            }); Text("每日临期与过期通知", Modifier.padding(12.dp)) }
            if (!notificationAllowed) Text("系统通知未授权，首页预警仍然有效。", style = MaterialTheme.typography.bodySmall)
            Row { Field("临期天数（0–365）", lead, { lead = it }, Modifier.weight(1f)); TextButton({ vm.update { state -> state.copy(preferences = state.preferences.copy(leadDays = lead.toIntOrNull() ?: throw IllegalArgumentException("请输入有效天数"))) } }) { Text("保存") } }
            Text("按日提醒，系统省电策略可能延迟通知。", style = MaterialTheme.typography.bodySmall)
        }
        item { HorizontalDivider(); SectionTitle("标签管理")
            s.labels.forEach { label -> TextButton({ tag = label }) { Text("●  ${label.name}", color = Color(label.color)) } }
            OutlinedButton({ tag = Label(name = "") }) { Text("创建标签") }
        }
        item { HorizontalDivider(); SectionTitle("备份与恢复", "备份含数据与照片；恢复会替换当前资料库。")
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("备份密码（可选 / 恢复时输入）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Text("设置密码后使用加密备份，请妥善保管密码。留空则导出未加密文件。当前支持最大 128 MB 的资料包。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            Button({ backup.launch("SmartStorage-${LocalDate.now()}.ssb") }, Modifier.fillMaxWidth(), enabled = !busy) { Text("保存本地备份") }
            OutlinedButton({ restore.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), enabled = !busy) { Text("选择备份并预览恢复") }
            if (backupStatus.isNotEmpty()) Text(backupStatus, color = MaterialTheme.colorScheme.primary)
            if (vm.repository.safetyBackup.exists()) TextButton({ safetyExport.launch("SmartStorage-恢复前快照.ssb") }, enabled = !busy) { Text("导出上次恢复前的安全快照") }
            Text("卸载应用会移除本机数据，请将备份保存到应用外的位置。", style = MaterialTheme.typography.bodySmall)
        }
        item { SectionTitle("Excel 清单", "导出物品汇总、批次与位置、库存流水和统计口径。")
            OutlinedButton({ excel.launch("SmartStorage-${LocalDate.now()}.xlsx") }, Modifier.fillMaxWidth(), enabled = !busy) { Text("导出全部 Excel 清单") }
        }
        item { RecognitionSettingsPanel(vm) }
        item { CutoutSettingsPanel(vm) }
        item { HorizontalDivider(); SectionTitle("联网能力")
            Text("AI 识别 · OpenRouter 免费模型\n云备份 · 待接入")
            Text("启用 AI 识别时会上传所选照片及分类选项至 OpenRouter 和模型服务商；自动抠图将照片发送至 remove.bg。密钥仅保存在本机。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("家有好物", "SmartStorage ${com.smartstorage.app.BuildConfig.VERSION_NAME} · 本地开发版")
        }
    }
    pendingRestore?.let { archive -> Confirm("恢复这个备份？", "备份含 ${archive.state.items.size} 种物品、${archive.state.places.count { it.kind != "SYSTEM" }} 个空间和 ${archive.photos.size} 张照片。\n\n将替换当前数据。恢复前会在本机保留一份安全快照。", { pendingRestore = null }, {
        vm.work({ pendingRestore = null; vm.notify("恢复完成"); WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ExpiryWorker>().build()) }) { vm.repository.restore(archive) }
    }) }
    tag?.let { label -> LabelDialog(s, label, vm, { tag = null }) }
}

@Composable private fun LabelDialog(s: Inventory, original: Label, vm: StorageViewModel, dismiss: () -> Unit) {
    var name by remember { mutableStateOf(original.name) }; var color by remember { mutableLongStateOf(original.color) }
    var merge by remember { mutableStateOf("") }; var deleting by remember { mutableStateOf(false) }
    AlertDialog(dismiss, title = { Text("编辑标签") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field("名称", name, { name = it })
        Row { listOf(0xFF236B50L, 0xFF896239L, 0xFF3B67A1L, 0xFF94527CL).forEach { value -> TextButton({ color = value }) { Text(if(color == value) "●" else "○", color = Color(value)) } } }
        if (s.labels.any { it.id == original.id }) {
            Choice("合并到", merge, listOf("" to "不合并") + s.labels.filterNot { it.id == original.id }.map { it.id to it.name }) { merge = it }
            TextButton({ deleting = true }) { Text("删除 / 合并此标签", color = MaterialTheme.colorScheme.error) }
        }
    } }, confirmButton = { TextButton({ vm.update(dismiss) { it.copy(labels = it.labels.filterNot { label -> label.id == original.id } + original.copy(name = name.trim(), color = color)) } }) { Text("保存") } }, dismissButton = { TextButton(dismiss) { Text("取消") } })
    if (deleting) Confirm("${if(merge.isEmpty()) "删除" else "合并"}标签？", "物品将保留，仅修改其标签关联。", { deleting = false }, { vm.update(dismiss) { InventoryRules.removeLabel(it, original.id, merge.ifBlank { null }) } })
}
