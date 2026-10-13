package com.macareen.stitchbook2.domain.parsing

/**
 * Selects explicit size-only prose before building one size's draft. Inline selectors apply
 * to that line; standalone selectors apply only within a block closed by "All sizes".
 * Unknown selectors or unbounded blocks are retained for review, never silently discarded.
 */
object SizeSpecificInstructions {
    data class Resolution(val document: ExtractedDocument, val issues: List<ParsingIssue>)
    private data class Selector(val indices: Set<Int>?, val body: String)
    private val options = setOf(RegexOption.IGNORE_CASE)
    private val explicit = Regex("""^(?:for\s+)?sizes?\s+(.+?)(?::\s*(.*))?$""", options)
    private const val LABEL = """(?:\d+X|\d*X*[SML]|\d+(?:\.\d+)?)"""
    private val bare = Regex("""^($LABEL(?:\s*(?:,|/|&|and|or|[-\u2013\u2014])\s*$LABEL)*)\s+only\s*(?::\s*(.*))?$""", options)
    private val all = Regex("""^(?:for\s+)?all\s+sizes?\s*(?::\s*(.*))?$""", options)
    private val informational = Regex("""^sizes?\s+(?:guide|chart|information|notes|selection)\s*:?$""", options)
    private val only = Regex("""\s+only$""", options)
    private val separator = Regex("""\s*(?:,|/|&|\band\b|\bor\b)\s*""", options)
    private val compactNames = Regex("""^[\p{L}\d]+(?:\s*(?:,|/|&|and|or|[-\u2013\u2014])\s*[\p{L}\d]+)*$""", options)
    private val range = Regex("""^(.+?)\s*[-\u2013\u2014]\s*(.+)$""")

    fun select(document: ExtractedDocument, labels: List<String>, sizeIndex: Int): Resolution {
        if (sizeIndex !in labels.indices) return Resolution(document, document.lines.firstOrNull()?.let {
            listOf(ParsingIssue("The selected size is unknown; size-only instructions are kept for review.", it.source))
        }.orEmpty())
        val kept = mutableListOf<ExtractedLine>()
        val issues = mutableListOf<ParsingIssue>()
        val protectedSources = mutableSetOf<SourceReference>()
        val lines = document.lines
        var index = 0
        fun keepBody(line: ExtractedLine, body: String) {
            if (body.isNotBlank()) kept += line.copy(text = body.trim())
        }
        fun unknown(line: ExtractedLine) {
            issues += ParsingIssue("This size-only selector does not match the pattern's size list; its text is kept for review.", line.source)
        }
        while (index < lines.size) {
            val line = lines[index]
            val common = all.matchEntire(line.text.trim())
            if (common != null) {
                keepBody(line, common.groupValues[1])
                index++
                continue
            }
            val selector = selector(line.text, labels)
            when {
                selector == null -> kept += line
                selector.body.isNotBlank() -> if (selector.indices == null) {
                    kept += line
                    protectedSources += line.source
                    unknown(line)
                } else if (sizeIndex in selector.indices) keepBody(line, selector.body)
                else -> {
                    val end = (index + 1 until lines.size).firstOrNull { all.matches(lines[it].text.trim()) }
                    if (end == null) {
                        val remaining = lines.drop(index)
                        kept += remaining
                        protectedSources += remaining.map { it.source }
                        issues += ParsingIssue("This size-only block has no All sizes ending; the following work is kept for review.", line.source)
                        break
                    } else {
                        val block = lines.subList(index, end)
                        val unknownLines = block.filter { member ->
                            selector(member.text, labels)?.let { it.indices == null } == true
                        }
                        if (unknownLines.isNotEmpty()) {
                            kept += block
                            protectedSources += block.map { it.source }
                            unknownLines.forEach(::unknown)
                        } else {
                            var applies = sizeIndex in requireNotNull(selector.indices)
                            block.drop(1).forEach { member ->
                                val branch = selector(member.text, labels)
                                if (branch == null) {
                                    if (applies) kept += member
                                } else if (branch.body.isBlank()) {
                                    applies = sizeIndex in requireNotNull(branch.indices)
                                } else if (applies && sizeIndex in requireNotNull(branch.indices)) {
                                    keepBody(member, branch.body)
                                }
                            }
                        }
                        index = end
                        continue
                    }
                }
            }
            index++
        }
        val resolved = mutableListOf<ExtractedLine>()
        val pending = mutableListOf<ExtractedLine>()
        fun flush() {
            val joined = PatternTextCleanup.clean(document.copy(lines = pending.toList()), PatternTextParser::startsNewLine) {
                PatternTextParser.headingTitle(it) != null || PatternMetadataSplitter.standsAlone(it)
            }
            resolved += joined.map { it.copy(text = PatternSizes.resolve(it.text, sizeIndex, labels.size)) }
            pending.clear()
        }
        for (line in kept) {
            if (line.source in protectedSources) {
                flush()
                resolved += line
            } else pending += line
        }
        flush()
        val selected = document.copy(lines = resolved)
        return Resolution(selected, issues)
    }

    private fun selector(text: String, labels: List<String>): Selector? {
        if (informational.matches(text.trim())) return null
        val match = explicit.matchEntire(text.trim()) ?: bare.matchEntire(text.trim()) ?: return null
        val names = match.groupValues[1].replace(only, "").trim()
        val exact = PatternSizes.indexOf(names, labels)
        // A sentence such as "size S sweater has a ..." describes fit, not a branch.
        // Unknown compact labels and explicit short colon/only selectors still require review.
        if (exact == null && !only.containsMatchIn(match.groupValues[1]) &&
            !compactNames.matches(names) && !(':' in text && names.length <= 60)) return null
        if (exact != null) return Selector(setOf(exact), match.groupValues[2])
        val indices = mutableSetOf<Int>()
        for (part in names.split(separator)) {
            val single = PatternSizes.indexOf(part, labels)
            if (single != null) indices += single else {
                val span = range.matchEntire(part) ?: return Selector(null, match.groupValues[2])
                val start = PatternSizes.indexOf(span.groupValues[1], labels) ?: return Selector(null, match.groupValues[2])
                val end = PatternSizes.indexOf(span.groupValues[2], labels) ?: return Selector(null, match.groupValues[2])
                if (end < start) return Selector(null, match.groupValues[2])
                indices += start..end
            }
        }
        return Selector(indices.takeIf { it.isNotEmpty() }, match.groupValues[2])
    }
}
