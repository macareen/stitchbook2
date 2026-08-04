package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem

@Entity(tableName = "stash_items")
data class StashItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val brand: String?,
    val colorway: String?,
    @ColumnInfo(name = "dye_lot") val dyeLot: String?,
    @ColumnInfo(name = "weight_category") val weightCategory: String?,
    @ColumnInfo(name = "fiber_content") val fiberContent: String?,
    val quantity: Double,
    @ColumnInfo(name = "unit_label") val unitLabel: String,
    @ColumnInfo(name = "yardage_per_unit") val yardagePerUnit: Double?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun StashItemEntity.toDomain(): StashItem {
    return StashItem(
        id = id,
        name = name,
        category = StashCategory.fromStorageValue(category)
            ?: throw UnknownStashItemValueException("category", category),
        brand = brand,
        colorway = colorway,
        dyeLot = dyeLot,
        weightCategory = weightCategory,
        fiberContent = fiberContent,
        quantity = quantity,
        unitLabel = unitLabel,
        yardagePerUnit = yardagePerUnit,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun StashItem.toEntity(): StashItemEntity {
    return StashItemEntity(
        id = id,
        name = name,
        category = category.storageValue,
        brand = brand,
        colorway = colorway,
        dyeLot = dyeLot,
        weightCategory = weightCategory,
        fiberContent = fiberContent,
        quantity = quantity,
        unitLabel = unitLabel,
        yardagePerUnit = yardagePerUnit,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

class UnknownStashItemValueException(
    field: String,
    value: String
) : IllegalStateException("Unknown stored stash item $field value: $value")
