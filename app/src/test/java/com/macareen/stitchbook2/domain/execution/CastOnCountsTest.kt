package com.macareen.stitchbook2.domain.execution

import org.junit.Assert.*
import org.junit.Test

class CastOnCountsTest {
    private val cast = NodeId("cast")
    private val repeat = NodeId("repeat")
    private val guide = ExecutionEngineFixtures.validated(listOf(repeat),
        Repeat(repeat, 2, children = listOf(cast)), Instruction(cast, "Cast on 6 sts."))
    private val engine = ExecutionEngine.forValidatedDefinition(guide)
    @Test fun countingDoesNotCompleteAndRepeatedOccurrencesAreIndependent() {
        val initial = engine.newExecution(ExecutionId("work"))
        assertEquals(initial, engine.adjustCastOn(initial, -1).state)
        val counted = engine.adjustCastOn(initial, 1).state
        assertTrue(counted.completedAddresses.isEmpty())
        val second = engine.complete(counted).state
        assertNull(second.castOnCounts[second.currentAddress])
        val twice = engine.adjustCastOn(second, 1).state
        assertEquals(2, twice.castOnCounts.size)
        assertEquals(counted.castOnCounts, engine.previous(second).state.castOnCounts)
        assertTrue(engine.newExecution(ExecutionId("other-piece")).castOnCounts.isEmpty())
    }
    @Test fun codecRoundTripsOccurrenceFramesAndRejectsDuplicatesOrNegativeCounts() {
        val state = engine.adjustCastOn(engine.newExecution(ExecutionId("work")), 1).state
        val json = CastOnCounts.encode(state.castOnCounts)
        assertEquals(state.castOnCounts, CastOnCounts.decode(json, state.definitionRevisionId))
        assertThrows(IllegalArgumentException::class.java) { CastOnCounts.decode(json.replace("\"count\":1", "\"count\":-1"), state.definitionRevisionId) }
        assertThrows(IllegalArgumentException::class.java) { CastOnCounts.decode("[" + json.drop(1).dropLast(1) + "," + json.drop(1).dropLast(1) + "]", state.definitionRevisionId) }
    }
    @Test fun nonCastOnAddressAndInvalidFramesAreRejected() {
        val other = ExecutionEngine.forValidatedDefinition(ExecutionEngineFixtures.singleInstructionGuide())
        val state = other.newExecution(ExecutionId("other"))
        assertThrows(IllegalArgumentException::class.java) { other.adjustCastOn(state, 1) }
        assertThrows(InvalidExecutionStateException::class.java) { other.complete(state.copy(castOnCounts = mapOf(state.currentAddress!! to 1))) }
    }
}
