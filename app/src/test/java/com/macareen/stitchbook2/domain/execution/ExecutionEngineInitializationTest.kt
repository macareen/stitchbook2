package com.macareen.stitchbook2.domain.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ExecutionEngineInitializationTest {

    @Test
    fun newExecutionStartsAtFirstInstruction() {
        val guide = ExecutionEngineFixtures.laceRepeatGuide()
        val traversal = GuideTraversal(guide)

        val state = ExecutionEngine
            .forValidatedDefinition(guide)
            .newExecution(ExecutionId("execution"))

        assertEquals(traversal.first().address, state.currentAddress)
        assertEquals(ExecutionStatus.ACTIVE, state.status)
        assertFalse(state.isComplete)
    }

    @Test
    fun newExecutionHasNoCompletedOccurrences() {
        val state = ExecutionEngine
            .forValidatedDefinition(ExecutionEngineFixtures.knitTenRoundsGuide())
            .newExecution(ExecutionId("execution"))

        assertTrue(state.completedAddresses.isEmpty())
    }

    @Test
    fun newExecutionRecordsExactGuideAndRevisionIdentity() {
        val guide = ExecutionEngineFixtures.singleInstructionGuide()

        val state = ExecutionEngine
            .forValidatedDefinition(guide)
            .newExecution(ExecutionId("execution"))

        assertEquals(guide.definition.guideId, state.guideId)
        assertEquals(guide.definition.revisionId, state.definitionRevisionId)
    }

    @Test
    fun invalidDefinitionCannotCreateEngine() {
        val emptyDefinition = ExecutionEngineFixtures.definition(
            roots = emptyList()
        )

        assertThrows(InvalidGuideDefinitionException::class.java) {
            ExecutionEngine.forDefinition(emptyDefinition)
        }
    }
}
