package com.macareen.stitchbook2.domain.execution

import org.junit.Assert.*
import org.junit.Test

class TargetBodyTest {
    private val a = NodeId("a")
    private val b = NodeId("b")
    private val s = NodeId("section")
    private val end = NodeId("end")
    private val target = ManualTarget(TargetUnit.CENTIMETRES, 10.0)
    private val definition = ExecutionEngineFixtures.definition(listOf(s, end),
        Section(s, "Body", listOf(a, b), target), Instruction(a, "Knit. (p.2 l.1)"),
        Instruction(b, "Purl. (p.2 l.2)"), Instruction(end, "Finish"))
    private val guide = GuideDefinitionValidator.validate(definition)
    private val traversal = GuideTraversal(guide)
    private val engine = ExecutionEngine.forValidatedDefinition(guide)
    private fun start() = engine.newExecution(ExecutionId("work"))

    @Test fun wholeBodyRepeatsAndOnlyFinalStepCanConfirm() {
        var state = engine.observeTarget(start(), 10.0).state
        val key = state.currentAddress!!
        assertThrows(IllegalArgumentException::class.java) { engine.confirmTarget(state) }
        state = engine.complete(state).state
        assertEquals(b, state.currentAddress!!.instructionNodeId)
        assertEquals(1, state.targetWork.getValue(key).cycle)
        state = engine.complete(state).state
        assertEquals(a, state.currentAddress!!.instructionNodeId)
        assertEquals(2, state.targetWork.getValue(key).cycle)
        assertTrue(state.completedAddresses.isEmpty())
        state = engine.complete(state).state
        assertThrows(IllegalArgumentException::class.java) { engine.confirmTarget(state) }
        state = engine.observeTarget(state, 10.0).state
        state = engine.confirmTarget(state).state
        assertEquals(end, state.currentAddress!!.instructionNodeId)
        assertTrue(state.targetWork.getValue(key).reached)
        assertEquals(listOf(a, b), state.completedAddresses.map { it.instructionNodeId })
        assertEquals("Knit. (p.2 l.1)", traversal.first().instruction.text)
        assertEquals(definition, guide.definition)
    }

    @Test fun previousJumpAndReloadKeepCycleEvidenceAndRequireTheBody() {
        var state = engine.complete(engine.complete(start()).state).state
        val key = state.currentAddress!!
        state = engine.observeTarget(state, 11.0).state
        state = engine.previous(state).state
        assertEquals(b, state.currentAddress!!.instructionNodeId)
        assertEquals(1, state.targetWork.getValue(key).cycle)
        assertEquals(listOf(TargetObservation(2, 11.0)), state.targetWork.getValue(key).observations)
        state = state.copy(targetWork = TargetWorkCodec.decode(TargetWorkCodec.encode(state.targetWork), state.definitionRevisionId))
        assertThrows(IllegalArgumentException::class.java) { engine.confirmTarget(state) }
        state = engine.complete(state).state
        state = engine.jump(state, traversal.occurrences().last().address).state
        state = engine.jump(state, traversal.occurrences().toList()[1].address).state
        assertThrows(IllegalArgumentException::class.java) { engine.confirmTarget(state) }
        state = engine.jump(state, key).state
        state = engine.complete(state).state
        state = engine.confirmTarget(state).state
        state = engine.previous(state).state
        assertFalse(state.targetWork.getValue(key).reached)
        assertEquals(2, state.targetWork.getValue(key).cycle)
        assertEquals(b, state.currentAddress!!.instructionNodeId)
        assertEquals(start().targetWork, engine.newExecution(ExecutionId("other-piece")).targetWork)
    }

