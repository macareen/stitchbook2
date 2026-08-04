package com.macareen.stitchbook2.data.database

import com.macareen.stitchbook2.domain.execution.AncestryFrame
import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.ExecutionAddress
import com.macareen.stitchbook2.domain.execution.ExecutionEngine
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.execution.ExecutionTransitionResult
import com.macareen.stitchbook2.domain.execution.NodeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ExecutionEntityMappingTest {

    @Test
    fun executionStateRoundTripsThroughEntitiesWithOrderedAncestryFrames() {
        val engine = ExecutionEngine.forDefinition(nestedRepeatRangeDefinition())
        val afterFirstComplete =
            (engine.complete(engine.newExecution(ExecutionId("exec"))) as
                ExecutionTransitionResult.Changed).state
        val state = (engine.complete(afterFirstComplete) as
            ExecutionTransitionResult.Changed).state

        // Repeat iteration 1, row 3 is current; rows 1 and 2 are complete.
        assertEquals(
            listOf(
                AncestryFrame.RepeatIteration(id("repeat"), 1),
                AncestryFrame.RangeValue(id("range"), 3)
            ),
            state.currentAddress?.ancestryFrames
        )

        val aggregate = ExecutionAggregate(
            execution = state.toExecutionEntity(
                createdAt = 100,
                updatedAt = 200,
                completedAt = null,
                version = 3
            ),
            currentFrames = state.toCurrentFrameEntities(),
            completedOccurrences = state.toCompletedOccurrenceEntities(),
            completedOccurrenceFrames = state.toCompletedOccurrenceFrameEntities(),
            revision = revisionEntity(),
            revisionNodes = nestedRepeatRangeRows()
        )

        val restored = aggregate.toDomain()

        assertEquals(state, restored.state)
        assertEquals(3L, restored.version)
        assertEquals(100L, restored.createdAt)
        assertEquals(200L, restored.updatedAt)
        assertNull(restored.completedAt)
    }

    @Test
    fun addressSignatureDependsOnFrameOrderAndValue() {
        val repeatFrame = AncestryFrame.RepeatIteration(id("repeat"), 1)
        val rangeFrame = AncestryFrame.RangeValue(id("range"), 2)

        val forward = ExecutionAddress(
            definitionRevisionId = DefinitionRevisionId("revision"),
            instructionNodeId = id("instruction"),
            ancestryFrames = listOf(repeatFrame, rangeFrame)
        )
        val reversed = forward.copy(ancestryFrames = listOf(rangeFrame, repeatFrame))
        val differentValue = forward.copy(
            ancestryFrames = listOf(
                repeatFrame,
                AncestryFrame.RangeValue(id("range"), 3)
            )
        )

        assertEquals(forward.toSignature(), forward.toSignature())
        assertNotEquals(forward.toSignature(), reversed.toSignature())
        assertNotEquals(forward.toSignature(), differentValue.toSignature())
    }

    @Test
    fun addressSignatureIsDeterministic() {
        val address = ExecutionAddress(
            definitionRevisionId = DefinitionRevisionId("revision"),
            instructionNodeId = id("instruction"),
            ancestryFrames = listOf(
                AncestryFrame.RepeatIteration(id("repeat"), 2),
                AncestryFrame.RangeValue(id("range"), 5)
            )
        )
        val equivalentButDistinctInstance = ExecutionAddress(
            definitionRevisionId = DefinitionRevisionId("revision"),
            instructionNodeId = id("instruction"),
            ancestryFrames = listOf(
                AncestryFrame.RepeatIteration(id("repeat"), 2),
                AncestryFrame.RangeValue(id("range"), 5)
            )
        )

        assertEquals(address.toSignature(), address.toSignature())
        assertEquals(
            address.toSignature(),
            equivalentButDistinctInstance.toSignature()
        )
    }

    @Test
    fun addressSignatureDistinguishesClassicFieldBoundaryShift() {
        // "ab" + "cd" and "a" + "bcd" concatenate to identical raw
        // characters ("abcd") without a length-aware boundary between
        // instructionNodeId and a frame's containerNodeId; the signature
        // must still tell these two distinct addresses apart.
        val splitEarly = ExecutionAddress(
            definitionRevisionId = DefinitionRevisionId("revision"),
            instructionNodeId = id("ab"),
            ancestryFrames = listOf(AncestryFrame.RangeValue(id("cd"), 1))
        )
        val splitLate = ExecutionAddress(
            definitionRevisionId = DefinitionRevisionId("revision"),
            instructionNodeId = id("a"),
            ancestryFrames = listOf(AncestryFrame.RangeValue(id("bcd"), 1))
        )
        assertNotEquals(splitEarly.toSignature(), splitLate.toSignature())

        // The same shift pattern between a containerNodeId and a frame
        // value rendered as digits: "1" followed by 23 versus "12"
        // followed by 3.
        val valueSplitEarly = ExecutionAddress(
            definitionRevisionId = DefinitionRevisionId("revision"),
            instructionNodeId = id("instruction"),
            ancestryFrames = listOf(AncestryFrame.RangeValue(id("1"), 23))
        )
        val valueSplitLate = ExecutionAddress(
            definitionRevisionId = DefinitionRevisionId("revision"),
            instructionNodeId = id("instruction"),
            ancestryFrames = listOf(AncestryFrame.RangeValue(id("12"), 3))
        )
        assertNotEquals(valueSplitEarly.toSignature(), valueSplitLate.toSignature())
    }

    @Test
    fun addressSignatureCannotCollideForSeparatorLikeCharacters() {
        val revisionId = DefinitionRevisionId("revision")
        fun address(instructionNodeId: String, frames: List<AncestryFrame>) =
            ExecutionAddress(
                definitionRevisionId = revisionId,
                instructionNodeId = id(instructionNodeId),
                ancestryFrames = frames
            )

        val adversarialAddresses = listOf(
            address("a", emptyList()),
            address("a:", emptyList()),
            address("a|", emptyList()),
            address("a#", emptyList()),
            address("a", listOf(AncestryFrame.RangeValue(id(":"), 1))),
            address("a", listOf(AncestryFrame.RangeValue(id("|"), 1))),
            address("a", listOf(AncestryFrame.RangeValue(id("#"), 1))),
            address("a:RANGE_VALUE:b:1", emptyList()),
            address("a", listOf(AncestryFrame.RangeValue(id("RANGE_VALUE"), 1))),
            address("4:node", emptyList()),
            address("node", listOf(AncestryFrame.RangeValue(id("4"), 0))),
            address("1", listOf(AncestryFrame.RangeValue(id("2"), 34))),
            address("12", listOf(AncestryFrame.RangeValue(id("3"), 4))),
            address("ab", listOf(AncestryFrame.RangeValue(id("cd"), 1))),
            address("a", listOf(AncestryFrame.RangeValue(id("bcd"), 1))),
            address(
                "a",
                listOf(
                    AncestryFrame.RepeatIteration(id("b"), 1),
                    AncestryFrame.RangeValue(id("c"), 2)
                )
            ),
            address(
                "a",
                listOf(
                    AncestryFrame.RangeValue(id("b"), 1),
                    AncestryFrame.RepeatIteration(id("c"), 2)
                )
            )
        )

        val signatures = adversarialAddresses.map { it.toSignature() }
        assertEquals(signatures.size, signatures.distinct().size)
    }

    @Test
    fun malformedFrameTypeFailsExplicitlyRatherThanBeingRepaired() {
        val aggregate = ExecutionAggregate(
            execution = ExecutionEntity(
                id = "exec",
                guideId = "guide",
                definitionRevisionId = "revision",
                status = "ACTIVE",
                currentInstructionNodeId = "instruction",
                createdAt = 1,
                updatedAt = 1,
                completedAt = null,
                version = 0
            ),
            currentFrames = listOf(
                ExecutionCurrentAddressFrameEntity(
                    executionId = "exec",
                    frameOrder = 0,
                    containerNodeId = "range",
                    frameType = "BOGUS",
                    frameValue = 1
                )
            ),
            completedOccurrences = emptyList(),
            completedOccurrenceFrames = emptyList(),
            revision = revisionEntity(),
            revisionNodes = nestedRepeatRangeRows()
        )

        assertThrows(MalformedPersistedExecutionStateException::class.java) {
            aggregate.toDomain()
        }
    }

    @Test
    fun malformedStatusFailsExplicitly() {
        val aggregate = ExecutionAggregate(
            execution = ExecutionEntity(
                id = "exec",
                guideId = "guide",
                definitionRevisionId = "revision",
                status = "BOGUS",
                currentInstructionNodeId = "instruction",
                createdAt = 1,
                updatedAt = 1,
                completedAt = null,
                version = 0
            ),
            currentFrames = emptyList(),
            completedOccurrences = emptyList(),
            completedOccurrenceFrames = emptyList(),
            revision = revisionEntity(),
            revisionNodes = nestedRepeatRangeRows()
        )

        assertThrows(MalformedPersistedExecutionStateException::class.java) {
            aggregate.toDomain()
        }
    }

    @Test
    fun revisionMismatchedCurrentAddressFailsExplicitly() {
        val aggregate = ExecutionAggregate(
            execution = ExecutionEntity(
                id = "exec",
                guideId = "guide",
                definitionRevisionId = "revision",
                status = "ACTIVE",
                currentInstructionNodeId = "missing-node",
                createdAt = 1,
                updatedAt = 1,
                completedAt = null,
                version = 0
            ),
            currentFrames = emptyList(),
            completedOccurrences = emptyList(),
            completedOccurrenceFrames = emptyList(),
            revision = revisionEntity(),
            revisionNodes = nestedRepeatRangeRows()
        )

        assertThrows(MalformedPersistedExecutionStateException::class.java) {
            aggregate.toDomain()
        }
    }

    @Test
    fun malformedUnderlyingRevisionPropagatesItsOwnExplicitFailure() {
        val aggregate = ExecutionAggregate(
            execution = ExecutionEntity(
                id = "exec",
                guideId = "guide",
                definitionRevisionId = "revision",
                status = "ACTIVE",
                currentInstructionNodeId = "instruction",
                createdAt = 1,
                updatedAt = 1,
                completedAt = null,
                version = 0
            ),
            currentFrames = emptyList(),
            completedOccurrences = emptyList(),
            completedOccurrenceFrames = emptyList(),
            revision = revisionEntity(),
            revisionNodes = listOf(
                RevisionNodeEntity(
                    revisionId = "revision",
                    nodeId = "repeat",
                    parentNodeId = null,
                    childOrder = 0,
                    type = "REPEAT",
                    title = null,
                    instructionText = null,
                    rangeUnitLabel = null,
                    rangeStartInclusive = null,
                    rangeEndInclusive = null,
                    repeatCount = null,
                    repeatLabel = null
                )
            )
        )

        assertThrows(MalformedPersistedDefinitionException::class.java) {
            aggregate.toDomain()
        }
    }

    private fun nestedRepeatRangeDefinition() =
        RevisionAggregate(revisionEntity(), nestedRepeatRangeRows()).toDomain().definition

    private fun revisionEntity() = DefinitionRevisionEntity(
        id = "revision",
        guideId = "guide",
        revisionNumber = 1,
        createdAt = 10
    )

    private fun nestedRepeatRangeRows() = listOf(
        revisionNode("section", null, 0, "SECTION", title = "Lace panel"),
        revisionNode(
            "repeat",
            "section",
            0,
            "REPEAT",
            repeatCount = 2,
            repeatLabel = "Lace"
        ),
        revisionNode(
            "range",
            "repeat",
            0,
            "RANGE",
            rangeUnitLabel = "row",
            rangeStartInclusive = 1,
            rangeEndInclusive = 3
        ),
        revisionNode(
            "instruction",
            "range",
            0,
            "INSTRUCTION",
            instructionText = "Work even"
        )
    )

    private fun revisionNode(
        nodeId: String,
        parentNodeId: String?,
        childOrder: Int,
        type: String,
        title: String? = null,
        instructionText: String? = null,
        rangeUnitLabel: String? = null,
        rangeStartInclusive: Int? = null,
        rangeEndInclusive: Int? = null,
        repeatCount: Int? = null,
        repeatLabel: String? = null
    ) = RevisionNodeEntity(
        revisionId = "revision",
        nodeId = nodeId,
        parentNodeId = parentNodeId,
        childOrder = childOrder,
        type = type,
        title = title,
        instructionText = instructionText,
        rangeUnitLabel = rangeUnitLabel,
        rangeStartInclusive = rangeStartInclusive,
        rangeEndInclusive = rangeEndInclusive,
        repeatCount = repeatCount,
        repeatLabel = repeatLabel
    )

    private fun id(value: String) = NodeId(value)
}
