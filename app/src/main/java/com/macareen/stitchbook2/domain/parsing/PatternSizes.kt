package com.macareen.stitchbook2.domain.parsing

/**
 * Reads a pattern written for several sizes at once and keeps one size's
 * numbers. Patterns give the first size outside brackets and the rest inside,
 * in the order of the size list: "Sizes: S (M, L)" with "Cast on 60 (66, 72)
 * sts" means 66 stitches for M.
 *
 * Only a group with exactly one value per size is resolved, so a stitch count
 * such as "(24 sts)" or a side such as "(RS)" is never mistaken for one. Any
 * other text is kept word for word.
 */
object PatternSizes {

    private val SIZES_LINE = Regex("""^(?:finished\s+)?sizes?\s*[:\-–—]?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val GROUPED_LABELS = Regex("""^([^()\[\],;]+?)\s*[(\[]([^)\]]+)[)\]]""")

    private val MEASUREMENT_LINE = Regex(
        """^(?:finished\s+)?(?:bust(?:\s+circumference)?|chest(?:\s+circumference)?|circumference|length|measurements?|designed to fit)\s*[:\-\u2013\u2014]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val MEASUREMENT_VALUES = Regex("""^([\d\s.,/()\[\];-]+?)\s*(?:cm|in(?:ches)?|\")(?:\s|$)""", RegexOption.IGNORE_CASE)

    // A count, a decimal, a mixed or plain fraction, or a dash for "not in this size".
    private const val VALUE = """(?:\d+/\d+|\d+(?:\.\d+)?(?:\s\d+/\d+)?|[-–—])"""

    private val SIZE_HEADING = Regex("""^(?:finished\s+)?sizes?\s*[:\-\u2013\u2014]?\s*$""", RegexOption.IGNORE_CASE)
    private const val LABEL = """(?:\d*X*[SML]|$VALUE)"""
    private val ALTERNATING_LABELS = Regex("""^($LABEL(?:\s*[(\[]\s*$LABEL\s*[)\]]|\s+$LABEL)+)(?:\s*(?:cm|in(?:ches)?|[\"])?\s*)$""", RegexOption.IGNORE_CASE)

    /** Labels in grouped, alternating, comma or slash notation; measurement units are not labels. */
    fun labelsFrom(text: String): List<String> {
        val trimmed = MEASUREMENT_VALUES.find(text.trim())?.groupValues?.get(1)?.trim() ?: text.trim()
        val alternating = ALTERNATING_LABELS.matchEntire(trimmed)
        val grouped = GROUPED_LABELS.matchEntire(trimmed)
        val labels = when {
            alternating != null -> Regex(LABEL, RegexOption.IGNORE_CASE).findAll(alternating.groupValues[1]).map { it.value }.toList()
            grouped != null -> listOf(grouped.groupValues[1]) + grouped.groupValues[2].split(',', ';')
            else -> trimmed.split(',', '/', ';')
        }.map { it.trim() }.filter { it.isNotEmpty() }
        return if (labels.size >= 2) labels else emptyList()
    }

    /** Prefer letter sizes to measurements, retaining the source of the actual list. */
    fun findInDocument(document: ExtractedDocument): Pair<List<String>, ExtractedLine>? {
        val candidates = document.lines.mapIndexedNotNull { index, line ->
            val inline = SIZES_LINE.matchEntire(line.text.trim()) ?: MEASUREMENT_LINE.matchEntire(line.text.trim())
            val source = if (SIZE_HEADING.matches(line.text.trim())) document.lines.getOrNull(index + 1) else line
            val text = if (source != line) source?.text else inline?.groupValues?.get(1)
            text?.let(::labelsFrom)?.takeIf { it.isNotEmpty() }?.let { it to requireNotNull(source) }
        }
        return candidates.firstOrNull { (labels, _) -> labels.all { it.any(Char::isLetter) } }
            ?: candidates.firstOrNull()
    }

    /** Where [label] sits in [labels], ignoring case and spacing, or null when it isn't one of them. */
    fun indexOf(label: String, labels: List<String>): Int? {
        val wanted = label.normalized()
        return labels.indexOfFirst { it.normalized() == wanted }.takeIf { it >= 0 }
    }

    /** [text] with every per-size group of [sizeCount] values replaced by the value at [sizeIndex]. */
    fun resolve(text: String, sizeIndex: Int, sizeCount: Int): String {
        if (sizeCount < 2 || sizeIndex !in 0 until sizeCount) return text
        // Match maximal runs before grouped lists, so a longer run is never partly
        // resolved as several two-size groups. Plain numbers and prose stay intact.
        val run = """($VALUE(?:\s*[(\[]\s*$VALUE\s*[)\]]|\s+$VALUE)+)"""
        val pattern = Regex("""(?<![\d./])$run|${groupPattern(sizeCount).pattern}""")
        return pattern.replace(text) { match ->
            val alternating = match.groupValues[1]
            if (alternating.isNotEmpty()) {
                val values = Regex(VALUE).findAll(alternating).map { it.value }.toList()
                if (('(' in alternating || '[' in alternating) && values.size == sizeCount) values[sizeIndex] else match.value
            } else {
                val values = listOf(match.groupValues[2]) + match.groupValues[3].split(',')
                values[sizeIndex].trim()
            }
        }
    }

    /** [document] with every line resolved for one size; sources are unchanged. */
    fun forSize(document: ExtractedDocument, sizeIndex: Int, sizeCount: Int): ExtractedDocument =
        document.copy(
            lines = document.lines.map { line -> line.copy(text = resolve(line.text, sizeIndex, sizeCount)) }
        )

    private fun groupPattern(sizeCount: Int): Regex {
        val others = List(sizeCount - 1) { VALUE }.joinToString("""\s*,\s*""")
        // The lookbehind keeps "k2 (3, 4)" whole but never starts inside a longer number.
        return Regex("""(?<![\d.])($VALUE)\s*[(\[]\s*($others)\s*[)\]]""")
    }

    private fun String.normalized() = trim().lowercase().replace(Regex("""\s+"""), " ")
}
