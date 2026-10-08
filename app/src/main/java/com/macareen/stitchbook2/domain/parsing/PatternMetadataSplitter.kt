package com.macareen.stitchbook2.domain.parsing

/**
 * What a pattern says about itself, as opposed to how to make it: the intro
 * paragraphs, designer, gauge, materials and tools. Each field is the
 * pattern's own wording, kept whole, so the user can read and edit it.
 */
data class PatternDetails(
    val description: String? = null,
    val designer: String? = null,
    val gauge: String? = null,
    val yarn: String? = null,
    val tools: String? = null,
    val sizes: String? = null,
    /** Total yarn needed in yards, when the materials state it in yards or metres. */
    val yardage: Double? = null
) {
    val isEmpty: Boolean
        get() = description == null && designer == null && gauge == null && yarn == null &&
            tools == null && sizes == null && yardage == null
}

/** [details] about the pattern, and the [instructions] that become steps. */
data class SplitPattern(val details: PatternDetails, val instructions: List<ExtractedLine>)

/**
 * Separates a pattern's front matter from its instructions. Patterns open
 * with an introduction, a byline, and blocks such as "Materials", "Gauge" or
 * "Abbreviations" before the first instruction; those lines describe the
 * pattern and must never become steps.
 *
 * Rules, in order, for each cleaned line:
 * - Copyright, web and social lines are dropped.
 * - A heading naming a details block ("Materials", "Gauge", "Needles",
 *   "Abbreviations", "Notes" ...) sends the lines under it to that block,
 *   until the next heading.
 * - A "Label: value" line for a details field ("Gauge: 22 sts = 10 cm") fills
 *   that field wherever it appears.
 * - Instructions begin at the first row/round line, a heading that is not a
 *   details block, or a line starting with a cast-on or chain.
 * - Before that, other prose is the description, and "by Name" lines name the
 *   designer.
 */
object PatternMetadataSplitter {

    private enum class Block { DESCRIPTION, GAUGE, YARN, TOOLS, SIZES, SKIP, INSTRUCTIONS }

    private val HEADING_BLOCKS: List<Pair<Regex, Block>> = listOf(
        Regex("""^(gauge|tension)$""") to Block.GAUGE,
        Regex("""^(yarn|yarns|materials?|supplies|you will need|what you need|materials and tools)$""") to Block.YARN,
        Regex("""^(needles?|hooks?|crochet hooks?|tools|notions|needles and notions|equipment)$""") to Block.TOOLS,
        Regex("""^(sizes?|finished (size|measurements?)|measurements?|sizing)$""") to Block.SIZES,
        Regex("""^(abbreviations?|stitch glossary|glossary|techniques|special stitches|skill level|difficulty|copyright|terms of use|support|contact)$""") to Block.SKIP,
        Regex("""^(about( this pattern)?|description|introduction|pattern notes|notes|designer'?s? notes?|construction|story)$""") to Block.DESCRIPTION
    )

