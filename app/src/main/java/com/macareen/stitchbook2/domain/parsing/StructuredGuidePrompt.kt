package com.macareen.stitchbook2.domain.parsing

import com.macareen.stitchbook2.domain.model.Craft

/**
 * Builds the request a person can paste into an assistant they already use
 * (a free Claude account works) to turn their own pattern text into a
 * [StructuredGuide]. Stitchbook sends nothing itself: the person chooses
 * where the text goes, and the reply comes back only when they paste it.
 *
 * The wording asks for the pattern's own instructions, never invented ones,
 * and routes anything uncertain into `review` so it stays visible.
 */
object StructuredGuidePrompt {

    fun build(craft: Craft, guideName: String, patternText: String): String = buildString {
        appendLine("You are helping me turn a ${craftName(craft)} pattern I own into a step-by-step guide for an app.")
        appendLine("Read the pattern text below and reply with ONLY one JSON object in this exact shape (no commentary):")
        appendLine()
        appendLine(SCHEMA_EXAMPLE)
        appendLine()
        appendLine("Rules:")
        appendLine("- Use the pattern's own instructions and wording. Never invent stitches, counts, or steps.")
        appendLine("- Step types: \"section\" (title + steps), \"rows\" (unit is \"row\" or \"round\" or the pattern's own unit, from, to, text), \"repeat\" (times, optional label, steps), \"instruction\" (text).")
        appendLine("- One numbered row or round is a \"rows\" step with from equal to to. A span worked the same way (\"Rounds 4-10\") is one \"rows\" step.")
        appendLine("- When the pattern says to repeat earlier rows, wrap those rows in a \"repeat\" step with the number of times.")
        appendLine("- Keep stitch counts at the end of the text, like \"(24 sts)\".")
        appendLine("- Put sizes, gauge, and finishing notes in \"notes\". Put yarn, hooks, needles, and notions in \"materials\".")
        appendLine("- If something is unclear, missing, or depends on a chart or photo, add a short sentence to \"review\" instead of guessing.")
        appendLine("- Use ${craftName(craft)} terms exactly as the pattern does; do not convert between US and UK terms.")
        appendLine()
        appendLine("Guide name: $guideName")
        appendLine()
        appendLine("Pattern text:")
        appendLine("<<<")
        appendLine(patternText.trim())
        append(">>>")
    }

    /** Pattern lines with page breaks marked, so the assistant can tell pages apart. */
    fun patternText(document: ExtractedDocument): String = buildString {
        var page = 0
        for (line in document.lines) {
            if (line.source.pageNumber != page) {
                page = line.source.pageNumber
                if (isNotEmpty()) appendLine()
                appendLine("[page $page]")
            }
            appendLine(line.text)
        }
    }.trimEnd()

    private fun craftName(craft: Craft): String = when (craft) {
        Craft.KNITTING -> "knitting"
        Craft.CROCHET -> "crochet"
        Craft.TUNISIAN_CROCHET -> "Tunisian crochet"
        Craft.LOOM_KNITTING -> "loom knitting"
        Craft.OTHER -> "fibre craft"
    }

    const val SCHEMA_EXAMPLE = """{
  "format": "stitchbook-guide",
  "version": 1,
  "name": "Guide name",
  "materials": ["Worsted yarn, 200 m", "5 mm hook"],
  "abbreviations": [{"term": "sc", "meaning": "single crochet"}],
  "notes": ["Gauge: 16 sts = 10 cm"],
  "steps": [
    {"type": "section", "title": "Body", "steps": [
      {"type": "rows", "unit": "round", "from": 1, "to": 1, "text": "Magic ring, 6 sc in ring. (6 sts)"},
      {"type": "rows", "unit": "round", "from": 2, "to": 2, "text": "2 sc in each st around. (12 sts)"},
      {"type": "repeat", "times": 3, "label": "Rounds 3-4", "steps": [
        {"type": "rows", "unit": "round", "from": 3, "to": 3, "text": "Sc around."},
        {"type": "rows", "unit": "round", "from": 4, "to": 4, "text": "Sc2tog around."}
      ]},
      {"type": "instruction", "text": "Fasten off and weave in ends."}
    ]}
  ],
  "review": ["Round 7 refers to a chart that is not in the text."]
}"""
}
