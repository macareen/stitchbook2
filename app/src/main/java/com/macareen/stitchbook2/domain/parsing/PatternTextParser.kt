package com.macareen.stitchbook2.domain.parsing

/**
 * Deterministic line-by-line parser turning an [ExtractedDocument] into a
 * [ParsedPattern]. This is intentionally a small, explicit, documented
 * subset of pattern-text conventions, not a general natural-language
 * parser -- ROADMAP.md Phase 12 explicitly rules out "universal accuracy"
 * and "AI dependency" for this deterministic stage. A line this parser does
 * not recognize as one of its supported forms below still becomes a plain
 * [ParsedInstruction] (patterns are full of preamble/note lines that are not
 * numbered rows), and only a line that looks like it was *trying* to be a
 * recognized form but doesn't parse cleanly (a malformed row/round line, or
 * a Repeat line whose target can't be found) becomes a [ParsingIssue] --
 * the parser never silently reinterprets or drops such a line, it just
 * flags it for review and keeps the line itself as a plain instruction.
 *
 * Supported line forms (case-insensitive keywords):
 * - `Section: <title>` -- starts a new top-level [ParsedSection]; a
 *   document with no Section line at all simply has its content at the
 *   root, since a Section is an optional organizational container, not a
 *   mandatory wrapper (EXECUTION_ENGINE_SPEC.md §5.1).
 * - `Row <n>: <text>` / `Round <n>: <text>` -- a single numbered row/round.
 * - `Rows <x>-<y>: <text>` / `Rounds <x>-<y>: <text>` -- one instruction
 *   applying uniformly across an inclusive span (EXECUTION_ENGINE_SPEC.md
 *   §14.1's "Knit 10 rounds" example).
 * - `Repeat row(s)/round(s) <x>[-<y>] <n> times.` -- wraps the most
 *   recently parsed row/round content spanning exactly `<x>` to `<y>`
 *   (defaulting `<y>` to `<x>`) in a [ParsedRepeat]. This matches either a
 *   single already-parsed range node (EXECUTION_ENGINE_SPEC.md §14.3) or a
 *   contiguous run of single-row instructions (§14.2's two-row lace
 *   sequence) -- whichever the preceding lines actually produced. If the
 *   referenced span cannot be found as the trailing, contiguous content of
 *   the current Section (or document root), this becomes a [ParsingIssue]
 *   rather than a guess.
 */
object PatternTextParser {

