package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * The many-to-many join ARCHITECTURE.md §9 calls for between Projects and
 * Tools ("explicit join entities for many-to-many relationships such as
 * ... projects-tools"): a pure membership record, no metadata of its own
 * (PRODUCT_SPEC.md 6.8 -- project assignment is a fact about the underlying
 * [ToolItemEntity], not a second stock count or a per-assignment note). Both
 * FKs cascade: a join row has no meaning once either side it links is gone.
 */
@Entity(
    tableName = "project_tool_assignments",
    primaryKeys = ["project_id", "tool_item_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ToolItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["tool_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tool_item_id")]
)
data class ProjectToolAssignmentEntity(
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "tool_item_id") val toolItemId: String
)
