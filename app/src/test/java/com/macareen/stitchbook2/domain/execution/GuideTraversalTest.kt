package com.macareen.stitchbook2.domain.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTraversalTest {

    @Test
    fun singleInstructionProducesOneOccurrence() {
        val occurrences = GuideTraversal(
            ExecutionEngineFixtures.singleInstructionGuide()
        ).occurrences().toList()

        assertEquals(1, occurrences.size)
        assertEquals(
            ExecutionEngineFixtures.nodeId("instruction"),
            occurrences.single().address.instructionNodeId
        )
        assertTrue(occurrences.single().address.ancestryFrames.isEmpty())
    }

    @Test
    fun sectionVisitsInstructionsInStoredOrderWithoutAddingFrames() {
        val section = Section(
            id = id("section"),
            title = "Finishing",
            children = listOf(id("second"), id("first"))
        )
        val guide = ExecutionEngineFixtures.validated(
            roots = listOf(section.id),
            section,
            Instruction(id("first"), "First"),
            Instruction(id("second"), "Second")
        )

        val occurrences = GuideTraversal(guide).occurrences().toList()

        assertEquals(listOf(id("second"), id("first")), occurrences.map {
            it.address.instructionNodeId
        })
        assertTrue(occurrences.all { it.address.ancestryFrames.isEmpty() })
    }

    @Test
    fun knitTenRoundsProducesInclusiveRangeOccurrences() {
        val occurrences = GuideTraversal(
            ExecutionEngineFixtures.knitTenRoundsGuide()
        ).occurrences().toList()

        assertEquals(10, occurrences.size)
        assertEquals((1..10).toList(), occurrences.map { occurrence ->
            (occurrence.address.ancestryFrames.single() as AncestryFrame.RangeValue).value
        })
        assertTrue(occurrences.all {
            it.address.instructionNodeId == id("knit")
        })
    }

    @Test
    fun repeatedTwoRowLaceSequenceCompletesEachIterationBeforeAdvancing() {
        val occurrences = GuideTraversal(
            ExecutionEngineFixtures.laceRepeatGuide()
        ).occurrences().toList()

        assertEquals(12, occurrences.size)
        assertEquals(
            (1..6).flatMap { iteration ->
                listOf(iteration to id("row-a"), iteration to id("row-b"))
            },
            occurrences.map { occurrence ->
                val frame = occurrence.address.ancestryFrames.single()
                    as AncestryFrame.RepeatIteration
                frame.iteration to occurrence.address.instructionNodeId
            }
        )
    }

    @Test
    fun repeatContainingRangeProducesOrderedStructuralFrames() {
        val occurrences = GuideTraversal(
            ExecutionEngineFixtures.repeatContainingRangeGuide()
        ).occurrences().toList()

        assertEquals(12, occurrences.size)
        assertEquals(
            (1..3).flatMap { repeat ->
                (1..4).map { round -> repeat to round }
            },
            occurrences.map { occurrence ->
                val repeat = occurrence.address.ancestryFrames[0]
                    as AncestryFrame.RepeatIteration
                val range = occurrence.address.ancestryFrames[1]
                    as AncestryFrame.RangeValue
                repeat.iteration to range.value
            }
        )
    }

    @Test
    fun ancestryNodePathIncludesContainersAndTheInstructionInOrder() {
        val traversal = GuideTraversal(
            ExecutionEngineFixtures.repeatContainingRangeGuide()
        )
        val fifthOccurrence = traversal.occurrences().elementAt(4)

        assertEquals(
            listOf(
                id("textured-band"),
                id("band-repeat"),
                id("band-rounds"),
                id("texture")
            ),
            traversal.ancestryNodePath(fifthOccurrence.address)
        )
    }

    @Test
    fun ancestryNodePathRejectsAnUnresolvedAddress() {
        val traversal = GuideTraversal(
            ExecutionEngineFixtures.singleInstructionGuide()
        )
        val staleAddress = traversal.first().address.copy(
            instructionNodeId = id("missing")
        )

        assertThrows(InvalidExecutionAddressException::class.java) {
            traversal.ancestryNodePath(staleAddress)
        }
    }

    @Test
    fun nestedRangeAndRepeatTraversalIsDepthFirstAndFinite() {
        val outerRange = Range(
            id = id("motifs"),
            unitLabel = "motif",
            startInclusive = 2,
            endInclusive = 3,
            children = listOf(id("repeat"))
        )
        val repeat = Repeat(
            id = id("repeat"),
            count = 2,
            children = listOf(id("passes"))
        )
        val innerRange = Range(
            id = id("passes"),
            unitLabel = "pass",
            startInclusive = 5,
            endInclusive = 6,
            children = listOf(id("instruction"))
        )
        val guide = ExecutionEngineFixtures.validated(
            roots = listOf(outerRange.id),
            outerRange,
            repeat,
            innerRange,
            Instruction(id("instruction"), "Work pass")
        )

        val projections = GuideTraversal(guide).occurrences().map { occurrence ->
            occurrence.address.ancestryFrames.map { frame ->
                when (frame) {
                    is AncestryFrame.RangeValue -> frame.value
                    is AncestryFrame.RepeatIteration -> frame.iteration
                }
            }
        }.toList()

        assertEquals(
            listOf(
                listOf(2, 1, 5),
                listOf(2, 1, 6),
                listOf(2, 2, 5),
                listOf(2, 2, 6),
                listOf(3, 1, 5),
                listOf(3, 1, 6),
                listOf(3, 2, 5),
                listOf(3, 2, 6)
            ),
            projections
        )
    }

    @Test
    fun firstLastNextAndPreviousUseTraversalOrder() {
        val traversal = GuideTraversal(
            ExecutionEngineFixtures.knitTenRoundsGuide()
        )
        val occurrences = traversal.occurrences().toList()

        assertEquals(occurrences.first(), traversal.first())
        assertEquals(occurrences.last(), traversal.last())
        assertNull(traversal.previous(occurrences.first().address))
        assertEquals(occurrences[3], traversal.next(occurrences[2].address))
        assertEquals(occurrences[2], traversal.previous(occurrences[3].address))
        assertNull(traversal.next(occurrences.last().address))
    }

    @Test
    fun structuralAddressEqualityDoesNotDependOnInstanceIdentity() {
        val address = GuideTraversal(
            ExecutionEngineFixtures.repeatContainingRangeGuide()
        ).occurrences().elementAt(5).address
        val copy = ExecutionAddress(
            definitionRevisionId = DefinitionRevisionId(address.definitionRevisionId.value),
            instructionNodeId = NodeId(address.instructionNodeId.value),
            ancestryFrames = address.ancestryFrames.map { frame ->
                when (frame) {
                    is AncestryFrame.RangeValue -> frame.copy()
                    is AncestryFrame.RepeatIteration -> frame.copy()
                }
            }
        )

        assertEquals(address, copy)
        assertEquals(address.hashCode(), copy.hashCode())
    }

    @Test
    fun addressDefensivelyCopiesAncestryFrames() {
        val frames = mutableListOf<AncestryFrame>(
            AncestryFrame.RangeValue(id("rounds"), 1)
        )
        val address = ExecutionAddress(
            definitionRevisionId = ExecutionEngineFixtures.revisionId,
            instructionNodeId = id("knit"),
            ancestryFrames = frames
        )

        frames.clear()

        assertEquals(1, address.ancestryFrames.size)
    }

    @Test
    fun addressBelongsOnlyToExactDefinitionRevision() {
        val traversal = GuideTraversal(
            ExecutionEngineFixtures.singleInstructionGuide()
        )
        val address = traversal.first().address
        val staleAddress = address.copy(
            definitionRevisionId = DefinitionRevisionId("revision-older")
        )

        assertTrue(traversal.contains(address))
        assertFalse(traversal.contains(staleAddress))
    }

    @Test
    fun twoAtATimeSleevesRemainOneReviewedInstructionInV1() {
        val instruction = Instruction(
            id = id("two-at-a-time"),
            text = "Work both sleeves two at a time"
        )
        val guide = ExecutionEngineFixtures.validated(
            roots = listOf(instruction.id),
            instruction
        )

        val occurrence = GuideTraversal(guide).occurrences().single()

        assertEquals(instruction, occurrence.instruction)
        assertTrue(occurrence.address.ancestryFrames.isEmpty())
    }

    private fun id(value: String) = ExecutionEngineFixtures.nodeId(value)
}
