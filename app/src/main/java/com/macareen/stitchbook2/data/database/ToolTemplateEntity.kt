package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.macareen.stitchbook2.domain.model.BulkSizeInputMode
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolTemplate

@Entity(tableName = "tool_templates")
data class ToolTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val brand: String?,
    val material: String?,
    @ColumnInfo(name = "size_input_mode") val sizeInputMode: String,
    @ColumnInfo(name = "range_start") val rangeStart: Double?,
    @ColumnInfo(name = "range_end") val rangeEnd: Double?,
    @ColumnInfo(name = "range_increment") val rangeIncrement: Double?,
    @ColumnInfo(name = "custom_sizes") val customSizes: String?,
    @ColumnInfo(name = "quantity_per_size") val quantityPerSize: Int,
    @ColumnInfo(name = "storage_location") val storageLocation: String?,
    val notes: String?,
    @ColumnInfo(name = "create_as_set") val createAsSet: Boolean,
    @ColumnInfo(name = "set_name") val setName: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun ToolTemplateEntity.toDomain(): ToolTemplate {
    return ToolTemplate(
        id = id,
        name = name,
        category = ToolCategory.fromStorageValue(category)
            ?: throw UnknownToolItemValueException("category", category),
        brand = brand,
        material = material,
        sizeInputMode = BulkSizeInputMode.entries.firstOrNull { it.name == sizeInputMode }
            ?: throw UnknownToolItemValueException("sizeInputMode", sizeInputMode),
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        rangeIncrement = rangeIncrement,
        customSizes = customSizes,
        quantityPerSize = quantityPerSize,
        storageLocation = storageLocation,
        notes = notes,
        createAsSet = createAsSet,
        setName = setName,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun ToolTemplate.toEntity(): ToolTemplateEntity {
    return ToolTemplateEntity(
        id = id,
        name = name,
        category = category.storageValue,
        brand = brand,
        material = material,
        sizeInputMode = sizeInputMode.name,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        rangeIncrement = rangeIncrement,
        customSizes = customSizes,
        quantityPerSize = quantityPerSize,
        storageLocation = storageLocation,
        notes = notes,
        createAsSet = createAsSet,
        setName = setName,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
