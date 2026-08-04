package com.macareen.stitchbook2.data.database

import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ToolEntityMappingTest {

    @Test
    fun toolItemRoundTripsThroughEntity() {
        val item = ToolItem(
            id = "tool-item-id",
            name = "US 7 interchangeable tip",
            category = ToolCategory.INTERCHANGEABLE_TIP,
            brand = "ChiaoGoo",
            material = "Stainless steel",
            sizeMetricMm = 4.5,
            sizeLabel = "US 7",
            lengthMm = 127.0,
            statedCableLengthMm = null,
            cableLengthDefinition = null,
            approximateAssembledLengthMm = null,
            connectorFamily = "ChiaoGoo Twist",
            compatibilityNotes = "Compatible with Twist Red Lace cables only.",
            quantity = 2,
            storageLocation = "Tip case, slot 7",
            notes = "Slightly bent, still usable.",
            setId = "tool-set-id",
            createdAt = 100,
            updatedAt = 200
        )

        assertEquals(item, item.toEntity().toDomain())
    }

    @Test
    fun toolItemWithoutOptionalFieldsRoundTrips() {
        val item = ToolItem(
            id = "tool-item-id",
            name = "5mm crochet hook",
            category = ToolCategory.CROCHET_HOOK,
            brand = null,
            material = null,
            sizeMetricMm = null,
            sizeLabel = null,
            lengthMm = null,
            statedCableLengthMm = null,
            cableLengthDefinition = null,
            approximateAssembledLengthMm = null,
            connectorFamily = null,
            compatibilityNotes = null,
            quantity = 1,
            storageLocation = null,
            notes = null,
            setId = null,
            createdAt = 100,
            updatedAt = 200
        )

        assertEquals(item, item.toEntity().toDomain())
    }

    @Test
    fun toolItemEntityUsesExplicitStableCategoryValue() {
        val entity = ToolItem(
            id = "tool-item-id",
            name = "24 inch cable",
            category = ToolCategory.INTERCHANGEABLE_CABLE,
            brand = null,
            material = null,
            sizeMetricMm = null,
            sizeLabel = null,
            lengthMm = null,
            statedCableLengthMm = 610.0,
            cableLengthDefinition = "Tip-to-tip including 5cm tips",
            approximateAssembledLengthMm = 620.0,
            connectorFamily = "ChiaoGoo Twist",
            compatibilityNotes = null,
            quantity = 1,
            storageLocation = null,
            notes = null,
            setId = null,
            createdAt = 100,
            updatedAt = 200
        ).toEntity()

        assertEquals("INTERCHANGEABLE_CABLE", entity.category)
    }

    @Test
    fun unknownStoredToolCategoryFailsWithDataMappingError() {
        val entity = ToolItemEntity(
            id = "tool-item-id",
            name = "Legacy tool",
            category = "UNRECOGNIZED_CATEGORY",
            brand = null,
            material = null,
            sizeMetricMm = null,
            sizeLabel = null,
            lengthMm = null,
            statedCableLengthMm = null,
            cableLengthDefinition = null,
            approximateAssembledLengthMm = null,
            connectorFamily = null,
            compatibilityNotes = null,
            quantity = 1,
            storageLocation = null,
            notes = null,
            setId = null,
            createdAt = 100,
            updatedAt = 200
        )

        assertThrows(UnknownToolItemValueException::class.java) {
            entity.toDomain()
        }
    }

    @Test
    fun toolSetRoundTripsThroughEntity() {
        val set = ToolSet(
            id = "tool-set-id",
            name = "ChiaoGoo TWIST Red Lace 5-inch Set",
            brand = "ChiaoGoo",
            notes = "Birthday gift, complete set.",
            createdAt = 100,
            updatedAt = 200
        )

        assertEquals(set, set.toEntity().toDomain())
    }
}
