package com.macareen.stitchbook2.data.database

import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.Instruction
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.execution.Range
import com.macareen.stitchbook2.domain.execution.Repeat
import com.macareen.stitchbook2.domain.execution.Section
import com.macareen.stitchbook2.domain.guide.DraftId
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import com.macareen.stitchbook2.domain.guide.GuideDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideEntityMappingTest {

    @Test
    fun draftTreeRoundTripPreservesHierarchyAndOrder() {
        val draft = validDraft()

        val restored = DraftAggregate(
            draft = draft.entity(),
            nodes = draft.toNodeEntities().reversed()
        ).toDomain()

        assertEquals(draft.rootNodeIds, restored.rootNodeIds)
        assertEquals(
            draft.nodes.associateBy(DraftNode::id),
            restored.nodes.associateBy(DraftNode::id)
        )
    }

    @Test
    fun invalidDraftFieldsStillRoundTrip() {
        val draft = GuideDraft(
            id = DraftId("draft"),
            guideId = GuideId("guide"),
            baseRevisionId = null,
            createdAt = 1,
            updatedAt = 2,
            version = 0,
            rootNodeIds = listOf(id("range")),
            nodes = listOf(
                DraftNode(
                    id = id("range"),
                    type = DraftNodeType.RANGE,
                    rangeUnitLabel = null,
                    rangeStartInclusive = null,
                    rangeEndInclusive = null
                )
            )
        )

        val restored = DraftAggregate(
            draft.entity(),
            draft.toNodeEntities()
        ).toDomain()

        assertEquals(draft.nodes, restored.nodes)
    }

    @Test
    fun draftWithMissingReferencedNodeIsRejectedBeforePersistence() {
        val draft = validDraft().copy(
            rootNodeIds = listOf(id("missing"))
        )

        assertThrows(InvalidDraftTreeException::class.java) {
            draft.toNodeEntities()
        }
    }

    @Test
    fun draftWithSharedNodeIsRejectedBeforePersistence() {
        val draft = validDraft()
        val section = draft.nodes.first().copy(
            children = listOf(id("range"), id("instruction"))
        )
        val range = draft.nodes[1].copy(
            children = listOf(id("instruction"))
        )

        assertThrows(InvalidDraftTreeException::class.java) {
            draft.copy(nodes = listOf(section, range, draft.nodes[2]))
                .toNodeEntities()
        }
    }

    @Test
    fun validDraftMapsToCanonicalDefinition() {
        val definition = validDraft().toGuideDefinitionForPublication(
            DefinitionRevisionId("revision")
        )

        assertEquals(GuideId("guide"), definition.guideId)
        assertEquals(DefinitionRevisionId("revision"), definition.revisionId)
        assertTrue(definition.nodes[0] is Section)
        assertTrue(definition.nodes[1] is Range)
        assertTrue(definition.nodes[2] is Instruction)
    }

    @Test
    fun incompleteDraftCannotMapForPublication() {
        val draft = validDraft().copy(
            nodes = validDraft().nodes.map { node ->
                if (node.type == DraftNodeType.RANGE) {
                    node.copy(rangeEndInclusive = null)
                } else {
                    node
                }
            }
        )

        assertThrows(InvalidDraftForPublicationException::class.java) {
            draft.toGuideDefinitionForPublication(
                DefinitionRevisionId("revision")
            )
        }
    }

    @Test
    fun publishedRangeRepeatAndInstructionFieldsRoundTrip() {
        val rows = listOf(
            revisionNode(
                nodeId = "section",
                parentNodeId = null,
                childOrder = 0,
                type = "SECTION",
                title = "Texture"
            ),
            revisionNode(
                nodeId = "repeat",
                parentNodeId = "section",
                childOrder = 0,
                type = "REPEAT",
                repeatCount = 3,
                repeatLabel = "Band"
            ),
            revisionNode(
                nodeId = "range",
                parentNodeId = "repeat",
                childOrder = 0,
                type = "RANGE",
                rangeUnitLabel = "round",
                rangeStartInclusive = 2,
                rangeEndInclusive = 5
            ),
            revisionNode(
                nodeId = "instruction",
                parentNodeId = "range",
                childOrder = 0,
                type = "INSTRUCTION",
                instructionText = "Work texture"
            )
        )

        val definition = revisionAggregate(rows).toDomain().definition

        val repeat = definition.nodes[1] as Repeat
        val range = definition.nodes[2] as Range
        val instruction = definition.nodes[3] as Instruction
        assertEquals(3, repeat.count)
        assertEquals("Band", repeat.label)
        assertEquals("round", range.unitLabel)
        assertEquals(2, range.startInclusive)
        assertEquals(5, range.endInclusive)
        assertEquals("Work texture", instruction.text)
    }

    @Test
    fun revisionChildOrderIsRestoredFromStoredOrder() {
        val rows = listOf(
            revisionNode("section", null, 0, "SECTION", title = "Body"),
            revisionNode(
                "second",
                "section",
                1,
                "INSTRUCTION",
                instructionText = "Second"
            ),
            revisionNode(
                "first",
                "section",
                0,
                "INSTRUCTION",
                instructionText = "First"
            )
        )

        val definition = revisionAggregate(rows).toDomain().definition

        assertEquals(
            listOf(id("first"), id("second")),
            (definition.nodes.first() as Section).children
        )
    }

    @Test
    fun malformedImmutableRevisionFailsExplicitly() {
        val rows = listOf(
            revisionNode(
                nodeId = "repeat",
                parentNodeId = null,
                childOrder = 0,
                type = "REPEAT",
                repeatCount = null
            )
        )

        assertThrows(MalformedPersistedDefinitionException::class.java) {
            revisionAggregate(rows).toDomain()
        }
    }

    @Test
    fun oneStoredRangeNodeProducesMultipleRuntimeOccurrencesWithoutDuplication() {
        val revision = revisionAggregate(
            validDraft().toNodeEntities().map {
                it.toRevisionNode("revision")
            }
        ).toDomain()

        val storedNodeCount = revision.definition.nodes.size
        val occurrenceCount =
            com.macareen.stitchbook2.domain.execution.GuideTraversal(
                com.macareen.stitchbook2.domain.execution.GuideDefinitionValidator
                    .validate(revision.definition)
            ).occurrences().count()

        assertEquals(3, storedNodeCount)
        assertEquals(4, occurrenceCount)
    }

    private fun validDraft() = GuideDraft(
        id = DraftId("draft"),
        guideId = GuideId("guide"),
        baseRevisionId = null,
        createdAt = 1,
        updatedAt = 2,
        version = 0,
        rootNodeIds = listOf(id("section")),
        nodes = listOf(
            DraftNode(
                id = id("section"),
                type = DraftNodeType.SECTION,
                title = "Body",
                children = listOf(id("range"))
            ),
            DraftNode(
                id = id("range"),
                type = DraftNodeType.RANGE,
                rangeUnitLabel = "round",
                rangeStartInclusive = 1,
                rangeEndInclusive = 4,
                children = listOf(id("instruction"))
            ),
            DraftNode(
                id = id("instruction"),
                type = DraftNodeType.INSTRUCTION,
                instructionText = "Knit all stitches"
            )
        )
    )

    private fun GuideDraft.entity() = GuideDraftEntity(
        id = id.value,
        guideId = guideId.value,
        baseRevisionId = baseRevisionId?.value,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version
    )

    private fun revisionAggregate(
        rows: List<RevisionNodeEntity>
    ) = RevisionAggregate(
        revision = DefinitionRevisionEntity(
            id = "revision",
            guideId = "guide",
            revisionNumber = 1,
            createdAt = 10
        ),
        nodes = rows
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
