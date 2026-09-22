package com.smartstorage.core

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
const val UNPLACED = "unplaced"
val categories = listOf("未分类", "食品", "护肤彩妆", "药品", "衣物", "日用品", "数码家电", "书籍文具", "其他")

@Serializable data class Place(val id: String = newId(), val parentId: String? = null, val kind: String,
    val name: String, val order: Int = 0, val archived: Boolean = false, val layerNumber: Int? = null)
@Serializable data class Thing(val id: String = newId(), val name: String, val category: String = "未分类",
    val unit: String = "件", val tags: List<String> = emptyList(), val notes: String = "", val valuable: Boolean = false,
    val photo: String? = null, val createdAt: Long = System.currentTimeMillis(), val archived: Boolean = false)
@Serializable data class Batch(val id: String = newId(), val itemId: String, val purchased: String? = null,
    val expires: String? = null, val price: Long? = null, val createdAt: Long = System.currentTimeMillis(),
    val leadDays: Int? = null, val reminderEnabled: Boolean = true)
@Serializable data class Balance(val id: String = newId(), val batchId: String, val placeId: String = UNPLACED,
    val quantity: Int, val revision: Int = 0)
@Serializable data class Movement(val id: String = newId(), val batchId: String, val type: String,
    val quantity: Int, val from: String? = null, val to: String? = null, val price: Long? = null,
    val at: Long = System.currentTimeMillis(), val reason: String = "", val reversedId: String? = null,
    val revisions: Map<String, Int> = emptyMap())
@Serializable data class Label(val id: String = newId(), val name: String, val color: Long = 0xFF236B50)
@Serializable data class LayoutBox(val id: String, val roomId: String, val x: Float = .1f, val y: Float = .1f,
    val width: Float = .3f, val height: Float = .2f, val rotation: Int = 0)
@Serializable data class Packing(val id: String = newId(), val title: String, val entries: List<CheckEntry>)
@Serializable data class CheckEntry(val id: String = newId(), val title: String, val done: Boolean = false)
@Serializable data class Preferences(val reminderEnabled: Boolean = false, val leadDays: Int = 7,
    val lastNotified: String? = null)
@Serializable data class Inventory(val version: Int = 2, val libraryId: String = newId(),
    val places: List<Place> = listOf(Place(UNPLACED, kind = "SYSTEM", name = "待归位")),
    val items: List<Thing> = emptyList(), val batches: List<Batch> = emptyList(),
    val balances: List<Balance> = emptyList(), val movements: List<Movement> = emptyList(),
    val labels: List<Label> = emptyList(), val boxes: List<LayoutBox> = emptyList(),
    val packing: List<Packing> = emptyList(), val preferences: Preferences = Preferences()) {
    fun layers(containerId: String) = places.filter { it.parentId == containerId && it.kind == "LAYER" && !it.archived }.sortedBy { it.layerNumber }
    fun path(id: String?): String {
        val nodes = places.associateBy { it.id }
        val parts = mutableListOf<String>()
        var cursor = id
        val seen = mutableSetOf<String>()
        while (cursor != null && seen.add(cursor)) {
            val p = nodes[cursor] ?: break
            parts.add(p.name); cursor = p.parentId
        }
        return parts.asReversed().joinToString(" / ")
    }
    fun descendants(id: String): Set<String> {
        val result = mutableSetOf(id)
        do { val before = result.size; places.filter { it.parentId in result }.forEach { result.add(it.id) } }
        while (before != result.size)
        return result
    }
    fun rows(): List<StockRow> {
        val itemMap = items.associateBy { it.id }; val batchMap = batches.associateBy { it.id }
        return balances.mapNotNull { bal -> batchMap[bal.batchId]?.let { b -> itemMap[b.itemId]?.let { StockRow(it, b, bal) } } }
    }
}
data class StockRow(val item: Thing, val batch: Batch, val balance: Balance) {
    fun expired(today: LocalDate = LocalDate.now()) = batch.expires?.let { LocalDate.parse(it) < today } ?: false
    fun due(days: Int, today: LocalDate = LocalDate.now()) = batch.expires?.let { LocalDate.parse(it) in today..today.plusDays((batch.leadDays ?: days).toLong()) } ?: false
    val value: Long get() = Math.multiplyExact(batch.price ?: 0, balance.quantity.toLong())
}

data class Filter(val query: String = "", val category: String? = null, val place: String? = null,
    val tags: Set<String> = emptySet(), val allTags: Boolean = false, val expiry: String = "全部",
    val minPrice: Long? = null, val maxPrice: Long? = null, val unknownPrice: Boolean = false,
    val includeEmpty: Boolean = false, val valuable: Boolean = false, val sort: String = "最近添加",
    val dateFrom: String? = null, val dateTo: String? = null)

fun Inventory.filtered(filter: Filter, today: LocalDate = LocalDate.now()): List<StockRow> {
    val locations = filter.place?.let(::descendants)
    val labelMap = labels.associate { it.id to it.name }
    val query = filter.query.trim().lowercase()
    return rows().filter { r ->
        (!r.item.archived || filter.includeEmpty) && (filter.includeEmpty || r.balance.quantity > 0) &&
        (filter.category == null || r.item.category == filter.category) &&
        (locations == null || r.balance.placeId in locations) &&
        (!filter.valuable || r.item.valuable) &&
        (filter.tags.isEmpty() || if (filter.allTags) r.item.tags.containsAll(filter.tags) else r.item.tags.any { it in filter.tags }) &&
        (query.isEmpty() || listOf(r.item.name, r.item.notes, r.item.category, path(r.balance.placeId),
            r.item.tags.mapNotNull { labelMap[it] }.joinToString(" ")).any { it.lowercase().contains(query) }) &&
        (when (filter.expiry) { "已过期" -> r.expired(today); "即将到期" -> r.due(preferences.leadDays, today)
            "未到期" -> r.batch.expires != null && !r.expired(today); "未设置" -> r.batch.expires == null; else -> true }) &&
        (!filter.unknownPrice || r.batch.price == null) &&
        (filter.minPrice == null || (r.batch.price != null && r.batch.price >= filter.minPrice)) &&
        (filter.maxPrice == null || (r.batch.price != null && r.batch.price <= filter.maxPrice)) &&
        (filter.dateFrom == null || (r.batch.expires != null && r.batch.expires >= filter.dateFrom)) &&
        (filter.dateTo == null || (r.batch.expires != null && r.batch.expires <= filter.dateTo))
    }
}

fun money(minor: Long): String = java.math.BigDecimal.valueOf(minor, 2).toPlainString()
fun parsePrice(value: String): Long? = if (value.isBlank()) null else
    value.toBigDecimalOrNull()?.let { require(it.signum() >= 0) { "价格不能为负数" }; it.movePointRight(2).longValueExact() }
        ?: throw IllegalArgumentException("请输入有效价格，最多两位小数")
fun parseDate(value: String): String? = if (value.isBlank()) null else
    runCatching { LocalDate.parse(value.trim()).toString() }.getOrElse { throw IllegalArgumentException("日期格式应为 YYYY-MM-DD") }
