package com.macareen.stitchbook2.data.database

import androidx.room.*
import com.macareen.stitchbook2.domain.model.ProjectPiece

@Entity(tableName = "project_pieces", foreignKeys = [
    ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["project_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = GuideEntity::class, parentColumns = ["id"], childColumns = ["guide_id"], onDelete = ForeignKey.CASCADE)
], indices = [Index("project_id"), Index("guide_id")])
data class ProjectPieceEntity(@PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "guide_id") val guideId: String,
    val name: String,
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long)

fun ProjectPieceEntity.toDomain() = ProjectPiece(id, projectId, guideId, name, isArchived, createdAt, updatedAt)
