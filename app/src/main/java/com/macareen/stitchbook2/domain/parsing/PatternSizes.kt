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

    // A count, a decimal, a mixed or plain fraction, or a dash for "not in this size".
    private const val VALUE = """(?:\d+(?:\.\d+)?(?:\s\d+/\d+)?|\d+/\d+|[-–—])"""

    /** Size names from text such as "XS (S, M, L)", "S, M, L" or "S/M/L"; empty unless there are two or more. */
    fun labelsFrom(text: String): List<String> {
        val trimmed = text.trim()
        val grouped = GROUPED_LABELS.find(trimmed)
        val labels = if (grouped != null) {
            listOf(grouped.groupValues[1]) + grouped.groupValues[2].split(',', ';')
        } else {
            trimmed.split(',', '/', ';')
        }.map { it.trim() }.filter { it.isNotEmpty() }
        return if (labels.size >= 2) labels else emptyList()
    }

    /** The size list from the pattern's own "Sizes:" line, with that line, or null when there is none. */
    fun findInDocument(document: ExtractedDocument): Pair<List<String>, ExtractedLine>? =
        document.lines.firstNotNullOfOrNull { line ->
            SIZES_LINE.matchEntire(line.text.trim())
                ?.let { labelsFrom(it.groupValues[1]) }
                ?.takeIf { it.isNotEmpty() }
                ?.let { it to line }
        }

    /** Where [label] sits in [labels], ignoring case and spacing, or null when it isn't one of them. */
    fun indexOf(label: String, labels: List<String>): Int? {
        val wanted = label.normalized()
        return labels.indexOfFirst { it.normalized() == wanted }.takeIf { it >= 0 }
    }

    /** [text] with every per-size group of [sizeCount] values replaced by the value at [sizeIndex]. */
    fun resolve(text: String, sizeIndex: Int, sizeCount: Int): String {
        if (sizeCount < 2 || sizeIndex !in 0 until sizeCount) return text
        return groupPattern(sizeCount).replace(text) { match ->
            val values = listOf(match.groupValues[1]) + match.groupValues[2].split(',')
            values[sizeIndex].trim()
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
