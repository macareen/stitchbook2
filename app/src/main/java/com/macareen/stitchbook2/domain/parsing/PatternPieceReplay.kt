package com.macareen.stitchbook2.domain.parsing

/** Conservative expansion of explicitly identical pieces; never infers mirrored shaping. */
internal object PatternPieceReplay {
    private const val PIECE = "(sleeves?|socks?|mittens?|gloves?|booties?|cuffs?|panels?|legs?|arms?|ears?)"
    private val heading = Regex("""^(?:(?:first|second|left|right|other)\s+)?$PIECE(?:\s+[12])?(?:\s*\(make\s+(\d+)\))?$""", RegexOption.IGNORE_CASE)
    private val repeat = Regex("""^(?:repeat(?:\s+(?:the\s+)?instructions)?\s+for\s+(?:the\s+)?(?:second|other)\s+$PIECE|(?:work|make)\s+(?:the\s+)?(?:second|other)\s+$PIECE\s+(?:as\s+(?:for\s+)?(?:the\s+)?first|identically|(?:in\s+)?the\s+same\s+(?:way|manner)))\.?$""", RegexOption.IGNORE_CASE)
    private val reference = Regex("""\b(?:second|other|another|opposite|remaining)\s+$PIECE\b""", RegexOption.IGNORE_CASE)
    private val action = Regex("""\b(?:repeat|work|make|same|as for)\b""", RegexOption.IGNORE_CASE)
    private val implicitReference = Regex("""\b(?:as\s+(?:for\s+)?(?:the\s+)?first|revers(?:e|ing)\s+(?:(?:all|the)\s+)?shaping)\b""", RegexOption.IGNORE_CASE)

    fun isPieceHeading(text: String): Boolean = heading.matches(text.trim().removeSuffix(":"))
    fun startsReference(text: String): Boolean = reference.containsMatchIn(text) && action.containsMatchIn(text) || implicitReference.containsMatchIn(text)
    private fun key(text: String): String? = heading.matchEntire(text.trim().removeSuffix(":"))?.groupValues?.get(1)?.lowercase()?.let {
        when (it) { "booties" -> "bootie"; "ears" -> "ear"; else -> it.removeSuffix("s") }
    }

    fun resolve(pattern: ParsedPattern): ParsedPattern {
        val issues = pattern.issues.toMutableList()
        val completed = mutableListOf<ParsedSection>()
        val expanded = mutableSetOf<SourceReference>()
        fun needsReview(node: ParsedInstruction) {
            issues += ParsingIssue("This piece refers to earlier work. Use the draft's section-instructions action to choose and review the complete steps; check changes to shaping, side or length: ${node.text}", node.source)
        }
        fun section(original: ParsedSection): ParsedSection {
            val countText = heading.matchEntire(original.title)?.groupValues?.get(2)?.takeIf(String::isNotEmpty)
            val count = countText?.let { it.toIntOrNull() ?: -1 }
            val children = mutableListOf<ParsedNode>()
            var replayed = false
            var hasReference = false
            original.children.forEach { node ->
                if (node is ParsedInstruction && startsReference(node.text)) {
                    val earlierReference = hasReference
                    hasReference = true
                    val match = repeat.matchEntire(node.text)
                    val noun = match?.groupValues?.drop(1)?.firstOrNull(String::isNotEmpty)?.let(::key)
                    val prior = completed.filter { key(it.title) == noun && it.source !in expanded }
                    val source = when {
                        match == null || earlierReference || replayed || count != null && count != 2 -> null
                        noun == key(original.title) && children.isNotEmpty() &&
                            !Regex("""^(?:second|other)\b""", RegexOption.IGNORE_CASE).containsMatchIn(original.title) -> children.toList()
                        children.isNotEmpty() && noun == key(original.title) -> null
                        prior.size == 1 -> prior.single().children
                        else -> null
                    }
                    if (source.isNullOrEmpty()) { needsReview(node); children += node }
                    else {
                        children += ParsedSection(node.text, source, node.source)
                        replayed = true
                    }
                } else children += node
            }
            val result = when {
                count != null && !hasReference && count in 2..100 && children.isNotEmpty() -> {
                    expanded += original.source
                    original.copy(children = listOf(ParsedRepeat(count, children.toList(), original.source, "Piece")))
                }
                count != null && !hasReference && count !in 1..100 -> {
                    issues += ParsingIssue("Review the number of pieces in '${original.title}'; instructions have not been multiplied.", original.source)
                    original.copy(children = children)
                }
                else -> original.copy(children = children)
            }
            if (hasReference) expanded += original.source
            return result
        }
        val roots = pattern.rootNodes.map { node ->
            when (node) {
                is ParsedSection -> section(node).also { completed += it }
                is ParsedInstruction -> node.also { if (startsReference(it.text)) needsReview(it) }
                else -> node
            }
        }
        return pattern.copy(rootNodes = roots, issues = issues)
    }
}
