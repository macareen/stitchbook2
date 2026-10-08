package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.macareen.stitchbook2.domain.model.ProjectPatternLink
import com.macareen.stitchbook2.domain.model.YarnAllocation

/**
 * Unlike the pure-membership project-tool join, an allocation carries its
 * own quantities, so it has a surrogate id (one stash item can be allocated
 * twice to the same project, e.g. body and trim). Both FKs cascade: the
 * consumed amount has already been subtracted from the stash item, so
 * nothing is lost by dropping the record with either parent.
 */
@Entity(
    tableName = "yarn_allocations",
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
        )
    ],
    indices = [Index("project_id"), Index("stash_item_id")]
)
data class YarnAllocationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "stash_item_id") val stashItemId: String,
    @ColumnInfo(name = "quantity_reserved") val quantityReserved: Double,
    @ColumnInfo(name = "quantity_used") val quantityUsed: Double,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun YarnAllocationEntity.toDomain() = YarnAllocation(
    id = id,
    projectId = projectId,
    stashItemId = stashItemId,
    quantityReserved = quantityReserved,
    quantityUsed = quantityUsed,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun YarnAllocation.toEntity() = YarnAllocationEntity(
    id = id,
    projectId = projectId,
    stashItemId = stashItemId,
    quantityReserved = quantityReserved,
    quantityUsed = quantityUsed,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

@Entity(
    tableName = "project_pattern_links",
    primaryKeys = ["project_id", "library_item_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LibraryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["library_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("library_item_id")]
)
data class ProjectPatternLinkEntity(
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "library_item_id") val libraryItemId: String
)

fun ProjectPatternLinkEntity.toDomain() = ProjectPatternLink(projectId, libraryItemId)

fun ProjectPatternLink.toEntity() = ProjectPatternLinkEntity(projectId, libraryItemId)
