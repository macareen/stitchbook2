package com.macareen.stitchbook2.domain.parsing

import com.macareen.stitchbook2.domain.execution.ManualTarget
import com.macareen.stitchbook2.domain.execution.TargetComparison
import com.macareen.stitchbook2.domain.execution.TargetUnit
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import com.macareen.stitchbook2.domain.guide.GuideDraft
import com.macareen.stitchbook2.domain.guide.DraftTargetCycles

/** Deliberately small grammar: a self-contained cycle and one independent termination condition.
 * Suggestions are derived, never saved as targets by the importer. No unit conversion or context lookup.
 */
object PdfTargetSuggestions {
    private val dependent = Regex("""\b(?:at the same time|simultaneously|while|as before|as established|depending|unless)\b""", RegexOption.IGNORE_CASE)
    private val condition = Regex("""\b(?:until|at the same time|simultaneously|while)\b""", RegexOption.IGNORE_CASE)
    private val provenance = Regex("""\s+\(p\.\d+(?: l\.\d+)?\)$""")
    private const val NUMBER = """([0-9]+(?:\.[0-9]+)?)"""
    private const val CYCLE = """(?:(?:knit|purl) (?:all stitches|one row|one round)|(?:single|double) crochet (?:one row|one round))"""
    private val length = Regex("""^$CYCLE until (?:the )?piece measures $NUMBER (cm|inches|in)\.?$""", RegexOption.IGNORE_CASE)
    // Explicit cadence and direction; 'decrease as before' or 'N stitches' alone cannot define a cycle.
    private val stitches = Regex("""^(increase|decrease) one stitch every (?:row|round) until $NUMBER stitches remain\.?$""", RegexOption.IGNORE_CASE)

    fun needsReview(text: String): Boolean = condition.containsMatchIn(text)

    fun reviewIssues(nodes: List<ParsedNode>): List<ParsingIssue> {
        var next = 0
        val mapped = ParsedPatternMapper.toDraftNodes(ParsedPattern(nodes, emptyList())) { "review-${next++}" }
        val accepted = bodySuggestions(mapped.nodes, mapped.rootNodeIds).keys.mapNotNull { id ->
            mapped.nodes.firstOrNull { it.id == id }?.instructionText?.replace(provenance, "")
        }.toSet()
        fun issues(children: List<ParsedNode>): List<ParsingIssue> = children.flatMap { node ->
            when (node) {
                is ParsedSection -> issues(node.children)
                is ParsedRange -> issues(node.children)
                is ParsedRepeat -> issues(node.children)
                is ParsedInstruction -> if (needsReview(node.text) && suggest(node.text) == null && node.text !in accepted)
                    listOf(ParsingIssue("Check this condition against the PDF; no target was inferred: ${node.text}", node.source)) else emptyList()
            }
        }
        return issues(nodes)
    }

    private val reference = Regex("""^Repeat (rows?|rounds?) ([0-9]+)(?:[-–]([0-9]+))? until (.+)$""", RegexOption.IGNORE_CASE)
    private val referencePrefix = Regex("""^Repeat (?:rows?|rounds?)\b.*\buntil\b""", RegexOption.IGNORE_CASE)
    private val row = Regex("""^(Row|Round) ([0-9]+)(?:\s*\([^)]*\))?: .+$""", RegexOption.IGNORE_CASE)
    private val bodyLength = Regex("""^(?:the )?piece measures $NUMBER (cm|inches|in)\.?$""", RegexOption.IGNORE_CASE)
    private val bodyStitches = Regex("""^at (least|most) $NUMBER stitches remain\.?$""", RegexOption.IGNORE_CASE)
    fun startsBodyReference(text: String): Boolean = referencePrefix.containsMatchIn(text)

    data class BodySuggestion(val conditionId: NodeId, val bodyIds: List<NodeId>, val target: ManualTarget)

