package com.smartstorage.core

import java.time.LocalDate

object InventoryRules {
    fun validate(s: Inventory) {
        require(s.version in 1..2) { "备份版本不受支持" }
        fun unique(ids: List<String>) = require(ids.size == ids.toSet().size && ids.none(String::isBlank)) { "数据中存在重复或空 ID" }
        unique(s.places.map { it.id }); unique(s.items.map { it.id }); unique(s.batches.map { it.id })
        unique(s.balances.map { it.id }); unique(s.movements.map { it.id }); unique(s.labels.map { it.id })
        unique(s.boxes.map { it.id }); unique(s.packing.map { it.id })
        val placeMap = s.places.associateBy { it.id }; val itemIds = s.items.map { it.id }.toSet()
        val batchIds = s.batches.map { it.id }.toSet(); val tagIds = s.labels.map { it.id }.toSet()
        require(placeMap[UNPLACED]?.kind == "SYSTEM" && placeMap[UNPLACED]?.parentId == null)
        require(s.places.filter { it.kind == "SYSTEM" }.size == 1)
        require(s.places.map { (it.parentId ?: "") to it.name.trim().lowercase() }.distinct().size == s.places.size) { "同级位置名称不能重复" }
        s.places.forEach { p ->
            require(p.name.isNotBlank()) { "位置名称不能为空" }
            val parent = placeMap[p.parentId]
            require(when (p.kind) { "SYSTEM", "HOUSE" -> p.parentId == null
                "ROOM" -> parent?.kind == "HOUSE"
                "CONTAINER" -> parent?.kind in listOf("ROOM", "CONTAINER", "LAYER")
                "LAYER" -> parent?.kind == "CONTAINER" && p.layerNumber in 1..20
                else -> false }) { "空间层级不正确" }
            var cursor: Place? = p; val seen = mutableSetOf<String>(); var containers = 0
            while (cursor != null) {
                require(seen.add(cursor.id)) { "空间不能循环嵌套" }
                if (cursor.kind == "CONTAINER") containers++
                cursor = placeMap[cursor.parentId]
            }
            require(containers <= 3) { "收纳容器最多嵌套三层" }
            require(p.kind == "LAYER" || p.layerNumber == null) { "只有容器层可以设置层号" }
        }
        val layers = s.places.filter { it.kind == "LAYER" }
        require(layers.map { it.parentId to it.layerNumber }.distinct().size == layers.size) { "容器层号不能重复" }
        layers.filter { it.archived }.forEach { layer ->
            require(s.balances.none { it.placeId in s.descendants(layer.id) && it.quantity > 0 }) { "请先移走此层物品" }
        }
        s.items.forEach { require(it.name.isNotBlank() && it.unit.isNotBlank()); require(tagIds.containsAll(it.tags)) }
        s.batches.forEach {
            require(it.leadDays == null || it.leadDays in 0..365)
            require(it.itemId in itemIds); it.expires?.let(LocalDate::parse); it.purchased?.let(LocalDate::parse)
            require(it.purchased == null || it.expires == null || it.purchased <= it.expires) { "到期日不能早于购买日期" }
            require(it.price == null || it.price in 0..1_000_000_000_000L) { "价格超出范围" }
        }
        require(s.balances.map { it.batchId to it.placeId }.distinct().size == s.balances.size)
        s.balances.forEach {
            require(it.batchId in batchIds && it.placeId in placeMap && it.quantity in 0..1_000_000) { "库存数据不正确" }
            require(placeMap[it.placeId]?.kind != "HOUSE") { "请存放到房间或容器" }
        }
        s.movements.forEach {
            require(it.batchId in batchIds && it.quantity in 1..1_000_000)
            require(it.type in listOf("入库", "消耗", "丢弃", "盘点增加", "盘点减少", "移动", "撤销"))
            require(it.from == null || it.from in placeMap); require(it.to == null || it.to in placeMap)
            require(it.price == null || it.price in 0..1_000_000_000_000L)
            require(it.reversedId == null || s.movements.any { original -> original.id == it.reversedId && original.type != "撤销" })
        }
        require(s.movements.mapNotNull { it.reversedId }.distinct().size == s.movements.count { it.reversedId != null })
        require(s.labels.map { it.name.trim().lowercase() }.distinct().size == s.labels.size && s.labels.none { it.name.isBlank() }) { "标签名称不能重复或为空" }
        s.boxes.forEach {
            require(placeMap[it.roomId]?.kind == "ROOM" && placeMap[it.id]?.kind == "CONTAINER" && it.id in s.descendants(it.roomId))
            require(it.x.isFinite() && it.y.isFinite() && it.width.isFinite() && it.height.isFinite())
            require(it.width in .10f..1f && it.height in .10f..1f && it.x >= 0 && it.y >= 0 && it.x + it.width <= 1.001f && it.y + it.height <= 1.001f)
        }
        require(s.preferences.leadDays in 0..365)
        s.packing.forEach { require(it.title.isNotBlank()); unique(it.entries.map { entry -> entry.id }); require(it.entries.none { entry -> entry.title.isBlank() }) }
    }

