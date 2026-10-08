package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.macareen.stitchbook2.domain.model.JournalEntry
import com.macareen.stitchbook2.domain.model.Milestone
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.model.PhotoRole

/**
 * Photo *references*. Cascading deletes only remove this row -- the
 * external original lives in user storage and is never touched
 * (PRODUCT_SPEC.md 6.9). A deleted milestone just detaches its photos.
 */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StashItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["stash_item_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MilestoneEntity::class,
            parentColumns = ["id"],
            childColumns = ["milestone_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("project_id"), Index("stash_item_id"), Index("milestone_id")]
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String?,
    @ColumnInfo(name = "stash_item_id") val stashItemId: String?,
    val uri: String,
    @ColumnInfo(name = "display_name") val displayName: String?,
    val caption: String?,
    @ColumnInfo(name = "taken_date") val takenDate: String?,
    @ColumnInfo(name = "milestone_id") val milestoneId: String?,
    val role: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun PhotoEntity.toDomain() = Photo(
    id = id,
    projectId = projectId,
    stashItemId = stashItemId,
    uri = uri,
    displayName = displayName,
    caption = caption,
    takenDate = takenDate,
    milestoneId = milestoneId,
    role = PhotoRole.fromStorageValue(role),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Photo.toEntity() = PhotoEntity(
    id = id,
    projectId = projectId,
    stashItemId = stashItemId,
    uri = uri,
    displayName = displayName,
    caption = caption,
    takenDate = takenDate,
    milestoneId = milestoneId,
    role = role.storageValue,
    createdAt = createdAt,
    updatedAt = updatedAt
)

@Entity(
    tableName = "journal_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("project_id")]
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "entry_date") val entryDate: String,
    val title: String?,
    val body: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun JournalEntryEntity.toDomain() = JournalEntry(id, projectId, entryDate, title, body, createdAt, updatedAt)

fun JournalEntry.toEntity() = JournalEntryEntity(id, projectId, entryDate, title, body, createdAt, updatedAt)

@Entity(
    tableName = "milestones",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("project_id")]
)
data class MilestoneEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    val title: String,
    @ColumnInfo(name = "reached_date") val reachedDate: String?,
    val notes: String?,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun MilestoneEntity.toDomain() = Milestone(id, projectId, title, reachedDate, notes, position, createdAt, updatedAt)

fun Milestone.toEntity() = MilestoneEntity(id, projectId, title, reachedDate, notes, position, createdAt, updatedAt)
