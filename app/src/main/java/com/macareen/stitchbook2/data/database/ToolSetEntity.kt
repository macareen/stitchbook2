package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.macareen.stitchbook2.domain.model.ToolSet

@Entity(tableName = "tool_sets")
data class ToolSetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun ToolSetEntity.toDomain(): ToolSet {
    return ToolSet(
        id = id,
        name = name,
        brand = brand,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun ToolSet.toEntity(): ToolSetEntity {
    return ToolSetEntity(
        id = id,
        name = name,
        brand = brand,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
