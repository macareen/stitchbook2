package com.macareen.stitchbook2.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterTest {

    @Test
    fun aBrandNewCounterNeverCreatesACycle() {
        assertFalse(wouldCreateCycle(counters = emptyList(), editingCounterId = null, proposedTargetId = "anything"))
    }

    @Test
    fun noProposedTargetNeverCreatesACycle() {
        assertFalse(wouldCreateCycle(counters = emptyList(), editingCounterId = "a", proposedTargetId = null))
    }

    @Test
    fun aCounterLinkingToItselfIsACycle() {
        assertTrue(wouldCreateCycle(counters = listOf(counter("a")), editingCounterId = "a", proposedTargetId = "a"))
    }

    @Test
    fun aDirectTwoCounterCycleIsRejected() {
        // b already links to a; pointing a at b would complete a 2-cycle.
        val counters = listOf(counter("a"), counter("b", linkedCounterId = "a"))

        assertTrue(wouldCreateCycle(counters, editingCounterId = "a", proposedTargetId = "b"))
    }

    @Test
    fun anIndirectThreeCounterCycleIsRejected() {
        // c already links to b, which already links to a; pointing a at c
        // would complete a -> c -> b -> a.
        val counters = listOf(
            counter("a"),
            counter("b", linkedCounterId = "a"),
            counter("c", linkedCounterId = "b")
        )

        assertTrue(wouldCreateCycle(counters, editingCounterId = "a", proposedTargetId = "c"))
    }

    @Test
    fun linkingIntoAnUnrelatedChainIsNotACycle() {
        // c already links to b, which links to a. Linking a brand-new
        // counter d at c is a valid, acyclic chain: d -> c -> b -> a.
        val counters = listOf(
            counter("a"),
            counter("b", linkedCounterId = "a"),
            counter("c", linkedCounterId = "b"),
            counter("d")
        )

        assertFalse(wouldCreateCycle(counters, editingCounterId = "d", proposedTargetId = "c"))
    }

    @Test
    fun retargetingAnExistingLinkAwayFromACycleIsNotACycle() {
        // a currently links to b; retargeting a at c (unrelated) is fine.
        val counters = listOf(counter("a", linkedCounterId = "b"), counter("b"), counter("c"))

        assertFalse(wouldCreateCycle(counters, editingCounterId = "a", proposedTargetId = "c"))
    }

    private companion object {
        const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000
    }

    @Test
    fun noScheduleIsNeverDue() {
        val counter = counter("a", repeatIntervalDays = null, createdAt = 0)

        assertFalse(dueForRepeatingReset(counter, now = ONE_DAY_MILLIS * 1000))
    }

    @Test
    fun aNonPositiveIntervalIsNeverDue() {
        val counter = counter("a", repeatIntervalDays = 0, createdAt = 0)

        assertFalse(dueForRepeatingReset(counter, now = ONE_DAY_MILLIS * 1000))
    }

    @Test
    fun notYetDueBeforeTheIntervalHasElapsed() {
        val counter = counter("a", repeatIntervalDays = 7, createdAt = 0)

        assertFalse(dueForRepeatingReset(counter, now = ONE_DAY_MILLIS * 6))
    }

    @Test
    fun dueOnceTheIntervalHasFullyElapsed() {
        val counter = counter("a", repeatIntervalDays = 7, createdAt = 0)

        assertTrue(dueForRepeatingReset(counter, now = ONE_DAY_MILLIS * 7))
    }

    @Test
    fun baselineIsCreatedAtWhenTheScheduleHasNeverFiredYet() {
        val counter = counter("a", repeatIntervalDays = 7, createdAt = ONE_DAY_MILLIS * 10, lastRepeatResetAt = null)

        // 7 days after createdAt (day 10), not yet 7 days after "now"=0.
        assertFalse(dueForRepeatingReset(counter, now = ONE_DAY_MILLIS * 16))
        assertTrue(dueForRepeatingReset(counter, now = ONE_DAY_MILLIS * 17))
    }

    @Test
    fun baselineIsLastRepeatResetAtOnceTheScheduleHasFiredBefore() {
        // createdAt is far in the past, but the schedule already fired at
        // day 20 -- the next reset should be measured from there, not from
        // createdAt (which would make it look overdue immediately).
        val counter = counter(
            "a",
            repeatIntervalDays = 7,
            createdAt = 0,
            lastRepeatResetAt = ONE_DAY_MILLIS * 20
        )

        assertFalse(dueForRepeatingReset(counter, now = ONE_DAY_MILLIS * 26))
        assertTrue(dueForRepeatingReset(counter, now = ONE_DAY_MILLIS * 27))
    }

    private fun counter(
        id: String,
        linkedCounterId: String? = null,
        repeatIntervalDays: Int? = null,
        createdAt: Long = 0,
        lastRepeatResetAt: Long? = null
    ) = Counter(
        id = id,
        projectId = null,
        name = id,
        unitLabel = "rows",
        currentValue = 0,
        goal = null,
        createdAt = createdAt,
        updatedAt = 0,
        linkedCounterId = linkedCounterId,
        linkIncrementInterval = linkedCounterId?.let { 1 },
        linkIncrementAmount = linkedCounterId?.let { 1 },
        repeatIntervalDays = repeatIntervalDays,
        lastRepeatResetAt = lastRepeatResetAt
    )
}
