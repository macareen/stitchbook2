package com.macareen.stitchbook2.domain.parsing

import org.junit.Assert.*
import org.junit.Test

class PatternPieceReplayTest {
    private fun parse(vararg text: String): ParsedPattern = PatternTextParser.parse(ExtractedDocument(1,
        text.mapIndexed { i, line -> ExtractedLine(line, SourceReference(1, i + 1)) }))

    @Test fun identicalSecondPieceWordingReplaysTheCompleteMixedCaseSleeveSection() {
        for ((heading, reference) in listOf("Mittens" to "Make the other mitten identically.",
            "Sleeves" to "Make the second sleeve in the same manner.", "Cuffs" to "Work the other cuff the same way.")) {
            val pattern = parse(heading, "Cast on 20 sts.", "Rows 1-3: Knit.", "Bind off.", reference)
            assertTrue(reference, pattern.issues.isEmpty())
            val section = pattern.rootNodes.single() as ParsedSection
            assertEquals(section.children.dropLast(1), (section.children.last() as ParsedSection).children)
        }
    }

    @Test fun secondSleeveReplaysSetupRangesAndNestedRepeatsWithoutFinishingOrBody() {
        val pattern = parse("BODY", "Cast on 80 sts.", "Sleeve:", "Cast on 20 sts.", "Row 1: Knit.", "Row 2: Purl.",
            "Work rows 1 and 2 a total of 3 times.", "Rows 3-4: Knit.", "Bind off.", "Repeat for the second sleeve.", "FINISHING", "Sew seams.")
        assertTrue(pattern.issues.isEmpty())
        val sleeve = pattern.rootNodes[1] as ParsedSection
        val second = sleeve.children.last() as ParsedSection
        assertEquals("Repeat for the second sleeve.", second.title)
        assertEquals(sleeve.children.dropLast(1), second.children)
        assertEquals(3, (second.children[1] as ParsedRepeat).count)
        assertEquals(SourceReference(1, 4), second.children.first().source)
        var id = 0
        val mapped = ParsedPatternMapper.toDraftNodes(pattern, { "node-${id++}" })
        assertEquals(mapped.nodes.size, mapped.nodes.map { it.id }.distinct().size)
        assertEquals(2, mapped.nodes.count { it.instructionText?.startsWith("Cast on 20 sts.") == true })
        assertEquals(1, mapped.nodes.count { it.instructionText?.startsWith("Sew seams.") == true })
        assertEquals(1, mapped.nodes.count { it.instructionText?.startsWith("Cast on 80 sts.") == true })
    }

    @Test fun aSeparateSecondSleeveSectionCanReferToTheUniqueFirstSection() {
        val pattern = parse("First sleeve:", "Cast on 20 sts.", "Row 1: Knit.", "Bind off.", "Second sleeve:", "Work the second sleeve as for the first.")
        assertTrue(pattern.issues.isEmpty())
        val first = pattern.rootNodes[0] as ParsedSection
        val second = pattern.rootNodes[1] as ParsedSection
        assertEquals(first.children, (second.children.single() as ParsedSection).children)
    }

    @Test fun makeTwoHeadingExecutesTheWholePieceTwiceAndKeepsTheQuantity() {
        val pattern = parse("Sleeves (make 2)", "Cast on 20 sts.", "Rows 1-3: Knit.", "Bind off.", "Finishing:", "Sew seams.")
        assertTrue(pattern.issues.isEmpty())
        val section = pattern.rootNodes.first() as ParsedSection
        assertEquals("Sleeves (make 2)", section.title)
        val repeat = section.children.single() as ParsedRepeat
        assertEquals(2, repeat.count)
        assertEquals("Piece", repeat.label)
        assertEquals(3, repeat.children.size)
    }

    @Test fun explicitSecondPieceInsideMakeTwoDoesNotMultiplyTheWorkAgain() {
        val section = parse("Sleeves (make 2):", "Cast on 20 sts.", "Bind off.", "Repeat for the second sleeve.").rootNodes.single() as ParsedSection
        assertTrue(section.children.none { it is ParsedRepeat })
        assertEquals(section.children.take(2), (section.children.last() as ParsedSection).children)
    }

    @Test fun alteredShapingAndMissingOrAmbiguousSourcesStayVisibleWithReview() {
        for (lines in listOf(
            arrayOf("Sleeve:", "Cast on 20 sts.", "Repeat for the second sleeve, reversing all shaping."),
            arrayOf("Sleeve:", "Cast on 20 sts.", "Other sleeve:", "Work as for the first, reversing shaping."),
            arrayOf("Cast on 20 sts.", "Repeat for the second sleeve."),
            arrayOf("Left sleeve:", "Cast on 20 sts.", "Right sleeve:", "Cast on 24 sts.", "Finishing:", "Repeat for the other sleeve."))) {
            val pattern = parse(*lines)
            assertTrue(lines.joinToString(), pattern.issues.any { it.message.contains("choose and review") })
            assertTrue(lines.joinToString(), pattern.issues.any { it.message.contains(lines.last()) })
        }
    }

    @Test fun anotherPieceCannotCopyAnAlreadyExpandedPair() {
        val pattern = parse("Sleeves (make 2):", "Cast on 20 sts.", "Finishing:", "Repeat for the other sleeve.")
        assertEquals(1, pattern.issues.size)
        assertTrue((pattern.rootNodes.last() as ParsedSection).children.single() is ParsedInstruction)
    }

    @Test fun unresolvedEarlierChangesCannotBeCopiedOrHiddenByALaterReference() {
        val pattern = parse("Sleeve:", "Cast on 20 sts.", "Work the other sleeve, reversing shaping.",
            "Repeat for the second sleeve.", "Finishing:", "Repeat for the other sleeve.")
        assertEquals(3, pattern.issues.size)
        assertTrue((pattern.rootNodes.first() as ParsedSection).children.all { it is ParsedInstruction })
        val modified = parse("First sleeve:", "Cast on 20 sts.", "Second sleeve:", "Cast on 24 sts.", "Repeat for the second sleeve.")
        assertEquals(1, modified.issues.size)
    }

    @Test fun excessivePieceCountsAreNotExpanded() {
        for (count in listOf("0", "101", "999999999999999")) {
            val pattern = parse("Sleeves (make $count):", "Cast on 20 sts.")
            assertEquals(1, pattern.issues.size)
            assertTrue((pattern.rootNodes.single() as ParsedSection).children.single() is ParsedInstruction)
        }
    }
}