    /** Only adjacent, ordered, explicitly numbered rows in the same container. No name lookup,
     * backward search, inferred cadence, or unbounded expansion. Original condition stays a step.
     */
    fun bodySuggestions(nodes: List<DraftNode>, rootNodeIds: List<NodeId>? = null): Map<NodeId, BodySuggestion> {
        if (nodes.any { it.target != null || dependent.containsMatchIn(it.instructionText.orEmpty()) ||
                dependent.containsMatchIn(it.title.orEmpty()) || dependent.containsMatchIn(it.repeatLabel.orEmpty()) ||
                it.instructionText.orEmpty().startsWith("Review needed:") }) return emptyMap()
        if (nodes.count { needsReview(it.instructionText.orEmpty()) } != 1) return emptyMap()
        val byId = nodes.associateBy { it.id }
        val childIds = nodes.flatMap { it.children }.toSet()
        val siblings = listOf(rootNodeIds ?: nodes.filter { it.id !in childIds }.map { it.id }) + nodes.filter { it.children.isNotEmpty() }.map { it.children }
        val result = mutableMapOf<NodeId, BodySuggestion>()
        for (ids in siblings) for ((index, id) in ids.withIndex()) {
            val node = byId[id] ?: continue
            if (node.type != DraftNodeType.INSTRUCTION) continue
            val match = reference.matchEntire(node.instructionText.orEmpty().trim().replace(provenance, "")) ?: continue
            val start = match.groupValues[2].toIntOrNull() ?: continue
            val end = match.groupValues[3].ifEmpty { match.groupValues[2] }.toIntOrNull() ?: continue
            if (start < 1 || end < start || end.toLong() - start + 1 > 32) continue
            val conditionText = match.groupValues[4]
            val target = bodyLength.matchEntire(conditionText)?.let {
                runCatching { ManualTarget(if (it.groupValues[2].equals("cm", true)) TargetUnit.CENTIMETRES else TargetUnit.INCHES, it.groupValues[1].toDouble()) }.getOrNull()
            } ?: bodyStitches.matchEntire(conditionText)?.let {
                runCatching { ManualTarget(TargetUnit.STITCHES, it.groupValues[2].toDouble(), if (it.groupValues[1].equals("most", true)) TargetComparison.AT_MOST else TargetComparison.AT_LEAST) }.getOrNull()
            } ?: continue
            val unit = match.groupValues[1].lowercase().removeSuffix("s")
            var expected = end
            val body = mutableListOf<NodeId>()
            for (previous in ids.take(index).asReversed()) {
                val child = byId[previous] ?: break
                val span = if (child.type == DraftNodeType.RANGE && child.rangeUnitLabel == unit)
                    (child.rangeStartInclusive ?: break) to (child.rangeEndInclusive ?: break)
                else if (child.type == DraftNodeType.INSTRUCTION) {
                    val label = row.matchEntire(child.instructionText.orEmpty().replace(provenance, "")) ?: break
                    if (!label.groupValues[1].equals(unit, true)) break
                    val number = label.groupValues[2].toIntOrNull() ?: break
                    number to number
                } else break
                if (span.second != expected || span.first < start) break
                body.add(0, previous)
                expected = span.first - 1
                if (expected < start) break
            }
            if (expected != start - 1 || body.isEmpty()) continue
            // Reuse the published-body guard for hidden conditions, finite bounds and ancestors.
            val suggestion = BodySuggestion(id, body, target)
            val draft = GuideDraft(com.macareen.stitchbook2.domain.guide.DraftId("preview"), com.macareen.stitchbook2.domain.execution.GuideId("preview"), null, 0, 0, 0, siblings.first(), nodes)
            if (DraftTargetCycles.problem(applyBody(draft, suggestion, NodeId(generateSequence("pdf-body-preview") { "$it-" }.first { candidate -> nodes.none { it.id.value == candidate } }), target)) == null) result[id] = suggestion
        }
        return if (result.size == 1) result else emptyMap()
    }

    fun applyBody(draft: GuideDraft, suggestion: BodySuggestion, id: NodeId, target: ManualTarget): GuideDraft {
        require(draft.nodes.none { it.id == id })
        fun wrap(ids: List<NodeId>) = ids.flatMap { if (it == suggestion.bodyIds.first()) listOf(id) else if (it in suggestion.bodyIds) emptyList() else listOf(it) }
        return draft.copy(rootNodeIds = wrap(draft.rootNodeIds), nodes = draft.nodes.map { it.copy(children = wrap(it.children)) } +
            DraftNode(id, DraftNodeType.SECTION, title = "Reviewed PDF body", children = suggestion.bodyIds, target = target))
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
    fun forNodes(nodes: List<DraftNode>, rootNodeIds: List<NodeId>? = null): Map<NodeId, ManualTarget> {
        if (nodes.any { dependent.containsMatchIn(it.instructionText.orEmpty()) || dependent.containsMatchIn(it.title.orEmpty()) ||
                it.instructionText.orEmpty().startsWith("Review needed:") }) return emptyMap()
        return nodes.mapNotNull { node ->
            if (node.type != DraftNodeType.INSTRUCTION || node.target != null) null
            else suggest(node.instructionText.orEmpty())?.let { node.id to it }
        }.toMap() + bodySuggestions(nodes, rootNodeIds).mapValues { it.value.target }
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
