package com.macareen.stitchbook2.domain.execution

import org.junit.Assert.*
import org.junit.Test

class ReviewedRevisionChangeTest {
    private val a = NodeId("a")
    private val b = NodeId("b")
    private val c = NodeId("c")
    private val source = GuideDefinition(GuideId("g"), DefinitionRevisionId("r1"), listOf(a, b),
        listOf(Instruction(a, "Knit."), Instruction(b, "Turn.")))
    private val target = GuideDefinition(GuideId("g"), DefinitionRevisionId("r2"), listOf(a, c, b),
        listOf(Instruction(a, "Knit."), Instruction(c, "Slip."), Instruction(b, "Turn.")))
    private fun address(definition: GuideDefinition, id: NodeId) = ExecutionAddress(definition.revisionId, id)
    private fun review(completed: Set<ExecutionAddress> = setOf(address(source, a)), next: GuideDefinition = target): ReviewedRevisionChange {
        val state = ExecutionState(ExecutionId("old"), source.guideId, source.revisionId,
            address(source, b), completed, ExecutionStatus.ACTIVE)
        return ReviewedRevisionChange("p", PersistedExecution(state, 1, 1, 2, null), source, 1, next, 2)
    }
    @Test fun onlySelectedMatchingWorkMovesAndNewlyInsertedWorkIsFirstIncomplete() {
        val value = review()
        val seed = revisionChangeSeed(value, setOf(address(target, a)), ExecutionId("new"))
        assertEquals(setOf(address(target, a)), seed.completedAddresses)
        assertEquals(address(target, c), seed.currentAddress)
        assertEquals(setOf(address(source, a)), value.source.state.completedAddresses)
    }
    @Test fun clearAllStartsFreshWithoutChangingTheSource() {
        val value = review()
        val seed = revisionChangeSeed(value, emptySet(), ExecutionId("new"))
        assertTrue(seed.completedAddresses.isEmpty())
        assertEquals(address(target, a), seed.currentAddress)
        assertEquals(1, value.source.state.completedAddresses.size)
    }
    @Test fun sourceRevisionAddressesAndChangedTextCannotBeForcedIntoCarryover() {
        assertThrows(IllegalArgumentException::class.java) { revisionChangeSeed(review(), setOf(address(source, a)), ExecutionId("new")) }
        val edited = GuideDefinition(target.guideId, target.revisionId, target.rootNodeIds,
            target.nodes.map { if (it.id == a) Instruction(a, "Purl.") else it })
        assertThrows(IllegalArgumentException::class.java) { revisionChangeSeed(review(next = edited), setOf(address(edited, a)), ExecutionId("new")) }
    }
    @Test fun fullyCarriedWorkProducesACompletedTargetWithNoPointer() {
        val same = GuideDefinition(source.guideId, DefinitionRevisionId("r2"), source.rootNodeIds, source.nodes)
        val value = review(setOf(address(source, a), address(source, b)), same)
        val seed = revisionChangeSeed(value, setOf(address(same, a), address(same, b)), ExecutionId("new"))
        assertEquals(ExecutionStatus.COMPLETED, seed.status)
        assertNull(seed.currentAddress)
    }
    @Test fun explicitPositionKeepsReviewedMatchesButDoesNotInventEarlierCompletion() {
        val value = review().copy(resumeAt = address(target, b))
        val seed = revisionChangeSeed(value, setOf(address(target, a)), ExecutionId("new"))
        assertEquals(address(target, b), seed.currentAddress)
        assertEquals(setOf(address(target, a)), seed.completedAddresses)
        assertEquals(address(source, b), value.source.state.currentAddress)
    }
    @Test fun confirmedEarlierStepsIncludeChangedOrInsertedWorkButCurrentStepStaysIncomplete() {
        val value = review().copy(resumeAt = address(target, b), confirmEarlierStepsDone = true)
        val seed = revisionChangeSeed(value, emptySet(), ExecutionId("new"))
        assertEquals(setOf(address(target, a), address(target, c)), seed.completedAddresses)
        assertEquals(address(target, b), seed.currentAddress)
        assertEquals(1, value.source.state.completedAddresses.size)
    }
    @Test fun foreignOrMissingPositionAndUnpositionedCompletionConfirmationAreRejected() {
        listOf(review().copy(resumeAt = address(source, b)), review().copy(resumeAt = address(target, NodeId("missing"))),
            review().copy(confirmEarlierStepsDone = true)).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { revisionChangeSeed(value, emptySet(), ExecutionId("new")) }
        }
    }
    @Test fun selectingPreviouslyCompletedStepMakesItTheNextStepWithoutDroppingOtherWork() {
        val value = review().copy(resumeAt = address(target, a))
        val seed = revisionChangeSeed(value, setOf(address(target, a)), ExecutionId("new"))
        assertTrue(seed.completedAddresses.isEmpty())
        assertEquals(address(target, a), seed.currentAddress)
    }
    @Test fun contentComparisonIgnoresFlatStorageOrderButKeepsRootOrderAndText() {
        assertTrue(source.hasSameContentAs(GuideDefinition(source.guideId, source.revisionId, source.rootNodeIds, source.nodes.reversed())))
        assertFalse(source.hasSameContentAs(GuideDefinition(source.guideId, source.revisionId, source.rootNodeIds.reversed(), source.nodes)))
        assertFalse(source.hasSameContentAs(GuideDefinition(source.guideId, source.revisionId, source.rootNodeIds, listOf(Instruction(a, "Purl."), Instruction(b, "Turn.")))))
    }
}
