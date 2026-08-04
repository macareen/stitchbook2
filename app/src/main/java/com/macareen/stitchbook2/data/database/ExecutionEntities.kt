package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One Execution of one immutable Definition Revision.
 *
 * `definition_revision_id` is fixed at creation and never updated: an
 * Execution must remain attached to the Revision it was created from, even
 * after the owning Guide publishes newer Revisions. Its foreign key
 * deliberately omits `onDelete`, which defaults to `NO ACTION` — this
 * blocks deleting a Definition Revision independently while an Execution
 * still references it. Deleting the owning Guide still removes this row,
 * because `guide_id` cascades directly from [GuideEntity]; that cascade
 * also removes every [DefinitionRevisionEntity] for the same guide in the
 * same statement, so the `NO ACTION` check on `definition_revision_id`
 * never blocks a full guide deletion.
 *
 * `current_instruction_node_id` is null if and only if `status` is
 * `COMPLETED`; its ordered ancestry frames live in
 * [ExecutionCurrentAddressFrameEntity]. `version` is optimistic-concurrency
 * metadata, mirroring [GuideDraftEntity.version].
 */
@Entity(
    tableName = "executions",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = GuideEntity::class,
            parentColumns = ["id"],
            childColumns = ["guide_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DefinitionRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["definition_revision_id"]
        )
    ],
    indices = [
        Index("guide_id"),
        Index("definition_revision_id")
    ]
)
data class ExecutionEntity(
    val id: String,
    @ColumnInfo(name = "guide_id") val guideId: String,
    @ColumnInfo(name = "definition_revision_id") val definitionRevisionId: String,
    val status: String,
    @ColumnInfo(name = "current_instruction_node_id")
    val currentInstructionNodeId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    val version: Long
)

/**
 * One ordered ancestry frame of an Execution's current pointer.
 *
 * Replaced wholesale on every transition (delete-then-reinsert), so its row
 * count is always exactly the current address's ancestry depth, never a
 * permanently expanded history.
 */
@Entity(
    tableName = "execution_current_address_frames",
    primaryKeys = ["execution_id", "frame_order"],
    foreignKeys = [
        ForeignKey(
            entity = ExecutionEntity::class,
            parentColumns = ["id"],
            childColumns = ["execution_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ExecutionCurrentAddressFrameEntity(
    @ColumnInfo(name = "execution_id") val executionId: String,
    @ColumnInfo(name = "frame_order") val frameOrder: Int,
    @ColumnInfo(name = "container_node_id") val containerNodeId: String,
    @ColumnInfo(name = "frame_type") val frameType: String,
    @ColumnInfo(name = "frame_value") val frameValue: Int
)

/**
 * One completed executable occurrence for an Execution.
 *
 * `address_signature` is a derived, injective (collision-free) encoding of
 * the occurrence's Instruction Node identity and ordered ancestry frames —
 * see `toSignature()` in `ExecutionEntityMapping.kt`. It is not display
 * text, and it is not itself the canonical identity of the address: the
 * normalized frame rows in [ExecutionCompletedOccurrenceFrameEntity] are.
 * The signature exists only so the primary key
 * `(execution_id, address_signature)` can keep completed occurrence
 * addresses unique as a set at the database level — inserting the same
 * occurrence twice for the same Execution violates the primary key.
 */
@Entity(
    tableName = "execution_completed_occurrences",
    primaryKeys = ["execution_id", "address_signature"],
    foreignKeys = [
        ForeignKey(
            entity = ExecutionEntity::class,
            parentColumns = ["id"],
            childColumns = ["execution_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ExecutionCompletedOccurrenceEntity(
    @ColumnInfo(name = "execution_id") val executionId: String,
    @ColumnInfo(name = "address_signature") val addressSignature: String,
    @ColumnInfo(name = "instruction_node_id") val instructionNodeId: String
)

/** One ordered ancestry frame of one completed occurrence. */
@Entity(
    tableName = "execution_completed_occurrence_frames",
    primaryKeys = ["execution_id", "address_signature", "frame_order"],
    foreignKeys = [
        ForeignKey(
            entity = ExecutionCompletedOccurrenceEntity::class,
            parentColumns = ["execution_id", "address_signature"],
            childColumns = ["execution_id", "address_signature"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ExecutionCompletedOccurrenceFrameEntity(
    @ColumnInfo(name = "execution_id") val executionId: String,
    @ColumnInfo(name = "address_signature") val addressSignature: String,
    @ColumnInfo(name = "frame_order") val frameOrder: Int,
    @ColumnInfo(name = "container_node_id") val containerNodeId: String,
    @ColumnInfo(name = "frame_type") val frameType: String,
    @ColumnInfo(name = "frame_value") val frameValue: Int
)

/**
 * Database-level enforcement of "at most one ACTIVE Execution per Guide."
 *
 * A row exists for a Guide if and only if that Guide currently has an ACTIVE
 * Execution. `guide_id` is the primary key, so a second attempt to activate
 * an Execution for a Guide that already has one violates the primary key
 * and the whole creating/reopening transaction rolls back — this is
 * enforced by SQLite itself, not only by a repository-level check. The row
 * is deleted when its Execution completes and reinserted if a later
 * transition (Previous) reopens a completed Execution, mirroring the
 * one-row-per-guide shape of [GuideDraftEntity].
 */
@Entity(
    tableName = "active_executions",
    primaryKeys = ["guide_id"],
    foreignKeys = [
        ForeignKey(
            entity = GuideEntity::class,
            parentColumns = ["id"],
            childColumns = ["guide_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExecutionEntity::class,
            parentColumns = ["id"],
            childColumns = ["execution_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["execution_id"], unique = true)
    ]
)
data class ActiveExecutionEntity(
    @ColumnInfo(name = "guide_id") val guideId: String,
    @ColumnInfo(name = "execution_id") val executionId: String
)
