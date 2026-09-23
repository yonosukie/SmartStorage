package com.smartstorage.core

import kotlinx.serialization.Serializable

@Serializable data class RecognizedItem(
    val name: String? = null,
    val category: String? = null,
    val unit: String? = null,
    val notes: String? = null,
    val suggestedTags: List<String> = emptyList()
)

@Serializable data class RecognitionResult(val items: List<RecognizedItem>)

/** Only suggestion fields; stock, price, dates and location never come from the model. */
data class RecognitionDraft(
    val name: String = "", val category: String = "未分类", val unit: String = "件",
    val notes: String = "", val tags: List<String> = emptyList()
) {
    fun fill(item: RecognizedItem, labels: List<Label>, touched: Set<String>): RecognitionDraft = copy(
        name = if ("name" !in touched && name.isBlank()) item.name ?: name else name,
        category = if ("category" !in touched && category in listOf("", "未分类")) item.category ?: category else category,
        unit = if ("unit" !in touched && unit in listOf("", "件")) item.unit ?: unit else unit,
        notes = if ("notes" !in touched && notes.isBlank()) item.notes ?: notes else notes,
        tags = if ("tags" !in touched && tags.isEmpty()) labels.filter { it.name in item.suggestedTags }.map { it.id } else tags
    )

    fun undo(before: RecognitionDraft, applied: RecognitionDraft, touched: Set<String>): RecognitionDraft = copy(
        name = if ("name" !in touched && name == applied.name) before.name else name,
        category = if ("category" !in touched && category == applied.category) before.category else category,
        unit = if ("unit" !in touched && unit == applied.unit) before.unit else unit,
        notes = if ("notes" !in touched && notes == applied.notes) before.notes else notes,
        tags = if ("tags" !in touched && tags == applied.tags) before.tags else tags
    )
}
