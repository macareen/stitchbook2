package com.macareen.stitchbook2.domain.execution

internal object ExecutionEngineFixtures {
    val guideId = GuideId("guide")
    val revisionId = DefinitionRevisionId("revision-1")

    fun nodeId(value: String) = NodeId(value)

    fun definition(
        roots: List<NodeId>,
        vararg nodes: GuideNode,
        revisionId: DefinitionRevisionId = this.revisionId
    ) = GuideDefinition(
        guideId = guideId,
        revisionId = revisionId,
        rootNodeIds = roots,
        nodes = nodes.toList()
    )

    fun validated(
        roots: List<NodeId>,
        vararg nodes: GuideNode
    ) = GuideDefinitionValidator.validate(
        definition(roots, *nodes)
    )

    fun singleInstructionGuide(): ValidatedGuideDefinition {
        val instruction = Instruction(nodeId("instruction"), "Knit all stitches")
        return validated(
            roots = listOf(instruction.id),
            instruction
        )
    }

    fun knitTenRoundsGuide(): ValidatedGuideDefinition {
        val section = Section(
            id = nodeId("body"),
            title = "Body",
            children = listOf(nodeId("rounds"))
        )
        val range = Range(
            id = nodeId("rounds"),
            unitLabel = "round",
            startInclusive = 1,
            endInclusive = 10,
            children = listOf(nodeId("knit"))
        )
        val instruction = Instruction(nodeId("knit"), "Knit all stitches")
        return validated(
            roots = listOf(section.id),
            section,
            range,
            instruction
        )
    }

    fun laceRepeatGuide(): ValidatedGuideDefinition {
        val section = Section(
            id = nodeId("lace-panel"),
            title = "Lace panel",
            children = listOf(nodeId("lace-repeat"))
        )
        val repeat = Repeat(
            id = nodeId("lace-repeat"),
            count = 6,
            children = listOf(nodeId("row-a"), nodeId("row-b"))
        )
        return validated(
            roots = listOf(section.id),
            section,
            repeat,
            Instruction(nodeId("row-a"), "Yarn over, knit two together across"),
            Instruction(nodeId("row-b"), "Purl across")
        )
    }

    fun repeatContainingRangeGuide(): ValidatedGuideDefinition {
        val section = Section(
            id = nodeId("textured-band"),
            title = "Textured band",
            children = listOf(nodeId("band-repeat"))
        )
        val repeat = Repeat(
            id = nodeId("band-repeat"),
            count = 3,
            children = listOf(nodeId("band-rounds"))
        )
        val range = Range(
            id = nodeId("band-rounds"),
            unitLabel = "round",
            startInclusive = 1,
            endInclusive = 4,
            children = listOf(nodeId("texture"))
        )
        return validated(
            roots = listOf(section.id),
            section,
            repeat,
            range,
            Instruction(nodeId("texture"), "Work texture round")
        )
    }
}
