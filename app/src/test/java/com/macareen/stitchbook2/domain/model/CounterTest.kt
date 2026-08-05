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

    private fun counter(id: String, linkedCounterId: String? = null) = Counter(
        id = id,
        projectId = null,
        name = id,
        unitLabel = "rows",
        currentValue = 0,
        goal = null,
        createdAt = 0,
        updatedAt = 0,
        linkedCounterId = linkedCounterId,
        linkIncrementInterval = linkedCounterId?.let { 1 },
        linkIncrementAmount = linkedCounterId?.let { 1 }
    )
}
