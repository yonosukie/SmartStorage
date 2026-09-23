package com.smartstorage.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import com.smartstorage.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File

class InventoryRepository(private val context: Context) {
    private val db = Room.databaseBuilder(context, StorageDatabase::class.java, "smart-storage.db").build()
    private val dao = db.dao()
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<Inventory?>(null)
    val state = mutableState.asStateFlow()
    val photos = File(context.filesDir, "photos").apply { mkdirs() }
    val cutoutSettings = CutoutSettings(context)
    val safetyBackup = File(context.filesDir, "restore-safety.ssb")
    fun close() = db.close()
    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock {
        val loaded = db.withTransaction {
            val metadata = dao.metadata().associate { it.id to it.payload }
            if (metadata["library"] == null) {
                require(dao.places().isEmpty() && dao.items().isEmpty() && dao.batches().isEmpty() && dao.balances().isEmpty()) { "资料库元数据缺失，请保留现有文件并恢复备份" }
                Inventory()
            } else Inventory(
                libraryId = metadata.getValue("library"),
                places = dao.places().map { storageJson.decodeFromString<Place>(it.payload) },
                items = dao.items().map { storageJson.decodeFromString<Thing>(it.payload) },
                batches = dao.batches().map { storageJson.decodeFromString<Batch>(it.payload) },
                balances = dao.balances().map { Balance(it.id, it.batchId, it.placeId, it.quantity, it.revision) },
                movements = dao.movements().map { storageJson.decodeFromString<Movement>(it.payload) },
                labels = dao.labels().map { storageJson.decodeFromString<Label>(it.payload) },
                boxes = dao.layouts().map { storageJson.decodeFromString<LayoutBox>(it.payload) },
                packing = dao.packing().map { storageJson.decodeFromString<Packing>(it.payload) },
                preferences = metadata["preferences"]?.let { storageJson.decodeFromString<Preferences>(it) } ?: Preferences())
        }
        InventoryRules.validate(loaded)
        if (db.dao().metadata().none { it.id == "library" }) persist(loaded, null)
        mutableState.value = loaded
    } }
    suspend fun update(transform: (Inventory) -> Inventory) = withContext(Dispatchers.IO) { mutex.withLock {
        val old = requireNotNull(mutableState.value) { "数据尚未加载" }; val next = transform(old)
        InventoryRules.validate(next); persist(next, old); mutableState.value = next
    } }
    private suspend fun persist(s: Inventory, previous: Inventory?) = db.withTransaction {
        // Prune dependants before parents. Upserts never use REPLACE, which could break references.
        if (previous == null) {
            dao.pruneMovements(emptyList()); dao.pruneBalances(emptyList()); dao.pruneBatches(emptyList())
            dao.pruneItems(emptyList()); dao.prunePlaces(emptyList()); dao.pruneLabels(emptyList())
            dao.pruneLayouts(emptyList()); dao.prunePacking(emptyList())
        } else {
            // Stock records are retained for audit; only these collections support deletion.
            (previous.places.map { it.id } - s.places.map { it.id }.toSet()).chunked(500).forEach { dao.deletePlaces(it) }
            (previous.labels.map { it.id } - s.labels.map { it.id }.toSet()).chunked(500).forEach { dao.deleteLabels(it) }
            (previous.boxes.map { it.id } - s.boxes.map { it.id }.toSet()).chunked(500).forEach { dao.deleteLayouts(it) }
            (previous.packing.map { it.id } - s.packing.map { it.id }.toSet()).chunked(500).forEach { dao.deletePacking(it) }
        }
        fun <T> changed(now: List<T>, before: List<T>?) = if (before == null) now else { val oldSet = before.toHashSet(); now.filterNot { it in oldSet } }
        dao.places(changed(s.places, previous?.places).map { PlaceRecord(it.id, storageJson.encodeToString(it)) })
        dao.items(changed(s.items, previous?.items).map { ItemRecord(it.id, storageJson.encodeToString(it)) })
        dao.batches(changed(s.batches, previous?.batches).map { BatchRecord(it.id, it.itemId, it.expires, storageJson.encodeToString(it)) })
        val previousBalances = previous?.balances?.map { it.id }?.toSet().orEmpty()
        val balanceChanges = changed(s.balances, previous?.balances).map { BalanceRecord(it.id, it.batchId, it.placeId, it.quantity, it.revision) }
        dao.balances(balanceChanges.filterNot { it.id in previousBalances })
        dao.updateBalances(balanceChanges.filter { it.id in previousBalances })
        dao.movements(changed(s.movements, previous?.movements).map { MovementRecord(it.id, it.batchId, storageJson.encodeToString(it)) })
        dao.labels(changed(s.labels, previous?.labels).map { LabelRecord(it.id, storageJson.encodeToString(it)) })
        dao.layouts(changed(s.boxes, previous?.boxes).map { LayoutRecord(it.id, storageJson.encodeToString(it)) })
        dao.packing(changed(s.packing, previous?.packing).map { PackingRecord(it.id, storageJson.encodeToString(it)) })
        dao.metadata(listOf(MetaRecord("library", s.libraryId), MetaRecord("preferences", storageJson.encodeToString(s.preferences))))
    }
    suspend fun importPhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
        var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("无法读取图片")
        val orientation = runCatching { context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }.getOrNull()
        val matrix = Matrix().apply { when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        } }
        val bitmap = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
        val transparent = bitmap.hasAlpha()
        val name = "${newId()}.${if (transparent) "png" else "jpg"}"
        try { File(photos, name).outputStream().use { require(bitmap.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 86, it)) } }
        finally { bitmap.recycle() }
        name
    }
    suspend fun removeBackground(name: String): String = withContext(Dispatchers.IO) {
        require(name.matches(Regex("[a-zA-Z0-9_-]+\\.(jpg|png)"))) { "图片路径无效" }
        val bytes = RemoveBgClient().remove(File(photos, name), cutoutSettings.readKey())
        val output = File(photos, "${newId()}.png")
        try { output.writeBytes(bytes); output.name } catch (e: Exception) { output.delete(); throw e }
    }
    suspend fun export(uri: Uri, excel: Boolean, password: String, filter: Filter? = null) = withContext(Dispatchers.IO) { mutex.withLock {
        val snapshot = requireNotNull(mutableState.value)
        val temporary = File.createTempFile("export-", ".tmp", context.cacheDir)
        try {
            temporary.outputStream().use { out -> if (excel) ExcelExport.write(snapshot, out, filter)
                else Archive.write(snapshot, { File(photos, it).readBytes() }, out, password.toCharArray()) }
            context.contentResolver.openOutputStream(uri, "wt")?.use { out -> temporary.inputStream().use { it.copyTo(out) } } ?: error("无法写入文件")
        } finally { temporary.delete() }
    } }
    suspend fun previewRestore(uri: Uri, password: String): RestoredArchive = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { Archive.read(it, password.toCharArray()) } ?: error("无法读取备份")
    }
    suspend fun restore(archive: RestoredArchive) = withContext(Dispatchers.IO) { mutex.withLock {
        InventoryRules.validate(archive.state)
        require(archive.state.items.mapNotNull { it.photo }.toSet() == archive.photos.keys) { "备份图片引用不完整" }
        val current = requireNotNull(mutableState.value)
        val safetyTemp = File(context.filesDir, "restore-safety.tmp")
        safetyTemp.outputStream().use { Archive.write(current, { File(photos, it).readBytes() }, it) }
        java.nio.file.Files.move(safetyTemp.toPath(), safetyBackup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        // New image names ensure a failed restore cannot overwrite images referenced by the old DB.
        val names = archive.photos.keys.associateWith { "${newId()}.${it.substringAfterLast('.')}" }
        val written = mutableListOf<File>()
        try {
            archive.photos.forEach { (name, bytes) -> File(photos, names.getValue(name)).also { written.add(it); it.writeBytes(bytes) } }
            val restored = archive.state.copy(version = 2, items = archive.state.items.map { it.copy(photo = it.photo?.let(names::getValue)) },
                preferences = archive.state.preferences.copy(lastNotified = null))
            persist(restored, null); mutableState.value = restored
        } catch (e: Exception) { written.forEach { it.delete() }; throw e }
    } }
}
