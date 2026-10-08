package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.macareen.stitchbook2.domain.model.CraftingSession

/**
 * `project_id` is SET_NULL rather than CASCADE: deleting a project keeps
 * its recorded crafting time in overall statistics, just unattributed.
 */
@Entity(
    tableName = "crafting_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("project_id")]
)
data class CraftingSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "paused_at") val pausedAt: Long?,
    @ColumnInfo(name = "paused_total_millis") val pausedTotalMillis: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "rows_completed") val rowsCompleted: Int?,
    @ColumnInfo(name = "stitches_per_row") val stitchesPerRow: Int?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun CraftingSessionEntity.toDomain() = CraftingSession(
    id, projectId, startedAt, endedAt, pausedAt, pausedTotalMillis, zoneId,
    rowsCompleted, stitchesPerRow, notes, createdAt, updatedAt
)

fun CraftingSession.toEntity() = CraftingSessionEntity(
    id, projectId, startedAt, endedAt, pausedAt, pausedTotalMillis, zoneId,
    rowsCompleted, stitchesPerRow, notes, createdAt, updatedAt
)
