package com.smartstorage.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.smartstorage.app.StorageViewModel
import com.smartstorage.core.*
import java.io.File
import androidx.core.net.toUri

@Composable fun ItemForm(s: Inventory, vm: StorageViewModel, itemId: String?, mode: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    val old = s.items.firstOrNull { it.id == itemId }
    var name by rememberSaveable { mutableStateOf(old?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(old?.category ?: "未分类") }
    var unit by rememberSaveable { mutableStateOf(old?.unit ?: "件") }
    var notes by rememberSaveable { mutableStateOf(old?.notes ?: "") }
    var valuable by rememberSaveable { mutableStateOf(old?.valuable ?: false) }
    var photo by rememberSaveable { mutableStateOf(old?.photo) }
    var originalPhoto by rememberSaveable { mutableStateOf(old?.photo) }
    var cutoutPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var photoStatus by remember { mutableStateOf("") }
    var cutoutConfigured by remember { mutableStateOf(vm.repository.cutoutSettings.readKey().isNotBlank()) }
    var tags by rememberSaveable { mutableStateOf(old?.tags ?: emptyList()) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var price by rememberSaveable { mutableStateOf("") }
    var purchased by rememberSaveable { mutableStateOf("") }
    var expires by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf(UNPLACED) }
    var error by remember { mutableStateOf<String?>(null) }
    var exit by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf(false) }
    var aiInfo by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }
    val operationId = rememberSaveable { newId() }
    val newItemId = rememberSaveable { newId() }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val busy by vm.busy.collectAsState()
    suspend fun cutout(name: String) {
        photoStatus = "正在抠图，请稍候…"
        try {
            val result = vm.repository.removeBackground(name)
            cutoutPhoto = result; photo = result; photoStatus = "抠图完成 · 透明 PNG"
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { photo = name; photoStatus = "${e.message ?: "抠图失败"}，已保留原图" }
    }
    fun importPhotoToForm(uri: android.net.Uri) { vm.work {
        val source = vm.repository.importPhoto(uri)
        originalPhoto = source; photo = source; cutoutPhoto = null; photoStatus = "原图已保存"
        if (vm.repository.cutoutSettings.automatic) cutout(source)
    } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::importPhotoToForm) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { importPhotoToForm(it.toUri()) } }
    BackHandler { if (busy) vm.notify("图片正在处理，请稍候") else exit = true }
    fun save(target: Thing? = old) {
        try {
            require(name.isNotBlank() && unit.isNotBlank()) { "请填写物品名称和单位" }
            val count = quantity.toIntOrNull() ?: error("请输入整数数量")
            val item = if (target != null && mode != "edit") target else Thing(target?.id ?: newItemId,
                name.trim(), category.trim().ifEmpty { "未分类" }, unit.trim(), tags, notes.trim(), valuable, photo,
                target?.createdAt ?: System.currentTimeMillis(), target?.archived ?: false)
            val batch = if (mode == "edit") null else Batch(itemId = item.id, purchased = parseDate(purchased), expires = parseDate(expires), price = parsePrice(price))
            batch?.let { val bought = it.purchased; val expiry = it.expires
                require(bought == null || expiry == null || bought <= expiry) { "到期日不能早于购买日期" } }
            vm.update({ onDone(item.id) }) { latest ->
                if (mode == "edit") latest.copy(items = latest.items.map { if (it.id == item.id) item else it })
                else InventoryRules.add(latest, item, batch!!, count, location, operationId)
            }
        } catch (e: Exception) { error = e.message ?: "请检查填写内容" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (mode != "restock") {
            if (photo != null) AsyncImage(File(vm.repository.photos, photo!!), "物品照片", Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
            else EmptyState("给物品留张照片", "照片保存在本机，帮助你更快找到它。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    runCatching {
                        val file = File(context.cacheDir, "camera/${newId()}.jpg").apply { parentFile?.mkdirs() }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        cameraUri = uri.toString(); camera.launch(uri)
                    }.onFailure { vm.notify("无法启动相机，可以从相册选择") }
                }, Modifier.weight(1f), enabled = !busy) { Icon(Icons.Outlined.PhotoCamera, null); Text("拍照") }
                OutlinedButton({ picker.launch("image/*") }, Modifier.weight(1f), enabled = !busy) { Text("相册") }
                TextButton({ aiInfo = true }) { Text("AI 识别") }
            }
            CutoutSettingsPanel(vm) { cutoutConfigured = vm.repository.cutoutSettings.readKey().isNotBlank() }
            if (photoStatus.isNotBlank()) Text(photoStatus, style = MaterialTheme.typography.bodySmall)
            if (originalPhoto != null) Row {
                TextButton({ vm.work { cutout(originalPhoto!!) } }, enabled = !busy && cutoutConfigured) { Text(if (cutoutPhoto == null) "抠图 / 重试" else "重新抠图") }
                if (cutoutPhoto != null) TextButton({ photo = if (photo == cutoutPhoto) originalPhoto else cutoutPhoto }, enabled = !busy) { Text(if (photo == cutoutPhoto) "使用原图" else "使用抠图") }
            }
            FormSection("基本信息") {
            Field("物品名称 *", name, { name = it })
            EditableChoice("分类", category, categories + s.items.map { it.category }) { category = it }
            EditableChoice("计量单位", unit, listOf("件", "个", "盒", "瓶", "包", "袋", "支", "套") + s.items.map { it.unit }) { unit = it }
            }
        } else SectionTitle(old?.name ?: "补充库存", "本次补货单独记录购买日期、单价和保质期。")
        if (mode != "edit") {
            FormSection("库存与存放", "可选择容器内的具体层") {
            Field("数量 *", quantity, { quantity = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            PlaceChoice(s, location, { location = it })
            Field("购入单价 / 元（可选）", price, { price = it })
            DateField("购买日期（可选）", purchased, { purchased = it }, maxDate = expires)
            DateField("到期日期（可选）", expires, { expires = it }, minDate = purchased)
            }
        }
        if (mode != "restock") {
            FormSection("标签与备注", "一个物品可以有多个标签") {
            s.labels.forEach { tag -> Row {
                Checkbox(tag.id in tags, { checked -> tags = if (checked) tags + tag.id else tags - tag.id }); Text(tag.name, Modifier.padding(top = 12.dp))
            } }
            Row { Field("新标签", newTag, { newTag = it }, Modifier.weight(1f)); TextButton({
                val tag = Label(name = newTag.trim())
                vm.update({ tags = tags + tag.id; newTag = "" }) { it.copy(labels = it.labels + tag) }
            }, enabled = newTag.isNotBlank() && !busy) { Text("添加") } }
            Row { Checkbox(valuable, { valuable = it }); Text("标记为贵重物品", Modifier.padding(top = 12.dp)) }
            Field("备注", notes, { notes = it }, singleLine = false)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button({ if (mode == "new" && s.items.any { it.name.trim().equals(name.trim(), true) }) duplicate = true else save() }, Modifier.fillMaxWidth(), enabled = !busy) { Text(if (mode == "edit") "保存修改" else "保存入库") }
        TextButton({ exit = true }, Modifier.fillMaxWidth(), enabled = !busy) { Text("取消") }
        Spacer(Modifier.height(30.dp))
    }
    if (exit) Confirm("放弃本次编辑？", "本次尚未保存的表单内容将被放弃。", { exit = false }, onCancel)
    if (aiInfo) AlertDialog({ aiInfo = false }, title = { Text("AI 识别待接入") }, text = { Text("物品名称和分类识别尚未接入，请手动填写。自动抠图是独立的 remove.bg 功能，开启后会上传照片处理。") }, confirmButton = { TextButton({ aiInfo = false }) { Text("继续手动填写") } })
    if (duplicate) AlertDialog({ duplicate = false }, title = { Text("发现同名物品") }, text = {
        Column { Text("可以新建独立物品，也可以为已有物品补货：")
            s.items.filter { it.name.trim().equals(name.trim(), true) }.forEach { existing -> TextButton({ duplicate = false; save(existing) }) { Text("补货：${existing.name} · ${existing.unit}") } }
        }
    }, confirmButton = { TextButton({ duplicate = false; save() }) { Text("仍然新建") } }, dismissButton = { TextButton({ duplicate = false }) { Text("返回修改") } })
}
