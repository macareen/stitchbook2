package com.macareen.stitchbook2.domain.parsing

/**
 * Reads a pattern written for several sizes at once and keeps one size's
 * numbers. Patterns give the first size outside brackets and the rest inside,
 * in the order of the size list: "Sizes: S (M, L)" with "Cast on 60 (66, 72)
 * sts" means 66 stitches for M.
 *
 * Groups resolve when they have one value per size, or an oversized list whose
 * values all agree. This preserves ordinary annotations, so a stitch count
 * such as "(24 sts)" or a side such as "(RS)" is never mistaken for one. Any
 * other text is kept word for word.
 */
object PatternSizes {

    private val SIZES_LINE = Regex("""^(?:finished\s+)?sizes?\s*[:\-–—]?\s*(.+)$""", RegexOption.IGNORE_CASE)

    private val MEASUREMENT_LINE = Regex(
        """^(?:finished\s+)?(?:bust(?:\s+circumference)?|chest(?:\s+circumference)?|circumference|length|measurements?|designed to fit)\s*[:\-\u2013\u2014]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val MEASUREMENT_VALUES = Regex("""^([\d\s.,/()\[\];-]+?)\s*(?:cm|in(?:ches)?|\")(?:\s|$)""", RegexOption.IGNORE_CASE)

    // A count, a decimal, a mixed or plain fraction, or a dash for "not in this size".
    private const val VALUE = """(?:\d+/\d+|\d+(?:\.\d+)?(?:\s\d+/\d+|[¼½¾⅛⅜⅝⅞])?(?:st|nd|rd|th)?|[-–—])"""

    private val SIZE_HEADING = Regex("""^(?:finished\s+)?sizes?\s*[:\-\u2013\u2014]?\s*$""", RegexOption.IGNORE_CASE)
    private const val LABEL = """(?:\d+X|\d*X*[SML]|[A-Z]|$VALUE)"""
    private val ALTERNATING_LABELS = Regex("""^($LABEL(?:\s*[(\[]\s*$LABEL\s*[)\]]|\s+$LABEL)+)(?:\s*(?:cm|in(?:ches)?|[\"])?\s*)$""", RegexOption.IGNORE_CASE)

    private val VALUE_TOKEN = Regex(VALUE, RegexOption.IGNORE_CASE)
    private val SINGLE_VALUE = Regex("^$VALUE$")
    private val LABEL_TOKEN = Regex(LABEL, RegexOption.IGNORE_CASE)
    private val NUMERIC_RUN: Regex = run {
        val inside = "$VALUE(?:\\s*[,;]\\s*$VALUE)*"
        val group = "(?:\\(\\s*$inside\\s*\\)|\\[\\s*$inside\\s*\\])"
        Regex("(?<![\\d./(])($VALUE(?:\\s*[,;]?\\s*$group|\\s*[,;]\\s+$VALUE|\\s+$VALUE)+)", RegexOption.IGNORE_CASE)
    }

    private val boundedLabel = Regex("($LABEL)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)
    private val canonicalLabel = Regex("^$LABEL$", RegexOption.IGNORE_CASE)
    private val annotatedSize = Regex("""(?i)(?<![\p{L}\d])(\d*X*[SML])\s*\([^)]*(?:cm|inches?|[”\"])[^)]*\)""")

    /** Trim prose after a complete size declaration; never consume an incomplete annotation. */
    private fun declarationPrefix(text: String): String {
        var end = 0
        var count = 0
        while (end < text.length) {
            val token = boundedLabel.find(text, end)?.takeIf { it.range.first == end }
            if (token != null) { end = token.range.last + 1; count++ }
            else if (text[end].isWhitespace() || text[end] in "()[],;/") end++
            else break
        }
        val prefix = text.take(end).trim()
        return if (count >= 2 && prefix.count { it == '(' } == prefix.count { it == ')' } && prefix.count { it == '[' } == prefix.count { it == ']' }) prefix else text
    }

    /** Labels in grouped, alternating, comma or slash notation; measurement units are not labels. */
    fun labelsFrom(text: String): List<String> {
        val annotated = annotatedSize.findAll(text).map { it.groupValues[1] }.toList()
        if (annotated.size >= 2) return annotated
        val trimmed = declarationPrefix(MEASUREMENT_VALUES.find(text.trim())?.groupValues?.get(1)?.trim() ?: text.trim())
        val flattened = StringBuilder()
        var closing: Char? = null
        for (character in trimmed) {
            when (character) {
                '(', '[' -> {
                    if (closing != null) return emptyList()
                    closing = if (character == '(') ')' else ']'
                    flattened.append(',')
                }
                ')', ']' -> {
                    if (closing != character) return emptyList()
                    closing = null
                    flattened.append(',')
                }
                else -> flattened.append(character)
            }
        }
        if (closing != null) return emptyList()
        val labels = flattened.toString().split(',', '/', ';').map(String::trim)
            .filter(String::isNotEmpty).flatMap { segment ->
                // A mixed fraction is one measurement, not two size labels.
                val alternating = if (SINGLE_VALUE.matches(segment)) null
                    else ALTERNATING_LABELS.matchEntire(segment)
                alternating?.let { LABEL_TOKEN
                    .findAll(it.groupValues[1]).map { match -> match.value }.toList() } ?: listOf(segment)
            }
        return if (labels.size >= 2) labels else emptyList()
    }

    /** Prefer letter sizes to measurements, retaining the source of the actual list. */
    fun findInDocument(document: ExtractedDocument): Pair<List<String>, ExtractedLine>? {
        val candidates = document.lines.mapIndexedNotNull { index, line ->
            val inline = SIZES_LINE.matchEntire(line.text.trim()) ?: MEASUREMENT_LINE.matchEntire(line.text.trim())
            if (SIZE_HEADING.matches(line.text.trim())) {
                document.lines.drop(index + 1).take(8)
                    .takeWhile { !Regex("""^(?:abbreviations?|materials?|gauge|pattern|instructions?|needles?|hooks?)\s*:?\s*$""", RegexOption.IGNORE_CASE).matches(it.text.trim()) }
                    .mapNotNull { source -> labelsFrom(source.text).takeIf { labels -> labels.isNotEmpty() && labels.all(canonicalLabel::matches) }?.let { it to source } }
                    .firstOrNull()
            } else inline?.groupValues?.get(1)?.let(::labelsFrom)?.takeIf { it.isNotEmpty() }?.let { it to line }
        }
        val canonical = candidates.filter { (labels, _) -> labels.all(canonicalLabel::matches) }
        return canonical.firstOrNull { (labels, _) -> labels.all { it.any(Char::isLetter) } }
            ?: canonical.firstOrNull() ?: candidates.firstOrNull()
    }

    /** Where [label] sits in [labels], ignoring case and spacing, or null when it isn't one of them. */
    fun indexOf(label: String, labels: List<String>): Int? {
        val wanted = label.normalized()
        return labels.indexOfFirst { it.normalized() == wanted }.takeIf { it >= 0 }
    }

    /** [text] with every per-size group of [sizeCount] values replaced by the value at [sizeIndex]. */
    fun resolve(text: String, sizeIndex: Int, sizeCount: Int): String {
        if (sizeCount < 2 || sizeIndex !in 0 until sizeCount) return text
        // Match a complete numeric sequence across any number of adjacent groups.
        // A longer sequence cannot be resolved as just its first bracketed group.
        return NUMERIC_RUN.replace(text) { match ->
            // A numbered marker immediately before the values is context, not a size.
            val marker = Regex("""(?i)\bmarker\s*$""").containsMatchIn(text.substring(0, match.range.first))
            val leading = if (marker) Regex("""^\d+\s+""").find(match.value)?.value.orEmpty() else ""
            val sequence = match.value.removePrefix(leading)
            val values = VALUE_TOKEN.findAll(sequence).map { it.value }.toList()
            // Some PDFs contain an extra repeated alternative. If every value agrees,
            // the selected result is invariant; differing oversized runs stay untouched.
            val invariant = values.size > sizeCount && values.distinct().size == 1
            if (('(' in match.value || '[' in match.value) && (values.size == sizeCount || invariant)) {
                leading + values[sizeIndex]
            } else match.value
        }
    }

    /** Long grouped alternatives left in an executable instruction require explicit size review. */
    fun unresolvedIssues(pattern: ParsedPattern, sizeCount: Int): List<ParsingIssue> {
        if (sizeCount < 2) return emptyList()
        fun visit(nodes: List<ParsedNode>): List<ParsingIssue> = nodes.flatMap { node ->
            when (node) {
                is ParsedInstruction -> {
                    val ambiguous = NUMERIC_RUN.findAll(node.text).any { match ->
                        val values = VALUE_TOKEN.findAll(match.value).map { it.value }.toList()
                        ('(' in match.value || '[' in match.value) && values.size >= 4 && values.size != sizeCount && !(values.size > sizeCount && values.distinct().size == 1)
                    }
                    if (ambiguous) listOf(ParsingIssue("Check this instruction's unresolved size numbers in the original pattern before following it.", node.source)) else emptyList()
                }
                is ParsedSection -> visit(node.children)
                is ParsedRange -> visit(node.children)
                is ParsedRepeat -> visit(node.children)
            }
        }
        return visit(pattern.rootNodes).distinctBy { it.source }
    }

    /** [document] with every line resolved for one size; sources are unchanged. */
    fun forSize(document: ExtractedDocument, sizeIndex: Int, sizeCount: Int): ExtractedDocument =
        document.copy(
            lines = document.lines.map { line -> line.copy(text = resolve(line.text, sizeIndex, sizeCount)) }
        )

    private fun String.normalized() = trim().lowercase().replace(Regex("""\s+"""), " ")
}
