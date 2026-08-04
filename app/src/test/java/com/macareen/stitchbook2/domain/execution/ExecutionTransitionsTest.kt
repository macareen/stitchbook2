package com.macareen.stitchbook2.domain.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ExecutionTransitionsTest {

    @Test
    fun completeMarksCurrentOccurrenceAndAdvances() {
        val context = context(orderedSectionGuide())
        val initial = context.engine.newExecution(executionId)

        val updated = changed(context.engine.complete(initial))

        assertEquals(setOf(context.address(0)), updated.completedAddresses)
        assertEquals(context.address(1), updated.currentAddress)
        assertEquals(ExecutionStatus.ACTIVE, updated.status)
    }

    @Test
    fun completeFollowsSectionChildOrder() {
        val context = context(orderedSectionGuide())
        val first = changed(
            context.engine.complete(context.engine.newExecution(executionId))
        )
        val second = changed(context.engine.complete(first))

        assertEquals(id("third"), second.currentAddress?.instructionNodeId)
    }

    @Test
    fun completeAdvancesRangeValues() {
        val context = context(ExecutionEngineFixtures.knitTenRoundsGuide())
        val updated = changed(
            context.engine.complete(context.engine.newExecution(executionId))
        )

        assertEquals(
            AncestryFrame.RangeValue(id("rounds"), 2),
            updated.currentAddress?.ancestryFrames?.single()
        )
    }

    @Test
    fun completeAdvancesRepeatInstructionsThenIterations() {
        val context = context(ExecutionEngineFixtures.laceRepeatGuide())
        val afterRowA = changed(
            context.engine.complete(context.engine.newExecution(executionId))
        )
        val afterRowB = changed(context.engine.complete(afterRowA))

        assertEquals(id("row-b"), afterRowA.currentAddress?.instructionNodeId)
        assertEquals(id("row-a"), afterRowB.currentAddress?.instructionNodeId)
        assertEquals(
            AncestryFrame.RepeatIteration(id("lace-repeat"), 2),
            afterRowB.currentAddress?.ancestryFrames?.single()
        )
    }

    @Test
    fun completeAdvancesRepeatContainingRangeAcrossBothFrames() {
        val context = context(ExecutionEngineFixtures.repeatContainingRangeGuide())
        val state = context.state(
            currentIndex = 3,
            completedIndices = setOf(0, 1, 2)
        )

        val updated = changed(context.engine.complete(state))

        assertEquals(
            listOf(
                AncestryFrame.RepeatIteration(id("band-repeat"), 2),
                AncestryFrame.RangeValue(id("band-rounds"), 1)
            ),
            updated.currentAddress?.ancestryFrames
        )
    }

    @Test
    fun completeSkipsAlreadyCompletedLaterOccurrences() {
        val context = context(orderedSectionGuide())
        val state = context.state(
            currentIndex = 0,
            completedIndices = setOf(1)
        )

        val updated = changed(context.engine.complete(state))

        assertEquals(context.address(2), updated.currentAddress)
        assertEquals(
            setOf(context.address(0), context.address(1)),
            updated.completedAddresses
        )
    }

    @Test
    fun completeAtFinalOccurrenceWrapsToEarliestIncomplete() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 3,
            completedIndices = setOf(0)
        )

        val updated = changed(context.engine.complete(state))

        assertEquals(context.address(1), updated.currentAddress)
        assertEquals(
            setOf(context.address(0), context.address(3)),
            updated.completedAddresses
        )
        assertEquals(ExecutionStatus.ACTIVE, updated.status)
    }

    @Test
    fun wraparoundPreservesNonContiguousCompletion() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 3,
            completedIndices = setOf(1)
        )

        val updated = changed(context.engine.complete(state))

        assertEquals(context.address(0), updated.currentAddress)
        assertEquals(
            setOf(context.address(1), context.address(3)),
            updated.completedAddresses
        )
        assertFalse(context.address(2) in updated.completedAddresses)
    }

    @Test
    fun terminalCompletionOccursOnlyWhenAllOccurrencesAreComplete() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 3,
            completedIndices = setOf(0, 1)
        )

        val updated = changed(context.engine.complete(state))

        assertEquals(context.address(2), updated.currentAddress)
        assertEquals(ExecutionStatus.ACTIVE, updated.status)
    }

    @Test
    fun completingFinalRemainingOccurrenceClearsPointerAndCompletes() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 2,
            completedIndices = setOf(0, 1, 3)
        )

        val updated = changed(context.engine.complete(state))

        assertNull(updated.currentAddress)
        assertEquals(ExecutionStatus.COMPLETED, updated.status)
        assertTrue(updated.isComplete)
        assertEquals(4, updated.completedAddresses.size)
    }

    @Test
    fun completingAlreadyCompletedCurrentAdvancesWithoutDuplicate() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 1,
            completedIndices = setOf(1)
        )

        val updated = changed(context.engine.complete(state))

        assertEquals(context.address(2), updated.currentAddress)
        assertEquals(setOf(context.address(1)), updated.completedAddresses)
    }

    @Test
    fun sameCompleteCommandProducesSameImmutableResult() {
        val context = context(orderedSectionGuide())
        val initial = context.engine.newExecution(executionId)

        val firstResult = context.engine.complete(initial)
        val secondResult = context.engine.complete(initial)

        assertEquals(firstResult, secondResult)
        assertTrue(initial.completedAddresses.isEmpty())
    }

    @Test
    fun completeOnCompletedExecutionIsValidNoOp() {
        val context = context(ExecutionEngineFixtures.singleInstructionGuide())
        val completed = changed(
            context.engine.complete(context.engine.newExecution(executionId))
        )

        val result = context.engine.complete(completed)

        assertNoChange(result, NoChangeReason.ALREADY_COMPLETE, completed)
    }

    @Test
    fun completeRejectsInvalidCurrentAddress() {
        val context = context(ExecutionEngineFixtures.knitTenRoundsGuide())
        val invalid = context.state(currentIndex = 0).copy(
            currentAddress = context.address(0).copy(ancestryFrames = emptyList())
        )

        assertThrows(InvalidExecutionStateException::class.java) {
            context.engine.complete(invalid)
        }
    }

    @Test
    fun completeRejectsRevisionMismatch() {
        val context = context(ExecutionEngineFixtures.singleInstructionGuide())
        val invalid = context.engine.newExecution(executionId).copy(
            definitionRevisionId = DefinitionRevisionId("stale")
        )

        assertThrows(InvalidExecutionStateException::class.java) {
            context.engine.complete(invalid)
        }
    }

    @Test
    fun completeRejectsCompletedStatusWithActivePointer() {
        val context = context(ExecutionEngineFixtures.singleInstructionGuide())
        val invalid = context.engine.newExecution(executionId).copy(
            status = ExecutionStatus.COMPLETED
        )

        val exception = assertThrows(InvalidExecutionStateException::class.java) {
            context.engine.complete(invalid)
        }

        assertTrue(
            exception.errors.any {
                it is ExecutionStateError.CompletedExecutionWithCurrentAddress
            }
        )
    }

    @Test
    fun previousMovesToImmediatePredecessorAndMarksItIncomplete() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 2,
            completedIndices = setOf(0, 1)
        )

        val updated = changed(context.engine.previous(state))

        assertEquals(context.address(1), updated.currentAddress)
        assertEquals(setOf(context.address(0)), updated.completedAddresses)
    }

    @Test
    fun previousFromFirstOccurrenceIsValidNoOp() {
        val context = context(orderedSectionGuide())
        val state = context.engine.newExecution(executionId)

        val result = context.engine.previous(state)

        assertNoChange(
            result,
            NoChangeReason.ALREADY_AT_FIRST_OCCURRENCE,
            state
        )
    }

    @Test
    fun previousFromCompletedExecutionReopensFinalOccurrence() {
        val context = context(orderedSectionGuide())
        val completed = context.state(
            currentIndex = null,
            completedIndices = setOf(0, 1, 2),
            status = ExecutionStatus.COMPLETED
        )

        val updated = changed(context.engine.previous(completed))

        assertEquals(context.address(2), updated.currentAddress)
        assertFalse(context.address(2) in updated.completedAddresses)
        assertEquals(ExecutionStatus.ACTIVE, updated.status)
    }

    @Test
    fun previousDoesNotClearUnrelatedLaterCompletion() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 2,
            completedIndices = setOf(0, 1, 3)
        )

        val updated = changed(context.engine.previous(state))

        assertEquals(setOf(context.address(0), context.address(3)), updated.completedAddresses)
    }

    @Test
    fun previousPreservesNonContiguousCompletion() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 3,
            completedIndices = setOf(0, 2)
        )

        val updated = changed(context.engine.previous(state))

        assertEquals(context.address(2), updated.currentAddress)
        assertEquals(setOf(context.address(0)), updated.completedAddresses)
        assertFalse(context.address(1) in updated.completedAddresses)
    }

    @Test
    fun previousRejectsInvalidExecutionState() {
        val context = context(orderedSectionGuide())
        val invalid = context.engine.newExecution(executionId).copy(
            status = ExecutionStatus.COMPLETED
        )

        assertThrows(InvalidExecutionStateException::class.java) {
            context.engine.previous(invalid)
        }
    }

    @Test
    fun previousCrossesNestedRangeAndRepeatBoundary() {
        val context = context(ExecutionEngineFixtures.repeatContainingRangeGuide())
        val state = context.state(
            currentIndex = 4,
            completedIndices = setOf(0, 1, 2, 3)
        )

        val updated = changed(context.engine.previous(state))

        assertEquals(context.address(3), updated.currentAddress)
        assertEquals(
            listOf(
                AncestryFrame.RepeatIteration(id("band-repeat"), 1),
                AncestryFrame.RangeValue(id("band-rounds"), 4)
            ),
            updated.currentAddress?.ancestryFrames
        )
    }

    @Test
    fun jumpToIncompleteOccurrenceDoesNotChangeCompletion() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 0,
            completedIndices = setOf(0)
        )

        val updated = changed(context.engine.jump(state, context.address(2)))

        assertEquals(context.address(2), updated.currentAddress)
        assertEquals(state.completedAddresses, updated.completedAddresses)
    }

    @Test
    fun jumpToCompletedOccurrenceDoesNotUncompleteIt() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 1,
            completedIndices = setOf(0, 3)
        )

        val updated = changed(context.engine.jump(state, context.address(3)))

        assertEquals(context.address(3), updated.currentAddress)
        assertEquals(state.completedAddresses, updated.completedAddresses)
    }

    @Test
    fun backwardJumpPreservesLaterCompletion() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 3,
            completedIndices = setOf(0, 2)
        )

        val updated = changed(context.engine.jump(state, context.address(1)))

        assertTrue(context.address(2) in updated.completedAddresses)
    }

    @Test
    fun forwardJumpDoesNotInferSkippedCompletion() {
        val context = context(fourInstructionGuide())
        val state = context.engine.newExecution(executionId)

        val updated = changed(context.engine.jump(state, context.address(3)))

        assertTrue(updated.completedAddresses.isEmpty())
    }

    @Test
    fun jumpFromCompletedExecutionReopensIt() {
        val context = context(orderedSectionGuide())
        val completed = context.state(
            currentIndex = null,
            completedIndices = setOf(0, 1, 2),
            status = ExecutionStatus.COMPLETED
        )

        val updated = changed(context.engine.jump(completed, context.address(1)))

        assertEquals(ExecutionStatus.ACTIVE, updated.status)
        assertEquals(context.address(1), updated.currentAddress)
        assertEquals(completed.completedAddresses, updated.completedAddresses)
    }

    @Test
    fun jumpRejectsRevisionMismatch() {
        val context = context(orderedSectionGuide())
        val target = context.address(1).copy(
            definitionRevisionId = DefinitionRevisionId("stale")
        )

        assertThrows(InvalidExecutionAddressException::class.java) {
            context.engine.jump(context.engine.newExecution(executionId), target)
        }
    }

    @Test
    fun jumpRejectsMalformedOrStaleAddress() {
        val context = context(ExecutionEngineFixtures.knitTenRoundsGuide())
        val malformed = context.address(5).copy(ancestryFrames = emptyList())

        assertThrows(InvalidExecutionAddressException::class.java) {
            context.engine.jump(context.engine.newExecution(executionId), malformed)
        }
    }

    @Test
    fun jumpCannotTargetContainer() {
        val context = context(ExecutionEngineFixtures.knitTenRoundsGuide())
        val containerAddress = ExecutionAddress(
            definitionRevisionId = ExecutionEngineFixtures.revisionId,
            instructionNodeId = id("rounds")
        )

        assertThrows(InvalidExecutionAddressException::class.java) {
            context.engine.jump(
                context.engine.newExecution(executionId),
                containerAddress
            )
        }
    }

    @Test
    fun jumpPreservesNestedStructuralAddress() {
        val context = context(ExecutionEngineFixtures.repeatContainingRangeGuide())
        val target = context.address(9)

        val updated = changed(
            context.engine.jump(context.engine.newExecution(executionId), target)
        )

        assertEquals(target, updated.currentAddress)
        assertEquals(
            listOf(
                AncestryFrame.RepeatIteration(id("band-repeat"), 3),
                AncestryFrame.RangeValue(id("band-rounds"), 2)
            ),
            updated.currentAddress?.ancestryFrames
        )
    }

    @Test
    fun jumpToCurrentAddressIsValidNoOp() {
        val context = context(orderedSectionGuide())
        val state = context.engine.newExecution(executionId)

        val result = context.engine.jump(state, checkNotNull(state.currentAddress))

        assertNoChange(result, NoChangeReason.ALREADY_AT_TARGET, state)
    }

    @Test
    fun activeTransitionResultsAlwaysHaveResolvableInstructionPointer() {
        val context = context(ExecutionEngineFixtures.repeatContainingRangeGuide())
        val initial = context.engine.newExecution(executionId)
        val jumped = changed(context.engine.jump(initial, context.address(7)))
        val completed = changed(context.engine.complete(jumped))
        val previous = changed(context.engine.previous(completed))
        val traversal = GuideTraversal(context.guide)

        listOf(jumped, completed, previous).forEach { state ->
            assertEquals(ExecutionStatus.ACTIVE, state.status)
            traversal.resolve(checkNotNull(state.currentAddress))
        }
    }

    @Test
    fun completedTransitionResultHasNoCurrentPointer() {
        val context = context(ExecutionEngineFixtures.singleInstructionGuide())

        val completed = changed(
            context.engine.complete(context.engine.newExecution(executionId))
        )

        assertEquals(ExecutionStatus.COMPLETED, completed.status)
        assertNull(completed.currentAddress)
    }

    @Test
    fun completedAddressesRemainValidForExactRevision() {
        val context = context(ExecutionEngineFixtures.knitTenRoundsGuide())
        val updated = changed(
            context.engine.complete(context.engine.newExecution(executionId))
        )
        val traversal = GuideTraversal(context.guide)

        updated.completedAddresses.forEach(traversal::resolve)
    }

    @Test
    fun transitionCompletionSetPreventsDuplicates() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 1,
            completedIndices = setOf(1)
        )

        val updated = changed(context.engine.complete(state))

        assertEquals(1, updated.completedAddresses.size)
    }

    @Test
    fun wraparoundNonContiguousCompletionDrivesDerivedProgress() {
        val context = context(fourInstructionGuide())
        val state = context.state(
            currentIndex = 3,
            completedIndices = setOf(1)
        )
        val updated = changed(context.engine.complete(state))

        val progress = DerivedProgressCalculator(context.guide).progressFor(
            containerNodeId = id("four-section"),
            completedAddresses = updated.completedAddresses,
            currentAddress = updated.currentAddress
        )

        assertEquals(ContainerProgressStatus.IN_PROGRESS, progress.status)
        assertEquals(2, progress.completedCount)
        assertEquals(4, progress.totalCount)
    }

    @Test
    fun forwardJumpWraparoundExampleCompletesOnlyAfterEveryOccurrence() {
        val context = context(fourInstructionGuide())
        var state = context.engine.newExecution(executionId)
        state = changed(context.engine.complete(state))
        state = changed(context.engine.jump(state, context.address(3)))

        state = changed(context.engine.complete(state))
        assertEquals(context.address(1), state.currentAddress)
        assertEquals(
            setOf(context.address(0), context.address(3)),
            state.completedAddresses
        )

        state = changed(context.engine.complete(state))
        assertEquals(context.address(2), state.currentAddress)
        assertEquals(ExecutionStatus.ACTIVE, state.status)

        state = changed(context.engine.complete(state))
        assertEquals(ExecutionStatus.COMPLETED, state.status)
        assertNull(state.currentAddress)
        assertEquals(4, state.completedAddresses.size)
    }

    @Test
    fun specificationKnitTenRoundsFixtureCompletesInOrder() {
        val context = context(ExecutionEngineFixtures.knitTenRoundsGuide())
        var state = context.engine.newExecution(executionId)

        repeat(10) {
            state = changed(context.engine.complete(state))
        }

        assertTrue(state.isComplete)
        assertEquals(10, state.completedAddresses.size)
    }

    @Test
    fun specificationLaceFixtureCompletesTwelveOccurrences() {
        val context = context(ExecutionEngineFixtures.laceRepeatGuide())
        var state = context.engine.newExecution(executionId)

        repeat(12) {
            state = changed(context.engine.complete(state))
        }

        assertTrue(state.isComplete)
        assertEquals(12, state.completedAddresses.size)
    }

    @Test
    fun specificationRepeatContainingRangeFixtureCompletesTwelveOccurrences() {
        val context = context(ExecutionEngineFixtures.repeatContainingRangeGuide())
        var state = context.engine.newExecution(executionId)

        repeat(12) {
            state = changed(context.engine.complete(state))
        }

        assertTrue(state.isComplete)
        assertEquals(12, state.completedAddresses.size)
    }

    private fun orderedSectionGuide(): ValidatedGuideDefinition {
        val section = Section(
            id = id("ordered-section"),
            title = "Ordered",
            children = listOf(id("first"), id("second"), id("third"))
        )
        return ExecutionEngineFixtures.validated(
            roots = listOf(section.id),
            section,
            Instruction(id("first"), "First"),
            Instruction(id("second"), "Second"),
            Instruction(id("third"), "Third")
        )
    }

    private fun fourInstructionGuide(): ValidatedGuideDefinition {
        val section = Section(
            id = id("four-section"),
            title = "Four instructions",
            children = (1..4).map { id("instruction-$it") }
        )
        return ExecutionEngineFixtures.validated(
            roots = listOf(section.id),
            section,
            *(1..4).map { index ->
                Instruction(id("instruction-$index"), "Instruction $index")
            }.toTypedArray()
        )
    }

    private fun context(guide: ValidatedGuideDefinition): TestContext {
        return TestContext(guide)
    }

    private fun changed(result: ExecutionTransitionResult): ExecutionState {
        return (result as ExecutionTransitionResult.Changed).state
    }

    private fun assertNoChange(
        result: ExecutionTransitionResult,
        expectedReason: NoChangeReason,
        expectedState: ExecutionState
    ) {
        val noChange = result as ExecutionTransitionResult.NoChange
        assertEquals(expectedReason, noChange.reason)
        assertSame(expectedState, noChange.state)
    }

    private fun id(value: String) = ExecutionEngineFixtures.nodeId(value)

    private class TestContext(
        val guide: ValidatedGuideDefinition
    ) {
        val engine = ExecutionEngine.forValidatedDefinition(guide)
        private val occurrences = GuideTraversal(guide).occurrences().toList()

        fun address(index: Int): ExecutionAddress = occurrences[index].address

        fun state(
            currentIndex: Int?,
            completedIndices: Set<Int> = emptySet(),
            status: ExecutionStatus = if (currentIndex == null) {
                ExecutionStatus.COMPLETED
            } else {
                ExecutionStatus.ACTIVE
            }
        ) = ExecutionState(
            executionId = executionId,
            guideId = guide.definition.guideId,
            definitionRevisionId = guide.definition.revisionId,
            currentAddress = currentIndex?.let(::address),
            completedAddresses = completedIndices.map(::address).toSet(),
            status = status
        )
    }

    companion object {
        private val executionId = ExecutionId("execution")
    }
}
