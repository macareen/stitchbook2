package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "guides",
    primaryKeys = ["id"],
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
data class GuideEntity(
    val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    val name: String,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * An immutable, validated snapshot of a Guide's definition tree.
 *
 * Once inserted, a revision row (and its owned [RevisionNodeEntity] rows) must
 * never be updated in place. Correcting a published definition always creates
 * a new [DefinitionRevisionEntity] with the next [revisionNumber]; it never
 * mutates an existing one.
 *
 * Revisions are also never deleted individually. The only way a revision row
 * is removed is as part of a Guide-level cascade delete (see [GuideEntity]'s
 * `ON DELETE CASCADE` foreign key below). This is why [GuideDraftEntity]'s
 * `base_revision_id` foreign key uses `ON DELETE SET NULL` rather than
 * `CASCADE`: it only exists to tolerate that cascade path, not to support
 * standalone revision deletion.
 */
@Entity(
    tableName = "definition_revisions",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = GuideEntity::class,
            parentColumns = ["id"],
            childColumns = ["guide_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("guide_id"),
        Index(value = ["guide_id", "revision_number"], unique = true)
    ]
)
data class DefinitionRevisionEntity(
    val id: String,
    @ColumnInfo(name = "guide_id") val guideId: String,
    @ColumnInfo(name = "revision_number") val revisionNumber: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "guide_drafts",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = GuideEntity::class,
            parentColumns = ["id"],
            childColumns = ["guide_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            // SET_NULL (not CASCADE) only tolerates a revision disappearing as
            // part of this same draft's Guide-level cascade delete. Revisions
            // are never deleted on their own — see the KDoc on
            // DefinitionRevisionEntity.
            entity = DefinitionRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["base_revision_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        // A unique index on guide_id is the database-level enforcement of
        // "at most one editable Draft per Guide." This is a hard schema
        // constraint, not merely a repository-layer convention.
        Index(value = ["guide_id"], unique = true),
        Index("base_revision_id")
    ]
)
data class GuideDraftEntity(
    val id: String,
    @ColumnInfo(name = "guide_id") val guideId: String,
    @ColumnInfo(name = "base_revision_id") val baseRevisionId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val version: Long
)

@Entity(
    tableName = "draft_nodes",
    primaryKeys = ["draft_id", "node_id"],
    foreignKeys = [
        ForeignKey(
            entity = GuideDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draft_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            // Deferred so an entire node tree can be inserted in one batch
            // without requiring parent rows to be inserted before their
            // children. The constraint is still checked, just at transaction
            // commit instead of per-statement.
            entity = DraftNodeEntity::class,
            parentColumns = ["draft_id", "node_id"],
            childColumns = ["draft_id", "parent_node_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true
        )
    ],
    indices = [
        Index(value = ["draft_id", "parent_node_id"])
    ]
)
data class DraftNodeEntity(
    @ColumnInfo(name = "draft_id") val draftId: String,
    @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "parent_node_id") val parentNodeId: String?,
    @ColumnInfo(name = "child_order") val childOrder: Int,
    val type: String,
    val title: String?,
    @ColumnInfo(name = "instruction_text") val instructionText: String?,
    @ColumnInfo(name = "range_unit_label") val rangeUnitLabel: String?,
    @ColumnInfo(name = "range_start_inclusive") val rangeStartInclusive: Int?,
    @ColumnInfo(name = "range_end_inclusive") val rangeEndInclusive: Int?,
    @ColumnInfo(name = "repeat_count") val repeatCount: Int?,
    @ColumnInfo(name = "repeat_label") val repeatLabel: String?
)

@Entity(
    tableName = "revision_nodes",
    primaryKeys = ["revision_id", "node_id"],
    foreignKeys = [
        ForeignKey(
            entity = DefinitionRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["revision_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            // Deferred so an entire node tree can be inserted in one batch
            // without requiring parent rows to be inserted before their
            // children. The constraint is still checked, just at transaction
            // commit instead of per-statement.
            entity = RevisionNodeEntity::class,
            parentColumns = ["revision_id", "node_id"],
            childColumns = ["revision_id", "parent_node_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true
        )
    ],
    indices = [
        Index(value = ["revision_id", "parent_node_id"])
    ]
)
data class RevisionNodeEntity(
    @ColumnInfo(name = "revision_id") val revisionId: String,
    @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "parent_node_id") val parentNodeId: String?,
    @ColumnInfo(name = "child_order") val childOrder: Int,
    val type: String,
    val title: String?,
    @ColumnInfo(name = "instruction_text") val instructionText: String?,
    @ColumnInfo(name = "range_unit_label") val rangeUnitLabel: String?,
    @ColumnInfo(name = "range_start_inclusive") val rangeStartInclusive: Int?,
    @ColumnInfo(name = "range_end_inclusive") val rangeEndInclusive: Int?,
    @ColumnInfo(name = "repeat_count") val repeatCount: Int?,
    @ColumnInfo(name = "repeat_label") val repeatLabel: String?
)