    private val SECTION_LINE = Regex("""^Section:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val ROW_RANGE_LINE = Regex("""^(Rows?|Rounds?)\s+(\d+)\s*-\s*(\d+):\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val ROW_SINGLE_LINE = Regex("""^(Rows?|Rounds?)\s+(\d+):\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val REPEAT_LINE = Regex(
        """^Repeat\s+(Rows?|Rounds?)\s+(\d+)(?:(?:\s*-\s*|\s+and\s+)(\d+))?\s+(\d+)\s+times\.?$""",
        RegexOption.IGNORE_CASE
    )

    private data class RowMarker(val unit: String, val start: Int, val end: Int, val node: ParsedNode)

    private class Container {
        val children = mutableListOf<ParsedNode>()
        val rowMarkers = mutableListOf<RowMarker>()
    }

    fun parse(document: ExtractedDocument): ParsedPattern {
        val issues = mutableListOf<ParsingIssue>()
        val root = Container()
        var currentSection: Container? = null
        var currentSectionTitle = ""
        var currentSectionSource: SourceReference? = null

        fun closeCurrentSection() {
            val section = currentSection
            val source = currentSectionSource
            if (section != null && source != null) {
                root.children += ParsedSection(currentSectionTitle, section.children.toList(), source)
            }
            currentSection = null
        }

        fun active(): Container = currentSection ?: root

        for (line in document.lines) {
            val text = line.text
            val source = line.source

            val sectionMatch = SECTION_LINE.matchEntire(text)
            if (sectionMatch != null) {
                closeCurrentSection()
                currentSectionTitle = sectionMatch.groupValues[1].trim()
                currentSectionSource = source
                currentSection = Container()
                continue
            }

            val repeatMatch = REPEAT_LINE.matchEntire(text)
            if (repeatMatch != null) {
                parseRepeatLine(repeatMatch, text, source, active(), issues)
                continue
            }

            val rangeMatch = ROW_RANGE_LINE.matchEntire(text)
            if (rangeMatch != null) {
                parseRangeLine(rangeMatch, text, source, active(), issues)
                continue
            }

            val singleMatch = ROW_SINGLE_LINE.matchEntire(text)
            if (singleMatch != null) {
                parseSingleRowLine(singleMatch, text, source, active(), issues)
                continue
            }

            active().children += ParsedInstruction(text, source)
        }

        closeCurrentSection()
        return ParsedPattern(rootNodes = root.children.toList(), issues = issues.toList())
    }

    private fun parseRangeLine(
        match: MatchResult,
        rawText: String,
        source: SourceReference,
        container: Container,
        issues: MutableList<ParsingIssue>
    ) {
        val unit = normalizeUnit(match.groupValues[1])
        val start = match.groupValues[2].toInt()
        val end = match.groupValues[3].toInt()
        val instructionText = match.groupValues[4].trim()
        if (end < start || instructionText.isEmpty()) {
            issues += ParsingIssue("Unrecognized row/round range: \"$rawText\"", source)
            container.children += ParsedInstruction(rawText, source)
            return
        }
        val node = ParsedRange(
            unitLabel = unit,
            startInclusive = start,
            endInclusive = end,
            children = listOf(ParsedInstruction(instructionText, source)),
            source = source
        )
        container.children += node
        container.rowMarkers += RowMarker(unit, start, end, node)
    }

    private fun parseSingleRowLine(
        match: MatchResult,
        rawText: String,
        source: SourceReference,
        container: Container,
        issues: MutableList<ParsingIssue>
    ) {
        val unit = normalizeUnit(match.groupValues[1])
        val number = match.groupValues[2].toInt()
        val instructionText = match.groupValues[3].trim()
        if (instructionText.isEmpty()) {
            issues += ParsingIssue("Unrecognized row/round line: \"$rawText\"", source)
            container.children += ParsedInstruction(rawText, source)
            return
        }
        val node = ParsedInstruction(instructionText, source)
        container.children += node
        container.rowMarkers += RowMarker(unit, number, number, node)
    }

    private fun parseRepeatLine(
        match: MatchResult,
        rawText: String,
        source: SourceReference,
        container: Container,
        issues: MutableList<ParsingIssue>
    ) {
        val unit = normalizeUnit(match.groupValues[1])
        val start = match.groupValues[2].toInt()
        val end = match.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: start
        val count = match.groupValues[4].toInt()

        val resolved = if (count < 1 || end < start) {
            null
        } else {
            resolveRepeatTarget(container.rowMarkers, unit, start, end)
        }

        if (resolved == null) {
            issues += ParsingIssue("Could not find $unit $start-$end to repeat: \"$rawText\"", source)
            return
        }

        val (matchedNodes, removeCount) = resolved
        if (container.children.takeLast(removeCount) != matchedNodes) {
            // Something else was appended after the referenced rows/rounds
            // (they are no longer the trailing, contiguous content), so
            // wrapping them now would silently reorder the definition.
            issues += ParsingIssue("Could not find $unit $start-$end to repeat: \"$rawText\"", source)
            return
        }

        repeat(removeCount) { container.children.removeAt(container.children.size - 1) }
        repeat(removeCount) { container.rowMarkers.removeAt(container.rowMarkers.size - 1) }
        container.children += ParsedRepeat(count = count, children = matchedNodes, source = source)
    }

    /**
     * Finds the content that a `Repeat <unit> <start>-<end> <count> times`
     * line refers to: either one existing range node spanning exactly
     * `start..end`, or a contiguous run of single-value markers (one per
     * row/round) covering `start..end` in order. Returns the matched nodes
     * (in original order) and how many trailing markers they occupy, or
     * `null` when no such content exists.
     */
    private fun resolveRepeatTarget(
        rowMarkers: List<RowMarker>,
        unit: String,
        start: Int,
        end: Int
    ): Pair<List<ParsedNode>, Int>? {
        val last = rowMarkers.lastOrNull()
        if (last != null && last.unit == unit && last.start == start && last.end == end) {
            return listOf(last.node) to 1
        }

        val span = end - start + 1
        if (span < 2 || span > rowMarkers.size) return null
        val trailing = rowMarkers.takeLast(span)
        val matches = trailing.withIndex().all { (index, marker) ->
            marker.unit == unit && marker.start == start + index && marker.end == start + index
        }
        return if (matches) trailing.map { it.node } to span else null
    }

    private fun normalizeUnit(rawUnit: String): String = rawUnit.lowercase().removeSuffix("s")
}
