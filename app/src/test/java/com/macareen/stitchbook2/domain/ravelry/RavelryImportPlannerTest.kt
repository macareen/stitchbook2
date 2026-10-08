package com.macareen.stitchbook2.domain.ravelry

import com.macareen.stitchbook2.domain.model.ToolCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RavelryImportPlannerTest {

    private val entry = RavelryStashEntry(
        id = 1,
        name = null,
        yarnId = 555,
        yarnName = "Merino Worsted",
        companyName = "Acme Mills",
        weightName = "Worsted",
        colorway = "Lagoon",
        dyeLot = "A12",
        location = "Blue box",
        skeins = 3.5,
        yardsPerSkein = 218.0,
        gramsPerSkein = 100.0
    )
    private val needle = RavelryNeedle(9, "US 6 - 4.0 mm", "circular", "4.0 mm", "24 inch", null)

    @Test
    fun firstPullAddsEverythingWithStableIds() {
        val plan = RavelryImportPlanner.plan(listOf(entry), listOf(needle), emptyList(), emptyList(), now = 10)

        val yarn = plan.newStash.single()
        assertEquals("ravelry-stash-1", yarn.id)
        assertEquals("Merino Worsted", yarn.name)
        assertEquals("Acme Mills", yarn.brand)
        assertEquals(3.5, yarn.quantity, 0.0)
        assertEquals("skeins", yarn.unitLabel)
        assertEquals(218.0, yarn.yardagePerUnit!!, 0.0)
        assertEquals(100.0, yarn.weightPerUnitGrams!!, 0.0)
        assertEquals("555", yarn.ravelryYarnId)
        assertEquals("Blue box", yarn.storageLocation)
        val tool = plan.newTools.single()
        assertEquals("ravelry-needle-9", tool.id)
        assertEquals(ToolCategory.CIRCULAR_NEEDLES, tool.category)
        assertEquals(4.0, tool.sizeMetricMm!!, 0.0)
        assertEquals("Length: 24 inch", tool.notes)
    }

    @Test
    fun aSecondPullOfTheSameDataChangesNothing() {
        val first = RavelryImportPlanner.plan(listOf(entry), listOf(needle), emptyList(), emptyList(), now = 10)
        val again = RavelryImportPlanner.plan(listOf(entry), listOf(needle), first.newStash, first.newTools, now = 20)

        assertTrue(again.hasNothingToDo)
        assertEquals(1, again.unchangedStashCount)
        assertEquals(1, again.unchangedToolCount)
    }

    @Test
    fun changesKeepWhatOnlyThePersonRecorded() {
        val local = RavelryImportPlanner.plan(listOf(entry), emptyList(), emptyList(), emptyList(), now = 10).newStash.single()
            .copy(notes = "For the cardigan", purchasePrice = 12.0, remainingWeightGrams = 250.0)

        val plan = RavelryImportPlanner.plan(listOf(entry.copy(skeins = 2.0)), emptyList(), listOf(local), emptyList(), now = 20)

        val change = plan.changedStash.single()
        assertEquals(2.0, change.updated.quantity, 0.0)
        assertEquals("For the cardigan", change.updated.notes)
        assertEquals(12.0, change.updated.purchasePrice!!, 0.0)
        assertEquals(250.0, change.updated.remainingWeightGrams!!, 0.0)
        assertEquals(20L, change.updated.updatedAt)
        assertEquals(10L, change.updated.createdAt)
    }

    @Test
    fun aPersonsToolNotesAreNeverReplaced() {
        val local = RavelryImportPlanner.toolItem(needle, now = 10).copy(notes = "Lives in the travel kit")

        val plan = RavelryImportPlanner.plan(emptyList(), listOf(needle.copy(comment = "Bamboo")), emptyList(), listOf(local), now = 20)

        assertEquals(1, plan.unchangedToolCount)
    }

    @Test
    fun recordsWithoutDetailsStillGetANameAndQuantity() {
        val bare = RavelryStashEntry(2, null, null, null, null, null, null, null, null, null, null, null)

        val item = RavelryImportPlanner.stashItem(bare, now = 1)

        assertEquals("Ravelry stash 2", item.name)
        assertEquals(1.0, item.quantity, 0.0)
    }

    @Test
    fun needleTypesAndSizesAreReadConservatively() {
        assertEquals(ToolCategory.CROCHET_HOOK, RavelryImportPlanner.categoryFor("Crochet Hook"))
        assertEquals(ToolCategory.TUNISIAN_HOOK, RavelryImportPlanner.categoryFor("tunisian hook"))
        assertEquals(ToolCategory.DPN_SET, RavelryImportPlanner.categoryFor("double pointed"))
        assertNull(RavelryImportPlanner.categoryFor("mystery"))
        assertEquals(3.25, RavelryImportPlanner.millimetres("3,25mm")!!, 0.0)
        assertNull(RavelryImportPlanner.millimetres("US 6"))
        assertNull(RavelryImportPlanner.millimetres("H-8"))
    }
}
