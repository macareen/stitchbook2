package com.macareen.stitchbook2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedleGridTest {

    private fun tool(category: ToolCategory, sizeMm: Double?, quantity: Int = 1) = ToolItem(
        id = "$category-$sizeMm-$quantity",
        name = "tool",
        category = category,
        brand = null,
        material = null,
        sizeMetricMm = sizeMm,
        sizeLabel = null,
        lengthMm = null,
        statedCableLengthMm = null,
        cableLengthDefinition = null,
        approximateAssembledLengthMm = null,
        connectorFamily = null,
        compatibilityNotes = null,
        quantity = quantity,
        storageLocation = null,
        notes = null,
        setId = null,
        createdAt = 0,
        updatedAt = 0
    )

    @Test
    fun rowsSpanStandardSizesBetweenSmallestAndLargestSoGapsShow() {
        val grid = NeedleGrid.from(
            listOf(tool(ToolCategory.CIRCULAR_NEEDLES, 3.0), tool(ToolCategory.CIRCULAR_NEEDLES, 4.0))
        )

        assertEquals(listOf(3.0, 3.25, 3.5, 3.75, 4.0), grid.rows.map { it.sizeMm })
        assertEquals(0, grid.rows.first { it.sizeMm == 3.5 }.counts[NeedleColumn.CIRCULAR])
        assertEquals("6", grid.rows.last().usLabel)
    }

    @Test
    fun onlyKindsYouOwnBecomeColumnsAndHooksShareOne() {
        val grid = NeedleGrid.from(
            listOf(
                tool(ToolCategory.DPN_SET, 2.5),
                tool(ToolCategory.CROCHET_HOOK, 2.5),
                tool(ToolCategory.TUNISIAN_HOOK, 2.5, quantity = 2),
                tool(ToolCategory.STITCH_MARKER, null)
            )
        )

        assertEquals(listOf(NeedleColumn.DPN, NeedleColumn.HOOK), grid.columns)
        assertEquals(3, grid.rows.single().counts[NeedleColumn.HOOK])
    }

    @Test
    fun unsizedToolsAndNotionsLeaveTheGridEmpty() {
        assertTrue(NeedleGrid.from(listOf(tool(ToolCategory.CIRCULAR_NEEDLES, null))).isEmpty)
    }

    @Test
    fun nonStandardSizesKeepTheirOwnRow() {
        val grid = NeedleGrid.from(listOf(tool(ToolCategory.STRAIGHT_NEEDLES, 4.2)))

        assertEquals(listOf(4.25), grid.rows.map { it.sizeMm })
    }
}