    private val INLINE_FIELD = Regex(
        """^(gauge|tension|yarn|needles?|hooks?|crochet hook|notions|sizes?|finished measurements?|yardage)\s*[:–—-]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val BYLINE = Regex("""^(?:.*\b)?(?:designed\s+)?by\s+([A-Z][\p{L}'.-]+(?:\s+[A-Z][\p{L}'.-]+){0,3})\s*$""")
    private val NOISE = Regex(
        """(©|\(c\)\s*\d{4}|copyright|all rights reserved|https?://|www\.|\.com\b|instagram|facebook|#\w+|@\w{3,})""",
        RegexOption.IGNORE_CASE
    )
    private val STARTS_INSTRUCTIONS = Regex(
        """^(cast on|co\s+\d|using .+ cast on|ch(ain)?\s+\d|make a magic ring|with .+ needles?,? cast on|begin(ning)?\b)""",
        RegexOption.IGNORE_CASE
    )
    /** One amount or a size list such as "1000 (1200, 1400)", followed by its unit. */
    private val YARDAGE = Regex("""(\d[\d,.]*(?:\s*\(\s*\d[\d,.\s]*\))?)\s*(yds?|yards?|m|metres?|meters?)\b""", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("""\d+(?:\.\d+)?""")
    private val PER_UNIT = Regex("""\b(per|each)\b|/\s*(skein|ball|hank|cake)""", RegexOption.IGNORE_CASE)
    private const val METRES_PER_YARD = 0.9144

    /**
     * [isRowLine] and [headingTitle] are the parser's own recognisers, so the
     * splitter and the parser agree on what a row or a heading is.
     */
    fun split(
        lines: List<ExtractedLine>,
        isRowLine: (String) -> Boolean,
        headingTitle: (String) -> String?
    ): SplitPattern {
        val description = mutableListOf<String>()
        val gauge = mutableListOf<String>()
        val yarn = mutableListOf<String>()
        val tools = mutableListOf<String>()
        val sizes = mutableListOf<String>()
        var designer: String? = null
        val instructions = mutableListOf<ExtractedLine>()
        var block = Block.DESCRIPTION
        var started = false

        fun add(target: Block, text: String) = when (target) {
            Block.DESCRIPTION -> description += text
            Block.GAUGE -> gauge += text
            Block.YARN -> yarn += text
            Block.TOOLS -> tools += text
            Block.SIZES -> sizes += text
            Block.SKIP, Block.INSTRUCTIONS -> Unit
        }

        for (line in lines) {
            val text = line.text.trim()
            if (text.isEmpty() || NOISE.containsMatchIn(text)) continue

            val heading = headingTitle(text)
            if (heading != null) {
                val detailsBlock = blockFor(heading)
                block = when {
                    detailsBlock != null -> detailsBlock
                    else -> {
                        started = true
                        Block.INSTRUCTIONS
                    }
                }
                if (block == Block.INSTRUCTIONS) instructions += line
                continue
            }

            val inline = INLINE_FIELD.matchEntire(text)
            if (inline != null) {
                add(fieldFor(inline.groupValues[1]), inline.groupValues[2].trim())
                continue
            }

            if (block != Block.INSTRUCTIONS && (isRowLine(text) || STARTS_INSTRUCTIONS.containsMatchIn(text))) {
                started = true
                block = Block.INSTRUCTIONS
            }

            if (block == Block.INSTRUCTIONS) {
                instructions += line
                continue
            }

            if (!started && block == Block.DESCRIPTION) {
                val byline = BYLINE.matchEntire(text)
                if (byline != null) {
                    if (designer == null) designer = byline.groupValues[1].trim()
                    continue
                }
            }
            add(block, text)
        }

        val yarnText = yarn.joined()
        return SplitPattern(
            details = PatternDetails(
                description = description.joined(),
                designer = designer,
                gauge = gauge.joined(),
                yarn = yarnText,
                tools = tools.joined(),
                sizes = sizes.joined(),
                yardage = yarnText?.let(::yardsIn)
            ),
            instructions = instructions
        )
    }

    private fun blockFor(heading: String): Block? {
        val key = heading.lowercase().trim().removeSuffix(":").trim()
        return HEADING_BLOCKS.firstOrNull { (pattern, _) -> pattern.matches(key) }?.second
    }

    private fun fieldFor(label: String): Block = when (label.lowercase().trim()) {
        "gauge", "tension" -> Block.GAUGE
        "yarn", "yardage" -> Block.YARN
        "size", "sizes", "finished measurement", "finished measurements" -> Block.SIZES
        else -> Block.TOOLS
    }

    /** Lines of one block read as one paragraph; a block of one line stays as written. */
    private fun List<String>.joined(): String? =
        if (isEmpty()) null else joinToString(" ").replace(Regex("""\s+"""), " ").trim().ifEmpty { null }

    /**
     * The largest total amount of yarn mentioned, in yards (patterns list the
     * biggest size last). An amount "per skein" or "each" is not a total, so
     * then nothing is guessed.
     */
    private fun yardsIn(text: String): Double? =
        if (PER_UNIT.containsMatchIn(text)) {
            null
        } else {
            YARDAGE.findAll(text).flatMap { match ->
                val toYards = if (match.groupValues[2].lowercase().startsWith("y")) 1.0 else 1 / METRES_PER_YARD
                NUMBER.findAll(match.groupValues[1].replace(",", "")).mapNotNull { it.value.toDoubleOrNull()?.times(toYards) }
            }.maxOrNull()?.let { Math.round(it).toDouble() }
        }
}
