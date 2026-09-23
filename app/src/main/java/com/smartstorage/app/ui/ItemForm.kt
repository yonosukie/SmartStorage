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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable fun ItemForm(s: Inventory, vm: StorageViewModel, itemId: String?, mode: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    val old = s.items.firstOrNull { it.id == itemId }
    var name by rememberSaveable { mutableStateOf(old?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(old?.category ?: "未分类") }
    val legacyUnit = old?.unit ?: "件"
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
    var recognizing by remember { mutableStateOf(false) }
    var recognitionStatus by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf(emptyList<RecognizedItem>()) }
    var touched by rememberSaveable { mutableStateOf(if (old == null) emptyList<String>() else listOf("category", "unit")) }
    var undoRecognition by remember { mutableStateOf<Pair<RecognitionDraft, RecognitionDraft>?>(null) }
    val recognitionScope = rememberCoroutineScope()
    val currentInventory by rememberUpdatedState(s)
    fun draft() = RecognitionDraft(name, category, legacyUnit, notes, tags)
    fun setDraft(value: RecognitionDraft) {
        name = value.name; category = value.category; notes = value.notes; tags = value.tags
    }
    fun undoAi() {
        undoRecognition?.let { (before, applied) -> setDraft(draft().undo(before, applied, touched.toSet())) }
        undoRecognition = null
        recognitionStatus = "已撤销自动填写，保留手动修改"
    }
    fun applyRecognition(item: RecognizedItem) {
        undoAi()
        val before = draft()
        val after = before.fill(item, currentInventory.labels, touched.toSet() + "unit")
        setDraft(after)
        undoRecognition = if (before != after) before to after else null
        candidates = emptyList()
        recognitionStatus = if (before == after) "已识别为 ${item.name}；已有或手动修改的字段保持不变" else "AI 已填写 · 请核对后保存，手动填写的内容已保留"
    }
    fun recognize(source: String) {
        if (recognizing) return
        if (vm.repository.recognitionSettings.readKey().isBlank()) {
            recognitionStatus = "请先在下方配置 OpenRouter API Key"
            return
        }
        candidates = emptyList()
        recognizing = true
        recognitionStatus = "正在识别物品…"
        recognitionScope.launch {
            try {
                val result = vm.repository.recognizePhoto(source, categories + currentInventory.items.map { it.category })
                if (originalPhoto == source) {
                    when (result.size) {
                        0 -> recognitionStatus = "未识别到清晰物品，请重新拍照或手动填写"
                        1 -> applyRecognition(result.single())
                        else -> { candidates = result; recognitionStatus = "识别到多种物品，请选择本次录入的物品" }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { recognitionStatus = "${e.message ?: "识别失败"}；可继续手动填写" }
            finally { recognizing = false }
        }
    }
    var newTag by remember { mutableStateOf("") }
    val operationId = rememberSaveable { newId() }
    val newItemId = rememberSaveable { newId() }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val working by vm.busy.collectAsState()
    val busy = working || recognizing
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
        undoAi()
        candidates = emptyList()
        recognitionStatus = ""
        originalPhoto = source; photo = source; cutoutPhoto = null; photoStatus = "原图已保存"
        if (vm.repository.recognitionSettings.automatic) recognize(source)
        if (vm.repository.cutoutSettings.automatic) cutout(source)
    } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::importPhotoToForm) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { importPhotoToForm(it.toUri()) } }
    BackHandler { if (busy) vm.notify("图片正在处理，请稍候") else exit = true }
    fun save(target: Thing? = old) {
        try {
            require(name.isNotBlank()) { "请填写物品名称" }
            val count = quantity.toIntOrNull() ?: error("请输入整数数量")
            val item = if (target != null && mode != "edit") target else Thing(target?.id ?: newItemId,
                name.trim(), category.trim().ifEmpty { "未分类" }, legacyUnit, tags, notes.trim(), valuable, photo,
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
                TextButton({ originalPhoto?.let(::recognize) }, enabled = !busy && originalPhoto != null) { Text("AI 识别") }
            }
            RecognitionSettingsPanel(vm, recognizing)
            if (recognitionStatus.isNotBlank()) Text(recognitionStatus, style = MaterialTheme.typography.bodySmall)
            if (recognizing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (undoRecognition != null) TextButton({ undoAi() }, enabled = !busy) { Text("撤销 AI 填写") }
            CutoutSettingsPanel(vm) { cutoutConfigured = vm.repository.cutoutSettings.readKey().isNotBlank() }
            if (photoStatus.isNotBlank()) Text(photoStatus, style = MaterialTheme.typography.bodySmall)
            if (originalPhoto != null) Row {
                TextButton({ vm.work { cutout(originalPhoto!!) } }, enabled = !busy && cutoutConfigured) { Text(if (cutoutPhoto == null) "抠图 / 重试" else "重新抠图") }
                if (cutoutPhoto != null) TextButton({ photo = if (photo == cutoutPhoto) originalPhoto else cutoutPhoto }, enabled = !busy) { Text(if (photo == cutoutPhoto) "使用原图" else "使用抠图") }
            }
            FormSection("基本信息") {
            Field("物品名称 *", name, { name = it; touched = (touched + "name").distinct() })
            EditableChoice("分类", category, categories + s.items.map { it.category }) { category = it; touched = (touched + "category").distinct() }
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
                Checkbox(tag.id in tags, { checked -> tags = if (checked) tags + tag.id else tags - tag.id; touched = (touched + "tags").distinct() }); Text(tag.name, Modifier.padding(top = 12.dp))
            } }
            Row { Field("新标签", newTag, { newTag = it }, Modifier.weight(1f)); TextButton({
                val tag = Label(name = newTag.trim())
                vm.update({ tags = tags + tag.id; newTag = ""; touched = (touched + "tags").distinct() }) { it.copy(labels = it.labels + tag) }
            }, enabled = newTag.isNotBlank() && !busy) { Text("添加") } }
            Row { Checkbox(valuable, { valuable = it }); Text("标记为贵重物品", Modifier.padding(top = 12.dp)) }
            Field("备注", notes, { notes = it; touched = (touched + "notes").distinct() }, singleLine = false)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button({ if (mode == "new" && s.items.any { it.name.trim().equals(name.trim(), true) }) duplicate = true else save() }, Modifier.fillMaxWidth(), enabled = !busy) { Text(if (mode == "edit") "保存修改" else "保存入库") }
        TextButton({ exit = true }, Modifier.fillMaxWidth(), enabled = !busy) { Text("取消") }
        Spacer(Modifier.height(30.dp))
    }
    if (exit) Confirm("放弃本次编辑？", "本次尚未保存的表单内容将被放弃。", { exit = false }, onCancel)
    if (candidates.isNotEmpty()) AlertDialog(
        onDismissRequest = { candidates = emptyList(); recognitionStatus = "已取消选择，可继续手动填写" },
        title = { Text("选择本次录入的物品") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            candidates.forEach { item -> TextButton({ applyRecognition(item) }) { Text("${item.name} · ${item.category}") } }
        } },
        confirmButton = { TextButton({ candidates = emptyList(); recognitionStatus = "已取消选择，可继续手动填写" }) { Text("取消") } }
    )
    if (duplicate) AlertDialog({ duplicate = false }, title = { Text("发现同名物品") }, text = {
        Column { Text("可以新建独立物品，也可以为已有物品补货：")
            s.items.filter { it.name.trim().equals(name.trim(), true) }.forEach { existing -> TextButton({ duplicate = false; save(existing) }) { Text("补货：${existing.name}") } }
        }
    }, confirmButton = { TextButton({ duplicate = false; save() }) { Text("仍然新建") } }, dismissButton = { TextButton({ duplicate = false }) { Text("返回修改") } })
}
