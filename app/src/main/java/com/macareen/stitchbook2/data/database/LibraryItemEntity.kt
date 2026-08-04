package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem

@Entity(tableName = "library_items")
data class LibraryItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val craft: String,
    val author: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    val tags: String,
    val notes: String?,
    val bookmarked: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

// Tags are normalized on the way in (normalizedLibraryItemTags strips
// commas), so a plain comma join needs no CSV-style escaping.
private const val TAG_DELIMITER = ","

private fun encodeTags(tags: List<String>): String = tags.joinToString(TAG_DELIMITER)

private fun decodeTags(value: String): List<String> =
    if (value.isEmpty()) emptyList() else value.split(TAG_DELIMITER)

fun LibraryItemEntity.toDomain(): LibraryItem {
    return LibraryItem(
        id = id,
        title = title,
        craft = Craft.fromStorageValue(craft)
            ?: throw UnknownLibraryItemValueException("craft", craft),
        author = author,
        sourceUrl = sourceUrl,
        tags = decodeTags(tags),
        notes = notes,
        bookmarked = bookmarked,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun LibraryItem.toEntity(): LibraryItemEntity {
    return LibraryItemEntity(
        id = id,
        title = title,
        craft = craft.storageValue,
        author = author,
        sourceUrl = sourceUrl,
        tags = encodeTags(tags),
        notes = notes,
        bookmarked = bookmarked,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

class UnknownLibraryItemValueException(
    field: String,
    value: String
) : IllegalStateException("Unknown stored library item $field value: $value")
