package com.macareen.stitchbook2.domain.model

data class LibraryItem(
    val id: String,
    val title: String,
    val craft: Craft,
    val author: String?,
    val sourceUrl: String?,
    val tags: List<String>,
    val notes: String?,
    val bookmarked: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

fun normalizedLibraryItemTitle(value: String): String? {
    return value.trim().takeIf { it.isNotEmpty() }
}

// Tags are stored joined by commas (see LibraryItemEntity), so commas are
// stripped here rather than escaped.
fun normalizedLibraryItemTags(values: List<String>): List<String> {
    return values
        .map { it.replace(",", "").trim() }
        .filter { it.isNotEmpty() }
}
