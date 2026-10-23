package com.macareen.stitchbook2.domain.parsing

import com.macareen.stitchbook2.domain.execution.ManualTarget
import com.macareen.stitchbook2.domain.execution.TargetComparison
import com.macareen.stitchbook2.domain.execution.TargetUnit
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType

/** Deliberately small grammar: a self-contained cycle and one independent termination condition.
 * Suggestions are derived, never saved as targets by the importer. No unit conversion or context lookup.
 */
object PdfTargetSuggestions {
    private val dependent = Regex("""\b(?:at the same time|simultaneously|while|as before|as established)\b""", RegexOption.IGNORE_CASE)
    private val condition = Regex("""\b(?:until|at the same time|simultaneously|while)\b""", RegexOption.IGNORE_CASE)
    private val provenance = Regex("""\s+\(p\.\d+(?: l\.\d+)?\)$""")
    private const val NUMBER = """([0-9]+(?:\.[0-9]+)?)"""
    private const val CYCLE = """(?:(?:knit|purl) (?:all stitches|one row|one round)|(?:single|double) crochet (?:one row|one round))"""
    private val length = Regex("""^$CYCLE until (?:the )?piece measures $NUMBER (cm|inches|in)\.?$""", RegexOption.IGNORE_CASE)
    // Explicit cadence and direction; 'decrease as before' or 'N stitches' alone cannot define a cycle.
    private val stitches = Regex("""^(increase|decrease) one stitch every (?:row|round) until $NUMBER stitches remain\.?$""", RegexOption.IGNORE_CASE)

    fun needsReview(text: String): Boolean = condition.containsMatchIn(text)

    fun reviewIssues(nodes: List<ParsedNode>): List<ParsingIssue> = nodes.flatMap { node ->
        when (node) {
            is ParsedSection -> reviewIssues(node.children)
            is ParsedRange -> reviewIssues(node.children)
            is ParsedRepeat -> reviewIssues(node.children)
            is ParsedInstruction -> if (needsReview(node.text) && suggest(node.text) == null)
                listOf(ParsingIssue("Check this condition against the PDF; no target was inferred: ${node.text}", node.source)) else emptyList()
        }
    }

    // Use the same paragraph cleanup as parsing before sizing, so wrapped alternatives and units
    // are retained together. Raw condition lines outside recognized work remain available too.
    fun sourceNotes(document: ExtractedDocument): String? =
        (PatternTextParser.splitReference(document).instructions.filter { needsReview(it.text) } +
            document.lines.filter { needsReview(it.text) }).distinct()
        .takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "Original PDF condition wording (before size selection; review against the PDF):\n") {
            "(p.${it.source.pageNumber} l.${it.source.lineNumber}) ${it.text}"
        }

    /** Context can live on a different PDF line. Keep suggestions unavailable while any draft
     * review warning or dependent/simultaneous work remains. Users can still author targets manually.
     */
    fun forNodes(nodes: List<DraftNode>): Map<NodeId, ManualTarget> {
        if (nodes.any { dependent.containsMatchIn(it.instructionText.orEmpty()) || dependent.containsMatchIn(it.title.orEmpty()) ||
                it.instructionText.orEmpty().startsWith("Review needed:") }) return emptyMap()
        return nodes.mapNotNull { node ->
            if (node.type != DraftNodeType.INSTRUCTION || node.target != null) null
            else suggest(node.instructionText.orEmpty())?.let { node.id to it }
        }.toMap()
    }

    fun suggest(text: String): ManualTarget? {
        val wording = text.trim().replace(provenance, "")
        length.matchEntire(wording)?.let { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return null
            val unit = if (match.groupValues[2].equals("cm", true)) TargetUnit.CENTIMETRES else TargetUnit.INCHES
            return runCatching { ManualTarget(unit, value) }.getOrNull()
        }
        stitches.matchEntire(wording)?.let { match ->
            val value = match.groupValues[2].toDoubleOrNull() ?: return null
            val comparison = if (match.groupValues[1].equals("decrease", true)) TargetComparison.AT_MOST else TargetComparison.AT_LEAST
            return runCatching { ManualTarget(TargetUnit.STITCHES, value, comparison) }.getOrNull()
        }
        return null
    }
}
