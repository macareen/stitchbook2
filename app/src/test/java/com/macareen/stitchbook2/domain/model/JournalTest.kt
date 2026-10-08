package com.macareen.stitchbook2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalTest {

    private fun milestone(id: String, position: Int, createdAt: Long = 0) =
        Milestone(id, "project", id, null, null, position, createdAt, createdAt)

    private fun photo(id: String, role: PhotoRole, updatedAt: Long) =
        Photo(id, "project", null, "content://$id", null, null, null, null, role, 0, updatedAt)

    @Test
    fun movingAMilestoneRenumbersContiguouslyAndReturnsOnlyChanges() {
        val milestones = listOf(milestone("a", 0), milestone("b", 1), milestone("c", 2))
        val changed = reorderMilestones(milestones, "c", -1, now = 5)
        assertEquals(mapOf("c" to 1, "b" to 2), changed.associate { it.id to it.position })
        assertTrue(changed.all { it.updatedAt == 5L })
    }

    @Test
    fun movingPastEitherEndIsANoOp() {
        val milestones = listOf(milestone("a", 0), milestone("b", 1))
        assertTrue(reorderMilestones(milestones, "a", -1, 0).isEmpty())
        assertTrue(reorderMilestones(milestones, "b", 1, 0).isEmpty())
        assertTrue(reorderMilestones(milestones, "missing", 1, 0).isEmpty())
    }

    @Test
    fun duplicatePositionsFromOlderDataAreRepairedOnMove() {
        val milestones = listOf(milestone("a", 0, 1), milestone("b", 0, 2), milestone("c", 0, 3))
        val changed = reorderMilestones(milestones, "a", 1, 0).associate { it.id to it.position }
        // "b" already sits at 0, so only "a" and "c" change; the result is contiguous 0..2.
        assertEquals(mapOf("a" to 1, "c" to 2), changed)
        val repaired = milestones.associate { it.id to it.position } + changed
        assertEquals(mapOf("b" to 0, "a" to 1, "c" to 2), repaired)
    }

    @Test
    fun beforeAfterNeedsBothRolesAndPrefersTheLatest() {
        assertNull(listOf(photo("x", PhotoRole.BEFORE, 1)).beforeAfterPair())
        val pair = listOf(
            photo("old", PhotoRole.BEFORE, 1),
            photo("new", PhotoRole.BEFORE, 2),
            photo("after", PhotoRole.AFTER, 1)
        ).beforeAfterPair()
        assertEquals("new" to "after", pair?.let { it.first.id to it.second.id })
    }

    @Test
    fun entriesSortNewestDateFirstThenNewestCreated() {
        val entries = listOf(
            JournalEntry("1", "p", "2026-01-01", null, "a", createdAt = 1, updatedAt = 1),
            JournalEntry("2", "p", "2026-02-01", null, "b", createdAt = 0, updatedAt = 0),
            JournalEntry("3", "p", "2026-01-01", null, "c", createdAt = 2, updatedAt = 2)
        )
        assertEquals(listOf("2", "3", "1"), entries.sortedForDisplay().map { it.id })
    }
}
