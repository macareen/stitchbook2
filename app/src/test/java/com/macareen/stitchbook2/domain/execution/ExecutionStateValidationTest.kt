package com.macareen.stitchbook2.domain.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ExecutionStateValidationTest {

    @Test
    fun activeStateRequiresResolvableLeafPointer() {
        val guide = ExecutionEngineFixtures.knitTenRoundsGuide()
        val current = GuideTraversal(guide).first().address
        val state = state(
            currentAddress = current,
            completedAddresses = emptySet()
        )

        assertEquals(state, ExecutionStateValidator.validate(state, guide))
    }

    @Test
    fun currentPointerFromAnotherRevisionIsRejected() {
        val guide = ExecutionEngineFixtures.singleInstructionGuide()
        val stale = GuideTraversal(guide).first().address.copy(
            definitionRevisionId = DefinitionRevisionId("stale-revision")
        )
        val state = state(
            currentAddress = stale,
            completedAddresses = emptySet()
        )

        val exception = assertThrows(InvalidExecutionStateException::class.java) {
            ExecutionStateValidator.validate(state, guide)
        }

        assertTrue(
            exception.errors.any {
                it is ExecutionStateError.InvalidCurrentAddress
            }
        )
    }

    @Test
    fun completedAddressesMustResolveStructurally() {
        val guide = ExecutionEngineFixtures.knitTenRoundsGuide()
        val current = GuideTraversal(guide).first().address
        val malformed = current.copy(ancestryFrames = emptyList())
        val state = state(
            currentAddress = current,
            completedAddresses = setOf(malformed)
        )

        val exception = assertThrows(InvalidExecutionStateException::class.java) {
            ExecutionStateValidator.validate(state, guide)
        }

        assertTrue(
            exception.errors.any {
                it is ExecutionStateError.InvalidCompletedAddress
            }
        )
    }

    @Test
    fun terminalStateRequiresEveryOccurrenceToBeComplete() {
        val guide = ExecutionEngineFixtures.knitTenRoundsGuide()
        val completed = GuideTraversal(guide)
            .occurrences()
            .take(9)
            .map { it.address }
            .toSet()
        val state = state(
            currentAddress = null,
            completedAddresses = completed
        )

        val exception = assertThrows(InvalidExecutionStateException::class.java) {
            ExecutionStateValidator.validate(state, guide)
        }

        assertTrue(
            exception.errors.any {
                it == ExecutionStateError.IncompleteExecutionWithoutCurrentAddress(
                    completedCount = 9,
                    totalCount = 10
                )
            }
        )
    }

    @Test
    fun terminalStateWithEveryOccurrenceCompleteIsValid() {
        val guide = ExecutionEngineFixtures.knitTenRoundsGuide()
        val completed = GuideTraversal(guide)
            .occurrences()
            .map { it.address }
            .toSet()
        val state = state(
            currentAddress = null,
            completedAddresses = completed
        )

        assertEquals(state, ExecutionStateValidator.validate(state, guide))
        assertTrue(state.isComplete)
    }

    @Test
    fun activeStatusWithoutCurrentPointerIsRejected() {
        val guide = ExecutionEngineFixtures.singleInstructionGuide()
        val invalid = ExecutionState(
            executionId = ExecutionId("execution"),
            guideId = ExecutionEngineFixtures.guideId,
            definitionRevisionId = ExecutionEngineFixtures.revisionId,
            currentAddress = null,
            completedAddresses = emptySet(),
            status = ExecutionStatus.ACTIVE
        )

        val exception = assertThrows(InvalidExecutionStateException::class.java) {
            ExecutionStateValidator.validate(invalid, guide)
        }

        assertTrue(
            ExecutionStateError.ActiveExecutionWithoutCurrentAddress in exception.errors
        )
    }

    @Test
    fun completedStatusWithCurrentPointerIsRejected() {
        val guide = ExecutionEngineFixtures.singleInstructionGuide()
        val current = GuideTraversal(guide).first().address
        val invalid = ExecutionState(
            executionId = ExecutionId("execution"),
            guideId = ExecutionEngineFixtures.guideId,
            definitionRevisionId = ExecutionEngineFixtures.revisionId,
            currentAddress = current,
            completedAddresses = setOf(current),
            status = ExecutionStatus.COMPLETED
        )

        val exception = assertThrows(InvalidExecutionStateException::class.java) {
            ExecutionStateValidator.validate(invalid, guide)
        }

        assertTrue(
            exception.errors.any {
                it == ExecutionStateError.CompletedExecutionWithCurrentAddress(current)
            }
        )
    }

    private fun state(
        currentAddress: ExecutionAddress?,
        completedAddresses: Set<ExecutionAddress>
    ) = ExecutionState(
        executionId = ExecutionId("execution"),
        guideId = ExecutionEngineFixtures.guideId,
        definitionRevisionId = ExecutionEngineFixtures.revisionId,
        currentAddress = currentAddress,
        completedAddresses = completedAddresses
    )
}