    fun add(s: Inventory, item: Thing, batch: Batch, count: Int, place: String, operationId: String): Inventory {
        if (s.movements.any { it.id == operationId }) return s
        require(count in 1..1_000_000) { "数量应为 1 到 1000000 的整数" }
        require(s.places.any { it.id == place && !it.archived }) { "目标位置不存在或已归档" }
        require(batch.itemId == item.id && s.batches.none { it.id == batch.id })
        val balance = Balance(batchId = batch.id, placeId = place, quantity = count)
        return s.copy(items = if (s.items.any { it.id == item.id }) s.items.map { if (it.id == item.id) it.copy(archived = false) else it } else s.items + item,
            batches = s.batches + batch, balances = s.balances + balance,
            movements = s.movements + Movement(operationId, batch.id, "入库", count, to = place, price = batch.price,
                revisions = mapOf(balance.id to balance.revision))).also(::validate)
    }

    fun change(s: Inventory, balanceId: String, count: Int, type: String, target: String? = null,
        operationId: String = newId(), allowExpired: Boolean = false): Inventory {
        if (s.movements.any { it.id == operationId }) return s
        require(type in listOf("消耗", "丢弃", "盘点增加", "盘点减少", "移动"))
        val source = s.balances.first { it.id == balanceId }; val batch = s.batches.first { it.id == source.batchId }
        require(count > 0) { "数量应大于 0" }
        require(type == "盘点增加" || source.quantity >= count) { "可用库存不足" }
        require(type != "消耗" || allowExpired || batch.expires == null || LocalDate.parse(batch.expires) >= LocalDate.now()) { "此批次已过期，请确认后再消耗" }
        require(type != "移动" || (target != null && target != source.placeId)) { "请选择不同的目标位置" }
        require(type != "移动" || s.places.any { it.id == target && !it.archived }) { "目标位置不存在或已归档" }
        val changed = source.copy(quantity = if (type == "盘点增加") Math.addExact(source.quantity, count) else source.quantity - count, revision = source.revision + 1)
        var balances = s.balances.map { if (it.id == source.id) changed else it }
        val revisions = mutableMapOf(changed.id to changed.revision)
        if (type == "移动") {
            val existing = balances.firstOrNull { it.batchId == source.batchId && it.placeId == target }
            val dest = existing?.copy(quantity = Math.addExact(existing.quantity, count), revision = existing.revision + 1)
                ?: Balance(batchId = source.batchId, placeId = target!!, quantity = count)
            balances = balances.filterNot { it.id == dest.id } + dest; revisions[dest.id] = dest.revision
        }
        return s.copy(balances = balances, movements = s.movements + Movement(operationId, source.batchId, type, count,
            from = if (type == "盘点增加") null else source.placeId,
            to = if (type == "盘点增加") source.placeId else target, price = batch.price,
            reason = if (type == "丢弃" && batch.expires != null && LocalDate.parse(batch.expires) < LocalDate.now()) "过期丢弃" else "",
            revisions = revisions)).also(::validate)
    }

    fun undo(s: Inventory, operationId: String): Inventory {
        val op = s.movements.first { it.id == operationId }
        require(op.type != "撤销" && s.movements.none { it.reversedId == op.id }) { "此操作已撤销或不可撤销" }
        require(op.revisions.isNotEmpty() && op.revisions.all { (id, rev) -> s.balances.any { it.id == id && it.revision == rev } }) { "库存已有后续变更，请使用盘点调整" }
        val balances = s.balances.map { b ->
            if (b.id !in op.revisions) b else {
                val delta = (if (b.placeId == op.from) op.quantity else 0) - (if (b.placeId == op.to) op.quantity else 0)
                b.copy(quantity = b.quantity + delta, revision = b.revision + 1)
            }
        }
        return s.copy(balances = balances, movements = s.movements + Movement(batchId = op.batchId, type = "撤销", quantity = op.quantity,
            from = op.to, to = op.from, price = op.price, reversedId = op.id)).also(::validate)
    }

