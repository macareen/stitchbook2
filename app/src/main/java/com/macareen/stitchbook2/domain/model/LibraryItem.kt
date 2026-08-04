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
    val updatedAt: Long,
    /**
     * A persisted-permission `content://` URI selected through the Storage
     * Access Framework -- never a path into app-private storage, and never a
     * copy of the original file. [pdfFileName] is the display name captured
     * at attach time so it can still be shown if the URI later becomes
     * inaccessible (revoked permission, moved/deleted file).
     */
    val pdfUri: String? = null,
    val pdfFileName: String? = null,
    /** 0-indexed. Null until the in-app viewer has been opened at least once. */
    val pdfLastViewedPage: Int? = null
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