    @Test fun finiteContainersAreTraversedFullyAndOuterOccurrencesStayIndependent() {
        val repeat = NodeId("repeat")
        val range = NodeId("range")
        val definition = ExecutionEngineFixtures.definition(listOf(repeat), Repeat(repeat, 2, children = listOf(s)),
            Section(s, "Body", listOf(range, b), target), Range(range, "row", 1, 2, listOf(a)),
            Instruction(a, "Knit"), Instruction(b, "Purl"))
        val engine = ExecutionEngine.forDefinition(definition)
        var state = engine.newExecution(ExecutionId("work"))
        val key = state.currentAddress!!
        state = engine.complete(engine.complete(state).state).state
        assertEquals(b, state.currentAddress!!.instructionNodeId)
        state = engine.confirmTarget(engine.observeTarget(state, 10.0).state).state
        assertEquals(2, (state.currentAddress!!.ancestryFrames.first() as AncestryFrame.RepeatIteration).iteration)
        assertEquals(1, state.targetWork.size)
        state = engine.complete(engine.complete(engine.complete(state).state).state).state
        assertEquals(2, state.targetWork.size)
        assertTrue(state.targetWork.getValue(key).reached)
    }

    @Test fun nestedAndDependentBodiesRefusePublication() {
        listOf("Work until 20 stitches remain", "Purl while shaping", "Review needed: ambiguous").forEach { wording ->
            assertThrows(InvalidGuideDefinitionException::class.java) {
                GuideDefinitionValidator.validate(GuideDefinition(definition.guideId, definition.revisionId,
                    definition.rootNodeIds, definition.nodes.map { if (it.id == b) Instruction(b, wording) else it }))
            }
        }
        assertThrows(InvalidGuideDefinitionException::class.java) {
            GuideDefinitionValidator.validate(GuideDefinition(definition.guideId, definition.revisionId,
                definition.rootNodeIds, definition.nodes.map { if (it.id == b) Instruction(b, "Purl", target) else it }))
        }
    }

    @Test fun targetBodyWorkCannotCarryAcrossPublicationsOrBeMarkedDoneWithoutEvidence() {
        val state = engine.complete(start()).state
        val newer = GuideDefinition(definition.guideId, DefinitionRevisionId("new"), definition.rootNodeIds, definition.nodes)
        assertTrue(RevisionProgressComparison.preview(definition, newer, state.completedAddresses).candidates.isEmpty())
        val body = traversal.targetCycle(traversal.first().address)!!
        assertThrows(InvalidExecutionStateException::class.java) {
            ExecutionStateValidator.validate(state.copy(completedAddresses = body.body.toSet()), guide)
        }
        assertThrows(InvalidExecutionStateException::class.java) {
            ExecutionStateValidator.validate(state.copy(targetWork = mapOf(body.last to TargetWork())), guide)
        }
    }

    @Test fun repeatLabelsAndUnknownAncestorConditionsCannotHideDependencies() {
        val repeat = NodeId("repeat")
        val outer = NodeId("outer")
        val nestedLabel = ExecutionEngineFixtures.definition(listOf(s), Section(s, "Body", listOf(repeat, b), target),
            Repeat(repeat, 2, "While shaping", listOf(a)), Instruction(a, "Knit"), Instruction(b, "Purl"))
        val unknownOuter = GuideDefinition(definition.guideId, definition.revisionId, listOf(outer),
            definition.nodes + Section(outer, "Repeat until the piece is long enough", definition.rootNodeIds))
        listOf(nestedLabel, unknownOuter).forEach { invalid ->
            assertThrows(InvalidGuideDefinitionException::class.java) { GuideDefinitionValidator.validate(invalid) }
        }
    }

    @Test fun legacyInstructionTargetsKeepExistingCastOnCountsAcrossCyclesAndPrevious() {
        val definition = ExecutionEngineFixtures.definition(listOf(a), Instruction(a, "Cast on 20 stitches", target))
        val engine = ExecutionEngine.forDefinition(definition)
        var state = engine.newExecution(ExecutionId("legacy"))
        state = engine.adjustCastOn(state, 1).state
        val counts = state.castOnCounts
        state = engine.complete(state).state
        assertEquals(counts, state.castOnCounts)
        state = engine.previous(state).state
        assertEquals(counts, state.castOnCounts)
    }
}