    fun savePlace(s: Inventory, p: Place, layerCount: Int? = null): Inventory {
        require(p.id != UNPLACED) { "系统位置不可修改" }
        require(p.parentId == null || s.places.any { it.id == p.parentId && !it.archived }) { "上级位置已归档" }
        if (p.archived) {
            val descendants = s.descendants(p.id)
            require(s.balances.none { it.placeId in descendants && it.quantity > 0 }) { "请先移走在库物品" }
            require(s.places.none { it.parentId == p.id && !it.archived }) { "请先归档子空间" }
        }
        val next = s.copy(places = s.places.filterNot { it.id == p.id } + p.copy(name = p.name.trim()))
        val result = next.copy(boxes = next.boxes.filter { it.id in next.descendants(it.roomId) })
        return (if (layerCount == null) result else setContainerLayers(result, p.id, layerCount)).also(::validate)
    }
    fun setContainerLayers(s: Inventory, containerId: String, count: Int): Inventory {
        require(count in 0..20) { "层数应为 0 到 20" }
        require(s.places.any { it.id == containerId && it.kind == "CONTAINER" && !it.archived }) { "请选择未归档的容器" }
        val existing = s.places.filter { it.parentId == containerId && it.kind == "LAYER" }
        existing.filter { (it.layerNumber ?: 0) > count }.forEach { layer ->
            val ids = s.descendants(layer.id)
            require(s.balances.none { it.placeId in ids && it.quantity > 0 }) { "${layer.name}还有物品，请先移动后再减少层数" }
            require(s.places.none { it.parentId == layer.id && !it.archived }) { "${layer.name}还有子容器，请先移走或归档" }
        }
        val places = s.places.map { p -> if (p in existing) p.copy(archived = p.layerNumber!! > count) else p }.toMutableList()
        for (number in 1..count) {
            if (existing.none { it.layerNumber == number }) places.add(Place(parentId = containerId, kind = "LAYER", name = "第 $number 层", order = number, layerNumber = number))
        }
        return s.copy(version = 2, places = places).also(::validate)
    }
    fun deletePlace(s: Inventory, id: String): Inventory {
        require(id != UNPLACED)
        require(s.places.none { it.id == id && it.kind == "LAYER" }) { "请在容器设置中调整层数" }
        require(s.places.none { it.parentId == id } && s.balances.none { it.placeId == id } && s.movements.none { it.from == id || it.to == id }) { "位置仍有关联库存、子空间或历史记录，可改名保留" }
        return s.copy(places = s.places.filterNot { it.id == id }, boxes = s.boxes.filterNot { it.id == id || it.roomId == id })
    }
    fun removeLabel(s: Inventory, id: String, mergeTo: String? = null): Inventory = s.copy(
        labels = s.labels.filterNot { it.id == id }, items = s.items.map {
            it.copy(tags = (it.tags.filterNot { tag -> tag == id } + if (id in it.tags && mergeTo != null) listOf(mergeTo) else emptyList()).distinct())
        }).also(::validate)
}

data class StorageTemplate(val name: String, val description: String, val sections: List<String>, val packing: Boolean = false)
val templates = listOf(
    StorageTemplate("衣柜分区", "按使用频率，给每件衣物一个位置", listOf("挂衣区", "叠衣区", "内衣区", "换季区")),
    StorageTemplate("抽屉分格", "小物分格，打开就能找到", listOf("文具", "线材", "小工具", "证件")),
    StorageTemplate("零食柜", "分门别类，临期优先", listOf("饼干", "坚果", "饮品", "临期优先区")),
    StorageTemplate("化妆品收纳", "日常与囤货分开放", listOf("日常护肤", "彩妆", "工具", "囤货")),
    StorageTemplate("行李箱打包", "出发前，把遗漏留在家里", listOf("证件", "衣物", "洗漱用品", "电子设备", "药品"), true)
)

interface RecognitionService { suspend fun recognize(image: ByteArray): List<RecognitionCandidate> }
data class RecognitionCandidate(val name: String, val category: String, val confidence: Float?)
interface CloudBackupService {
    suspend fun upload(encryptedArchive: java.io.File): String
    suspend fun list(): List<CloudSnapshot>
    suspend fun download(id: String, destination: java.io.File)
    suspend fun delete(id: String)
}
data class CloudSnapshot(val id: String, val createdAt: Long, val bytes: Long, val checksum: String)
