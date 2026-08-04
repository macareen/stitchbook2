package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val craft: String,
    @ColumnInfo(name = "project_type") val projectType: String,
    val status: String,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun ProjectEntity.toDomain(): Project {
    return Project(
        id = id,
        name = name,
        craft = Craft.fromStorageValue(craft)
            ?: throw UnknownProjectValueException("craft", craft),
        projectType = ProjectType.fromStorageValue(projectType)
            ?: throw UnknownProjectValueException("project_type", projectType),
        status = ProjectStatus.fromStorageValue(status)
            ?: throw UnknownProjectValueException("status", status),
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Project.toEntity(): ProjectEntity {
    return ProjectEntity(
        id = id,
        name = name,
        craft = craft.storageValue,
        projectType = projectType.storageValue,
        status = status.storageValue,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

class UnknownProjectValueException(
    field: String,
    value: String
) : IllegalStateException("Unknown stored project $field value: $value")
