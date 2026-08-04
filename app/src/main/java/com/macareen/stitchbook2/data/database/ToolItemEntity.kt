package com.macareen.stitchbook2.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem

@Entity(
    tableName = "tool_items",
    foreignKeys = [
        ForeignKey(
            entity = ToolSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["set_id"],
            // A set is a label over existing inventory, not a second stock
            // count (PRODUCT_SPEC.md 6.8): deleting a set must not delete or
            // orphan-fail its components, so membership is nulled rather than
            // cascaded.
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("set_id")]
)
data class ToolItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val brand: String?,
    val material: String?,
    @ColumnInfo(name = "size_metric_mm") val sizeMetricMm: Double?,
    @ColumnInfo(name = "size_label") val sizeLabel: String?,
    @ColumnInfo(name = "length_mm") val lengthMm: Double?,
    @ColumnInfo(name = "stated_cable_length_mm") val statedCableLengthMm: Double?,
    @ColumnInfo(name = "cable_length_definition") val cableLengthDefinition: String?,
    @ColumnInfo(name = "approximate_assembled_length_mm")
    val approximateAssembledLengthMm: Double?,
    @ColumnInfo(name = "connector_family") val connectorFamily: String?,
    @ColumnInfo(name = "compatibility_notes") val compatibilityNotes: String?,
    val quantity: Int,
    @ColumnInfo(name = "storage_location") val storageLocation: String?,
    val notes: String?,
    @ColumnInfo(name = "set_id") val setId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun ToolItemEntity.toDomain(): ToolItem {
    return ToolItem(
        id = id,
        name = name,
        category = ToolCategory.fromStorageValue(category)
            ?: throw UnknownToolItemValueException("category", category),
        brand = brand,
        material = material,
        sizeMetricMm = sizeMetricMm,
        sizeLabel = sizeLabel,
        lengthMm = lengthMm,
        statedCableLengthMm = statedCableLengthMm,
        cableLengthDefinition = cableLengthDefinition,
        approximateAssembledLengthMm = approximateAssembledLengthMm,
        connectorFamily = connectorFamily,
        compatibilityNotes = compatibilityNotes,
        quantity = quantity,
        storageLocation = storageLocation,
        notes = notes,
        setId = setId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun ToolItem.toEntity(): ToolItemEntity {
    return ToolItemEntity(
        id = id,
        name = name,
        category = category.storageValue,
        brand = brand,
        material = material,
        sizeMetricMm = sizeMetricMm,
        sizeLabel = sizeLabel,
        lengthMm = lengthMm,
        statedCableLengthMm = statedCableLengthMm,
        cableLengthDefinition = cableLengthDefinition,
        approximateAssembledLengthMm = approximateAssembledLengthMm,
        connectorFamily = connectorFamily,
        compatibilityNotes = compatibilityNotes,
        quantity = quantity,
        storageLocation = storageLocation,
        notes = notes,
        setId = setId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

class UnknownToolItemValueException(
    field: String,
    value: String
) : IllegalStateException("Unknown stored tool item $field value: $value")
