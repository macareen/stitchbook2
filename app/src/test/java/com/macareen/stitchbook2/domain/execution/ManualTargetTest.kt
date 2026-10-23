package com.macareen.stitchbook2.domain.execution

import org.junit.Assert.*
import org.junit.Test

class ManualTargetTest {
    private val before = NodeId("before")
    private val loop = NodeId("cycle")
    private val after = NodeId("after")
    private val target = ManualTarget(TargetUnit.CENTIMETRES, 12.5)
    private val guide = ExecutionEngineFixtures.validated(listOf(before, loop, after),
        Instruction(before, "Prepare"), Instruction(loop, "Work one cycle (p.2)", target), Instruction(after, "Finish"))
    private val engine = ExecutionEngine.forValidatedDefinition(guide)
    private fun start() = engine.complete(engine.newExecution(ExecutionId("work"))).state
    private fun reached(state: ExecutionState = start()) = engine.confirmTarget(engine.observeTarget(state, 12.5).state).state

    @Test fun cyclesAreUnknownAndNeverCompleteFromCountingOrMeasuringAlone() {
        val first = start()
        val second = engine.complete(first).state
        assertEquals(first.currentAddress, second.currentAddress)
        assertFalse(second.currentAddress in second.completedAddresses)
        assertEquals(2, second.targetWork[second.currentAddress]!!.cycle)
        val measured = engine.observeTarget(second, 13.0).state
        assertEquals(second.currentAddress, measured.currentAddress)
        assertFalse(measured.targetWork[measured.currentAddress]!!.reached)
        assertEquals(after, engine.confirmTarget(measured).state.currentAddress!!.instructionNodeId)
    }

    @Test fun explicitConfirmationRequiresLatestObservationInCurrentCycle() {
        val below = engine.observeTarget(start(), 12.0).state
        assertThrows(IllegalArgumentException::class.java) { engine.confirmTarget(below) }
        val enough = engine.observeTarget(below, 13.0).state
        val corrected = engine.observeTarget(enough, 11.0).state
        assertThrows(IllegalArgumentException::class.java) { engine.confirmTarget(corrected) }
        val next = engine.complete(enough).state
        assertThrows(IllegalArgumentException::class.java) { engine.confirmTarget(next) }
        assertEquals(2, next.targetWork[next.currentAddress]!!.observations.size)
    }

    @Test fun previousWithinCycleAndAcrossStepBoundaryPreservesObservations() {
        val first = engine.observeTarget(start(), 9.0).state
        val second = engine.observeTarget(engine.complete(first).state, 11.0).state
        val back = engine.previous(second).state
        assertEquals(1, back.targetWork[back.currentAddress]!!.cycle)
        assertEquals(second.targetWork[second.currentAddress]!!.observations, back.targetWork[back.currentAddress]!!.observations)
        assertEquals(before, engine.previous(back).state.currentAddress!!.instructionNodeId)
        val done = reached(first)
        val reopened = engine.previous(done).state
        assertEquals(loop, reopened.currentAddress!!.instructionNodeId)
        assertFalse(reopened.targetWork[reopened.currentAddress]!!.reached)
        assertFalse(reopened.currentAddress in reopened.completedAddresses)
    }

    @Test fun jumpDoesNotConfirmAndReopeningRequiresConfirmationAgain() {
        val current = start()
        val skipped = engine.jump(current, GuideTraversal(guide).last().address).state
        val wraps = engine.complete(skipped).state
        assertEquals(current.currentAddress, wraps.currentAddress)
        val done = reached(current)
        val reopened = engine.jump(done, current.currentAddress!!).state
        assertFalse(reopened.currentAddress in reopened.completedAddresses)
        assertFalse(reopened.targetWork[reopened.currentAddress]!!.reached)
        assertEquals(done.targetWork[current.currentAddress]!!.observations, reopened.targetWork[current.currentAddress]!!.observations)
        assertTrue(engine.complete(engine.confirmTarget(reopened).state).state.isComplete)
    }

    @Test fun repeatedOccurrencesAndFreshExecutionsKeepIndependentTargets() {
        val wrapper = NodeId("repeat")
        val repeatedGuide = ExecutionEngineFixtures.validated(listOf(wrapper), Repeat(wrapper, 2, children = listOf(loop)), Instruction(loop, "Work cycle", target))
        val repeated = ExecutionEngine.forValidatedDefinition(repeatedGuide)
        val initial = repeated.newExecution(ExecutionId("left"))
        val first = repeated.observeTarget(initial, 13.0).state
        val second = repeated.confirmTarget(first).state
        assertNotEquals(first.currentAddress, second.currentAddress)
        assertNull(second.targetWork[second.currentAddress])
        assertTrue(repeated.newExecution(ExecutionId("right")).targetWork.isEmpty())
        assertEquals(first.targetWork, TargetWorkCodec.decode(TargetWorkCodec.encode(first.targetWork), first.definitionRevisionId))
    }

