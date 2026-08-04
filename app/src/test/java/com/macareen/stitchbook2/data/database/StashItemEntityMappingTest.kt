package com.macareen.stitchbook2.data.database

import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StashItemEntityMappingTest {

    @Test
    fun stashItemRoundTripsThroughEntity() {
        val item = StashItem(
            id = "stash-id",
            name = "Cascade 220",
            category = StashCategory.YARN,
            brand = "Cascade Yarns",
            colorway = "Ivory",
            dyeLot = "12345",
            weightCategory = "Worsted",
            fiberContent = "100% Peruvian Highland Wool",
            quantity = 6.0,
            unitLabel = "skeins",
            yardagePerUnit = 220.0,
            notes = "Reserved for the cardigan body.",
            createdAt = 100,
            updatedAt = 200
        )

        assertEquals(item, item.toEntity().toDomain())
    }

    @Test
    fun nonYarnStashItemWithoutYarnFieldsRoundTrips() {
        val item = StashItem(
            id = "stash-id",
            name = "5mm crochet hook",
            category = StashCategory.NEEDLES_HOOKS,
            brand = null,
            colorway = null,
            dyeLot = null,
            weightCategory = null,
            fiberContent = null,
            quantity = 1.0,
            unitLabel = "hook",
            yardagePerUnit = null,
            notes = null,
            createdAt = 100,
            updatedAt = 200
        )

        assertEquals(item, item.toEntity().toDomain())
    }

    @Test
    fun entityUsesExplicitStableValues() {
        val entity = StashItem(
            id = "stash-id",
            name = "Stitch markers",
            category = StashCategory.NOTIONS,
            brand = null,
            colorway = null,
            dyeLot = null,
            weightCategory = null,
            fiberContent = null,
            quantity = 20.0,
            unitLabel = "markers",
            yardagePerUnit = null,
            notes = null,
            createdAt = 100,
            updatedAt = 200
        ).toEntity()

        assertEquals("NOTIONS", entity.category)
    }

    @Test
    fun unknownStoredValueFailsWithDataMappingError() {
        val entity = StashItemEntity(
            id = "stash-id",
            name = "Legacy item",
            category = "UNRECOGNIZED_CATEGORY",
            brand = null,
            colorway = null,
            dyeLot = null,
            weightCategory = null,
            fiberContent = null,
            quantity = 1.0,
            unitLabel = "unit",
            yardagePerUnit = null,
            notes = null,
            createdAt = 100,
            updatedAt = 200
        )

        assertThrows(UnknownStashItemValueException::class.java) {
            entity.toDomain()
        }
    }
}
