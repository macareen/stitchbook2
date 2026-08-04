package com.macareen.stitchbook2.domain.execution

import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class GuideValidationTest {

    @Test
    fun duplicateNodeIdsAreRejected() {
        val duplicateId = id("duplicate")
        val error = invalid(
            ExecutionEngineFixtures.definition(
                roots = listOf(duplicateId),
                Instruction(duplicateId, "First"),
                Instruction(duplicateId, "Second")
            )
        )

        assertHas<GuideDefinitionError.DuplicateNodeId>(error)
    }

    @Test
    fun definitionWithoutRootsIsRejectedAsEmpty() {
        val error = invalid(
            ExecutionEngineFixtures.definition(
                roots = emptyList(),
                Instruction(id("orphan"), "Unreachable")
            )
        )

        assertHas<GuideDefinitionError.EmptyDefinition>(error)
    }

    @Test
    fun emptySectionIsRejected() {
        val section = Section(id("section"), "Empty", emptyList())

        val error = invalid(
            ExecutionEngineFixtures.definition(listOf(section.id), section)
        )

        assertHas<GuideDefinitionError.EmptyChildren>(error)
        assertHas<GuideDefinitionError.ContainerWithoutExecutableDescendant>(error)
    }

    @Test
    fun emptyRangeIsRejected() {
        val range = Range(id("range"), "round", 1, 2, emptyList())

        val error = invalid(
            ExecutionEngineFixtures.definition(listOf(range.id), range)
        )

        assertHas<GuideDefinitionError.EmptyChildren>(error)
    }

    @Test
    fun emptyRepeatIsRejected() {
        val repeat = Repeat(id("repeat"), 2, children = emptyList())

        val error = invalid(
            ExecutionEngineFixtures.definition(listOf(repeat.id), repeat)
        )

        assertHas<GuideDefinitionError.EmptyChildren>(error)
    }

    @Test
    fun containerChainWithoutExecutableDescendantIsRejected() {
        val outer = Section(id("outer"), "Outer", listOf(id("inner")))
        val inner = Section(id("inner"), "Inner", emptyList())

        val error = invalid(
            ExecutionEngineFixtures.definition(
                listOf(outer.id),
                outer,
                inner
            )
        )

        assertTrue(
            error.errors.contains(
                GuideDefinitionError.ContainerWithoutExecutableDescendant(outer.id)
            )
        )
    }

    @Test
    fun descendingRangeBoundsAreRejected() {
        val range = Range(
            id = id("range"),
            unitLabel = "round",
            startInclusive = 10,
            endInclusive = 1,
            children = listOf(id("instruction"))
        )

        val error = invalid(
            ExecutionEngineFixtures.definition(
                listOf(range.id),
                range,
                Instruction(id("instruction"), "Knit")
            )
        )

        assertHas<GuideDefinitionError.InvalidRangeBounds>(error)
    }

    @Test
    fun blankRangeUnitLabelIsRejected() {
        val range = Range(
            id = id("range"),
            unitLabel = " ",
            startInclusive = 1,
            endInclusive = 2,
            children = listOf(id("instruction"))
        )

        val error = invalid(
            ExecutionEngineFixtures.definition(
                listOf(range.id),
                range,
                Instruction(id("instruction"), "Knit")
            )
        )

        assertHas<GuideDefinitionError.BlankRangeUnitLabel>(error)
    }

    @Test
    fun nonPositiveRepeatCountIsRejected() {
        val repeat = Repeat(
            id = id("repeat"),
            count = 0,
            children = listOf(id("instruction"))
        )

        val error = invalid(
            ExecutionEngineFixtures.definition(
                listOf(repeat.id),
                repeat,
                Instruction(id("instruction"), "Knit")
            )
        )

        assertHas<GuideDefinitionError.NonPositiveRepeatCount>(error)
    }

    @Test
    fun missingChildNodeIsRejected() {
        val section = Section(
            id = id("section"),
            title = "Broken",
            children = listOf(id("missing"))
        )

        val error = invalid(
            ExecutionEngineFixtures.definition(listOf(section.id), section)
        )

        assertHas<GuideDefinitionError.MissingNode>(error)
    }

    @Test
    fun cyclesAreRejected() {
        val first = Section(id("first"), "First", listOf(id("second")))
        val second = Section(id("second"), "Second", listOf(id("first")))

        val error = invalid(
            ExecutionEngineFixtures.definition(
                roots = listOf(first.id),
                first,
                second
            )
        )

        assertHas<GuideDefinitionError.CycleDetected>(error)
    }

    @Test
    fun sharedChildIsRejectedBecauseDefinitionMustBeATree() {
        val first = Section(id("first"), "First", listOf(id("instruction")))
        val second = Section(id("second"), "Second", listOf(id("instruction")))

        val error = invalid(
            ExecutionEngineFixtures.definition(
                roots = listOf(first.id, second.id),
                first,
                second,
                Instruction(id("instruction"), "Knit")
            )
        )

        assertHas<GuideDefinitionError.MultipleParents>(error)
    }

    @Test
    fun unreachableNodesAreRejected() {
        val root = Instruction(id("root"), "Knit")
        val orphan = Instruction(id("orphan"), "Purl")

        val error = invalid(
            ExecutionEngineFixtures.definition(
                roots = listOf(root.id),
                root,
                orphan
            )
        )

        assertHas<GuideDefinitionError.UnreachableNode>(error)
    }

    @Test
    fun definitionsDefensivelyCopyNodeCollections() {
        val childIds = mutableListOf(id("instruction"))
        val section = Section(id("section"), "Body", childIds)
        val nodes = mutableListOf<GuideNode>(
            section,
            Instruction(id("instruction"), "Knit")
        )
        val definition = GuideDefinition(
            guideId = ExecutionEngineFixtures.guideId,
            revisionId = ExecutionEngineFixtures.revisionId,
            rootNodeIds = mutableListOf(section.id),
            nodes = nodes
        )

        childIds.clear()
        nodes.clear()

        GuideDefinitionValidator.validate(definition)
        assertTrue(section.children.isNotEmpty())
        assertTrue(definition.nodes.isNotEmpty())
    }

    private fun invalid(definition: GuideDefinition): InvalidGuideDefinitionException {
        return assertThrows(InvalidGuideDefinitionException::class.java) {
            GuideDefinitionValidator.validate(definition)
        }
    }

    private inline fun <reified T : GuideDefinitionError> assertHas(
        exception: InvalidGuideDefinitionException
    ) {
        assertTrue(
            "Expected ${T::class.simpleName} in ${exception.errors}",
            exception.errors.any { it is T }
        )
    }

    private fun id(value: String) = ExecutionEngineFixtures.nodeId(value)
}