    @Test fun explicitTargetInstructionCannotBeMovedAwayByReferenceCleanup() {
        val draft = com.macareen.stitchbook2.domain.guide.GuideDraft(
            com.macareen.stitchbook2.domain.guide.DraftId("draft"), guide.definition.guideId, null, 1, 1, 0, listOf(loop),
            listOf(com.macareen.stitchbook2.domain.guide.DraftNode(loop,
                com.macareen.stitchbook2.domain.guide.DraftNodeType.INSTRUCTION, instructionText = "Gauge: work a swatch", target = target)))
        val preview = com.macareen.stitchbook2.domain.guide.GuideReferenceCleanup.preview(draft)
        assertEquals(draft, preview.draft)
        assertTrue(preview.movedTexts.isEmpty())
    }

    @Test fun decreasingStitchTargetsUseAnExplicitAtMostComparison() {
        val target = ManualTarget(TargetUnit.STITCHES, 20.0, TargetComparison.AT_MOST)
        val guide = ExecutionEngineFixtures.validated(listOf(loop), Instruction(loop, "Work one decrease cycle", target))
        val engine = ExecutionEngine.forValidatedDefinition(guide)
        val first = engine.newExecution(ExecutionId("decreases"))
        val above = engine.observeTarget(first, 30.0).state
        assertThrows(IllegalArgumentException::class.java) { engine.confirmTarget(above) }
        val atTarget = engine.observeTarget(above, 20.0).state
        assertFalse(atTarget.isComplete)
        assertTrue(engine.confirmTarget(atTarget).state.isComplete)
        assertEquals(target, ManualTargets.decode(ManualTargets.encode(target)))
    }

    @Test fun typedValuesRejectFractionsForStitchesAndNonFiniteNumbers() {
        assertThrows(IllegalArgumentException::class.java) { ManualTarget(TargetUnit.STITCHES, 3.5) }
        assertThrows(IllegalArgumentException::class.java) { ManualTarget(TargetUnit.INCHES, Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { ManualTarget(TargetUnit.CENTIMETRES, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { engine.observeTarget(start(), -1.0) }
        assertThrows(IllegalArgumentException::class.java) { engine.observeTarget(start(), Double.POSITIVE_INFINITY) }
        val target = ManualTarget(TargetUnit.STITCHES, 50.0)
        assertFalse(target.accepts(0.5))
        assertTrue(target.accepts(0.0))
        assertEquals(target, ManualTargets.decode(ManualTargets.encode(target)))
    }

    @Test fun invalidPersistedWorkAndDuplicateAddressesAreRefused() {
        val state = start()
        val address = state.currentAddress!!
        assertThrows(InvalidExecutionStateException::class.java) {
            engine.complete(state.copy(targetWork = mapOf(address to TargetWork(reached = true))))
        }
        assertThrows(InvalidExecutionStateException::class.java) {
            engine.complete(state.copy(targetWork = mapOf(address to TargetWork(cycle = 0))))
        }
        val observed = engine.observeTarget(state, 1.0).state
        val entry = TargetWorkCodec.encode(observed.targetWork).drop(1).dropLast(1)
        assertThrows(IllegalArgumentException::class.java) { TargetWorkCodec.decode("[$entry,$entry]", state.definitionRevisionId) }
    }

    @Test fun cyclesCannotOverflowAndGuideSummaryUsesStepsRatherThanFabricatedStitchWork() {
        val state = start()
        val atLimit = state.copy(targetWork = mapOf(state.currentAddress!! to TargetWork(cycle = Int.MAX_VALUE)))
        assertThrows(ArithmeticException::class.java) { engine.complete(atLimit) }
        assertEquals(ProgressBasis.STEPS, GuideProgressSummary.of(guide, state.completedAddresses).basis)
        assertEquals(3, GuideProgressSummary.of(guide, state.completedAddresses).totalSteps)
    }

    @Test fun revisedTargetsNeverInheritCompletionAndSavedSourceRemainsIntact() {
        val source = reached()
        val definition = guide.definition
        val revised = GuideDefinition(definition.guideId, DefinitionRevisionId("new"), definition.rootNodeIds, definition.nodes)
        val comparison = RevisionProgressComparison.preview(definition, revised, source.completedAddresses)
        assertFalse(comparison.candidates.any { it.target.instructionNodeId == loop })
        val persisted = PersistedExecution(source, 2, 1, 2, null)
        val review = ReviewedRevisionChange(null, persisted, definition, 1, revised, 2,
            GuideTraversal(GuideDefinitionValidator.validate(revised)).last().address, true)
        val seed = revisionChangeSeed(review, emptySet(), ExecutionId("new-work"))
        assertTrue(seed.targetWork.isEmpty())
        assertFalse(seed.completedAddresses.any { it.instructionNodeId == loop })
        assertTrue(source.targetWork.values.single().reached)
    }
}
