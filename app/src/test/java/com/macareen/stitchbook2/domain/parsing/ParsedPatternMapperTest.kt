package com.macareen.stitchbook2.domain.parsing

import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsedPatternMapperTest {

    private fun source(line: Int) = SourceReference(pageNumber = 1, lineNumber = line)

    private fun newIdGenerator(): () -> String {
        var counter = 0
        return { "node-${counter++}" }
    }

    private fun nodesById(nodes: List<com.macareen.stitchbook2.domain.guide.DraftNode>) =
        nodes.associateBy { it.id }

    @Test
    fun `a plain instruction becomes an Instruction node with provenance appended`() {
        val pattern = ParsedPattern(
            rootNodes = listOf(ParsedInstruction("Cast on 80 stitches.", source(3))),
            issues = emptyList()
        )

        val result = ParsedPatternMapper.toDraftNodes(pattern, newIdGenerator())

        assertEquals(1, result.rootNodeIds.size)
        val node = nodesById(result.nodes).getValue(result.rootNodeIds.single())
        assertEquals(DraftNodeType.INSTRUCTION, node.type)
        assertEquals("Cast on 80 stitches. (p.1 l.3)", node.instructionText)
    }

    @Test
    fun `a section preserves its title verbatim and nests its children`() {
        val instruction = ParsedInstruction("Knit all stitches.", source(2))
        val pattern = ParsedPattern(
            rootNodes = listOf(ParsedSection("Body", listOf(instruction), source(1))),
            issues = emptyList()
        )

        val result = ParsedPatternMapper.toDraftNodes(pattern, newIdGenerator())

        val byId = nodesById(result.nodes)
        val section = byId.getValue(result.rootNodeIds.single())
        assertEquals(DraftNodeType.SECTION, section.type)
        assertEquals("Body", section.title)
        assertEquals(1, section.children.size)

        val child = byId.getValue(section.children.single())
        assertEquals(DraftNodeType.INSTRUCTION, child.type)
        assertEquals("Knit all stitches. (p.1 l.2)", child.instructionText)
    }

    @Test
    fun `a range preserves unit label and bounds`() {
        val pattern = ParsedPattern(
            rootNodes = listOf(
                ParsedRange(
                    unitLabel = "round",
                    startInclusive = 1,
                    endInclusive = 10,
                    children = listOf(ParsedInstruction("Knit all stitches.", source(1))),
                    source = source(1)
                )
            ),
            issues = emptyList()
        )

        val result = ParsedPatternMapper.toDraftNodes(pattern, newIdGenerator())

        val range = nodesById(result.nodes).getValue(result.rootNodeIds.single())
        assertEquals(DraftNodeType.RANGE, range.type)
        assertEquals("round", range.rangeUnitLabel)
        assertEquals(1, range.rangeStartInclusive)
        assertEquals(10, range.rangeEndInclusive)
        assertEquals(1, range.children.size)
    }

    @Test
    fun `a repeat preserves its count and wraps its children`() {
        val pattern = ParsedPattern(
            rootNodes = listOf(
                ParsedRepeat(
                    count = 6,
                    children = listOf(
                        ParsedInstruction("Row A", source(1)),
                        ParsedInstruction("Row B", source(2))
                    ),
                    source = source(3)
                )
            ),
            issues = emptyList()
        )

        val result = ParsedPatternMapper.toDraftNodes(pattern, newIdGenerator())

        val repeat = nodesById(result.nodes).getValue(result.rootNodeIds.single())
        assertEquals(DraftNodeType.REPEAT, repeat.type)
        assertEquals(6, repeat.repeatCount)
        assertEquals(2, repeat.children.size)
    }

    @Test
    fun `issues become their own clearly marked root instructions`() {
        val pattern = ParsedPattern(
            rootNodes = listOf(ParsedInstruction("Cast on 80 stitches.", source(1))),
            issues = listOf(ParsingIssue("Could not find row 1-2 to repeat.", source(4)))
        )

        val result = ParsedPatternMapper.toDraftNodes(pattern, newIdGenerator())

        assertEquals(2, result.rootNodeIds.size)
        val byId = nodesById(result.nodes)
        val issueNode = byId.getValue(result.rootNodeIds[1])
        assertEquals(DraftNodeType.INSTRUCTION, issueNode.type)
        assertTrue(issueNode.instructionText!!.startsWith("Review needed:"))
        assertTrue(issueNode.instructionText!!.contains("Could not find row 1-2 to repeat."))
        assertTrue(issueNode.instructionText!!.contains("(p.1 l.4)"))
    }

    @Test
    fun `every generated node id is unique`() {
        val pattern = ParsedPattern(
            rootNodes = listOf(
                ParsedSection(
                    "Body",
                    listOf(
                        ParsedRange(
                            unitLabel = "row",
                            startInclusive = 1,
                            endInclusive = 2,
                            children = listOf(ParsedInstruction("Knit.", source(2))),
                            source = source(2)
                        )
                    ),
                    source(1)
                )
            ),
            issues = listOf(ParsingIssue("ambiguous", source(3)))
        )

        val result = ParsedPatternMapper.toDraftNodes(pattern, newIdGenerator())

        val allIds = result.nodes.map { it.id }
        assertEquals(allIds.size, allIds.toSet().size)
        assertNotNull(NodeId("node-0")) // sanity: the generator's id format is a plain NodeId
    }
}
