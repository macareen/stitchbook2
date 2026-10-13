package com.macareen.stitchbook2.domain.parsing

/**
 * Undoes what PDF layout does to pattern text before parsing: removes page
 * furniture (page numbers, and headers or footers repeated on several
 * pages) and rejoins sentences a PDF wrapped across lines. Each kept line
 * keeps the [SourceReference] of where it starts.
 *
 * Removal only looks at the first and last lines of each page, and only
 * drops bare page numbers or text repeated there on at least half the
 * pages; everything else stays, so nothing a person needs is dropped.
 */
object PatternTextCleanup {

    private val PAGE_NUMBER = Regex("""^(page\s*)?\d{1,3}(\s*(of|/)\s*\d{1,3})?$""", RegexOption.IGNORE_CASE)
    private val ENDS_A_THOUGHT = Regex("""[.:;!?)\]]$""")
    private val RULE = Regex("""^(?:_{3,}|[-\u2013\u2014=]{3,})$""")
    private val NUMERIC_TAIL = Regex("""\d[\d\s.,;/()\[\]\u2013\u2014-]*[\d)\]]\s*$""")
    private val DANGLING_WORD = Regex("""\b(?:slip|before|after|around|to|of|the|a|an|at|with|on|in|from|over|under|through|by)$""", RegexOption.IGNORE_CASE)
    private val NUMERIC_START = Regex("""^(?:\d|[(\[]\s*\d)""")
    private const val MAX_FURNITURE_LENGTH = 100
    private const val EDGE_LINES = 2

    /**
     * [startsNewLine] marks lines that begin their own step (rows, repeats,
     * headings, bullets); [standsAlone] marks lines nothing may be joined
     * onto, such as headings.
     */
    fun clean(
        document: ExtractedDocument,
        startsNewLine: (String) -> Boolean,
        standsAlone: (String) -> Boolean
    ): List<ExtractedLine> {
        val edges = edgeLines(document)
        val furniture = repeatedAcrossPages(document, edges, startsNewLine)
        val kept = document.lines.filterNot { line ->
            val text = line.text.trim()
            text.isEmpty() || RULE.matches(text) ||
                line.source in edges && (PAGE_NUMBER.matches(text) || !startsNewLine(text) && furnitureKey(text) in furniture)
        }

        val joined = mutableListOf<ExtractedLine>()
        for (line in kept) {
            val text = line.text.trim()
            val previous = joined.lastOrNull()
            if (previous != null && !standsAlone(previous.text) && continues(previous.text, text, startsNewLine)) {
                joined[joined.lastIndex] = previous.copy(text = join(previous.text, text))
            } else {
                joined += line.copy(text = text)
            }
        }
        return joined
    }

    /** True when [next] carries on the sentence [previous] started. */
    private fun continues(previous: String, next: String, startsNewLine: (String) -> Boolean): Boolean {
        if (startsNewLine(next)) return false
        if (PatternTextParser.isRowLabelOnly(previous)) return true
        val tail = NUMERIC_TAIL.find(previous)?.value.orEmpty()
        if (('(' in tail || '[' in tail) && NUMERIC_START.containsMatchIn(next)) return true
        if (ENDS_A_THOUGHT.containsMatchIn(previous)) return false
        // A wrapped sentence resumes in lower case, with a digit, or after a hyphen or comma.
        val first = next.first()
        if (first.isUpperCase() && DANGLING_WORD.containsMatchIn(previous)) return true
        return previous.endsWith("-") || previous.endsWith(",") || first.isLowerCase() || first.isDigit() || first == '('
    }

    private fun join(previous: String, next: String): String =
        if (previous.endsWith("-") && previous.length > 1 && previous[previous.length - 2].isLetter() && next.first().isLowerCase()) {
            previous.dropLast(1) + next
        } else {
            "$previous $next"
        }

    /**
     * Header/footer text: at the top or bottom edge of its page, on at least
     * half the pages (and at least two). Lines that look like pattern
     * structure never count, so two identical rows on different pages stay.
     */
    private fun repeatedAcrossPages(
        document: ExtractedDocument,
        edges: Set<SourceReference>,
        startsNewLine: (String) -> Boolean
    ): Set<String> {
        if (document.pageCount < 2) return emptySet()
        val needed = maxOf(2, (document.pageCount + 1) / 2)
        return document.lines
            .filter { it.source in edges }
            .filter { it.text.length <= MAX_FURNITURE_LENGTH && !startsNewLine(it.text.trim()) }
            .groupBy { furnitureKey(it.text) }
            .filter { (key, lines) -> key.isNotEmpty() && lines.map { it.source.pageNumber }.distinct().size >= needed }
            .keys
    }

    /** The first and last [EDGE_LINES] lines of each page, where headers, footers, and page numbers sit. */
    private fun edgeLines(document: ExtractedDocument): Set<SourceReference> =
        document.lines.groupBy { it.source.pageNumber }.values.flatMap { page ->
            page.take(EDGE_LINES) + page.takeLast(EDGE_LINES)
        }.map { it.source }.toSet()

    /** Headers often differ only by their page number ("Cosy Hat - page 2"). */
    private fun furnitureKey(text: String): String =
        text.lowercase().replace(Regex("""\d+"""), "#").replace(Regex("""\s+"""), " ").trim()
}
