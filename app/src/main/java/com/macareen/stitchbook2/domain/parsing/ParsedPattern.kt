package com.macareen.stitchbook2.domain.parsing

/**
 * The output of deterministic text parsing (see [PatternTextParser]), before
 * it is reviewed and mapped into a `GuideDraft` -- a distinct, later mapping
 * step (ROADMAP.md's "Parser foundation" item) rather than something this
 * model does itself. Every node retains the [SourceReference] of the line it
 * came from, per PRODUCT_SPEC.md 6.6's "show source references for
 * generated steps" requirement.
 */
sealed interface ParsedNode {
    val source: SourceReference
}

data class ParsedSection(
    val title: String,
    val children: List<ParsedNode>,
    override val source: SourceReference
) : ParsedNode {
    init {
        require(title.isNotBlank()) { "Section title must not be blank." }
    }
}

/** A row/round span, e.g. "Rounds 1-10: Knit all stitches." -> rounds 1 through 10. */
data class ParsedRange(
    val unitLabel: String,
    val startInclusive: Int,
    val endInclusive: Int,
    val children: List<ParsedNode>,
    override val source: SourceReference
) : ParsedNode {
    init {
        require(unitLabel.isNotBlank()) { "Range unit label must not be blank." }
        require(startInclusive >= 1) { "Range start must be 1 or greater." }
        require(endInclusive >= startInclusive) { "Range end must not be before its start." }
    }
}

data class ParsedRepeat(
    val count: Int,
    val children: List<ParsedNode>,
    override val source: SourceReference
) : ParsedNode {
    init {
        require(count >= 1) { "Repeat count must be 1 or greater." }
    }
}

data class ParsedInstruction(
    val text: String,
    override val source: SourceReference
) : ParsedNode {
    init {
        require(text.isNotBlank()) { "Instruction text must not be blank." }
    }
}

/**
 * A line the parser could not confidently interpret. Ambiguity is surfaced,
 * never guessed away, per PRODUCT_SPEC.md 6.6 and ROADMAP.md Phase 12's
 * "unsupported documents fail safely without inventing instructions."
 */
data class ParsingIssue(val message: String, val source: SourceReference)

data class ParsedPattern(val rootNodes: List<ParsedNode>, val issues: List<ParsingIssue>)
