package com.macareen.stitchbook2.domain.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExecutionAddressValidationTest {

    @Test
    fun revisionMismatchIsReportedExplicitly() {
        val traversal = GuideTraversal(
            ExecutionEngineFixtures.singleInstructionGuide()
        )
        val stale = traversal.first().address.copy(
            definitionRevisionId = DefinitionRevisionId("revision-2")
        )

        val exception = assertThrows(InvalidExecutionAddressException::class.java) {
            traversal.resolve(stale)
        }

        assertEquals(
            ExecutionAddressError.RevisionMismatch(
                expected = ExecutionEngineFixtures.revisionId,
                actual = DefinitionRevisionId("revision-2")
            ),
            exception.error
        )
    }

    @Test
    fun missingInstructionNodeIsRejected() {
        val traversal = GuideTraversal(
            ExecutionEngineFixtures.singleInstructionGuide()
        )
        val address = ExecutionAddress(
            definitionRevisionId = ExecutionEngineFixtures.revisionId,
            instructionNodeId = id("missing")
        )

        val exception = assertThrows(InvalidExecutionAddressException::class.java) {
            traversal.resolve(address)
        }

        assertEquals(
            ExecutionAddressError.InstructionNodeMissing(id("missing")),
            exception.error
        )
    }

    @Test
    fun containerCannotBeResolvedAsExecutableTarget() {
        val traversal = GuideTraversal(
            ExecutionEngineFixtures.knitTenRoundsGuide()
        )
        val address = ExecutionAddress(
            definitionRevisionId = ExecutionEngineFixtures.revisionId,
            instructionNodeId = id("rounds")
        )

        val exception = assertThrows(InvalidExecutionAddressException::class.java) {
            traversal.resolve(address)
        }

        assertEquals(
            ExecutionAddressError.TargetIsNotInstruction(id("rounds")),
            exception.error
        )
    }

    @Test
    fun outOfBoundsRangeFrameIsMalformed() {
        val traversal = GuideTraversal(
            ExecutionEngineFixtures.knitTenRoundsGuide()
        )
        val frame = AncestryFrame.RangeValue(id("rounds"), 11)
        val address = ExecutionAddress(
            definitionRevisionId = ExecutionEngineFixtures.revisionId,
            instructionNodeId = id("knit"),
            ancestryFrames = listOf(frame)
        )

        val exception = assertThrows(InvalidExecutionAddressException::class.java) {
            traversal.resolve(address)
        }

        assertEquals(
            ExecutionAddressError.MalformedAncestryFrame(
                frame,
                "Range value is outside the inclusive bounds."
            ),
            exception.error
        )
    }

    @Test
    fun staleAddressWithMissingAncestryIsUnresolved() {
        val traversal = GuideTraversal(
            ExecutionEngineFixtures.knitTenRoundsGuide()
        )
        val address = ExecutionAddress(
            definitionRevisionId = ExecutionEngineFixtures.revisionId,
            instructionNodeId = id("knit")
        )

        val exception = assertThrows(InvalidExecutionAddressException::class.java) {
            traversal.next(address)
        }

        assertEquals(
            ExecutionAddressError.UnresolvedAddress(address),
            exception.error
        )
    }

    @Test
    fun wrongFrameOrderIsUnresolvedEvenWhenIndividualFramesAreValid() {
        val traversal = GuideTraversal(
            ExecutionEngineFixtures.repeatContainingRangeGuide()
        )
        val valid = traversal.first().address
        val reordered = valid.copy(
            ancestryFrames = valid.ancestryFrames.reversed()
        )

        val exception = assertThrows(InvalidExecutionAddressException::class.java) {
            traversal.resolve(reordered)
        }

        assertEquals(
            ExecutionAddressError.UnresolvedAddress(reordered),
            exception.error
        )
    }

    @Test
    fun duplicateContainerFramesAreMalformed() {
        val traversal = GuideTraversal(
            ExecutionEngineFixtures.knitTenRoundsGuide()
        )
        val frame = AncestryFrame.RangeValue(id("rounds"), 1)
        val address = ExecutionAddress(
            definitionRevisionId = ExecutionEngineFixtures.revisionId,
            instructionNodeId = id("knit"),
            ancestryFrames = listOf(frame, frame.copy())
        )

        val exception = assertThrows(InvalidExecutionAddressException::class.java) {
            traversal.resolve(address)
        }

        assertEquals(
            ExecutionAddressError.DuplicateAncestryFrame(id("rounds")),
            exception.error
        )
    }

    private fun id(value: String) = ExecutionEngineFixtures.nodeId(value)
}
