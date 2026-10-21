package com.macareen.stitchbook2.domain.guide

import com.macareen.stitchbook2.domain.parsing.PatternDetails
import com.macareen.stitchbook2.domain.parsing.StructuredGuide

data class GlossaryEntry(val term: String, val definition: String)

/** Human-readable reference notes, carried by the existing guide JSON/Markdown exports. */
object GuideReference {
    private val definition = Regex("""\b(?:([\p{L}][\p{L}\d-]{0,20}(?:\(s\))?(?: +(?:st|stitch|pass|tbl|tfl))?)(?:\s*[:=]\s*|\s+[-–—]\s+)|(k|p|sts?|rs|ws|co|bo|kfb|k2tog|ssk|skp|m1[lr]?|yo|sc|dc|hdc|tr|sl st)\s+(?=\p{L}))""", RegexOption.IGNORE_CASE)
    private val headings = setOf("abbreviation", "abbreviations", "glossary", "materials", "gauge", "notes", "measurements", "description", "tools", "sizes", "designer")

    fun glossary(notes: String?): List<GlossaryEntry> {
        if (notes.isNullOrBlank()) return emptyList()
        val glossary = Regex("""(?is)(?:^|\n)(?:##\s*)?(?:abbreviations?|glossary|techniques)\s*:?\s*(.*?)(?=\n##\s|$)""")
            .findAll(notes).map { it.groupValues[1] }.joinToString("\n")
        val matches = definition.findAll(glossary).toList()
        return matches.mapIndexedNotNull { index, match ->
            val term = match.groupValues[1].ifBlank { match.groupValues[2] }
            val meaning = glossary.substring(match.range.last + 1, matches.getOrNull(index + 1)?.range?.first ?: glossary.length)
                .trim().trimEnd(';').trim()
            if (term.lowercase() in headings || meaning.isEmpty()) null else GlossaryEntry(term, meaning)
        }.distinctBy { it.term.lowercase() }
    }

    fun notes(details: PatternDetails): String? = sections(listOf(
        "Description" to details.description, "Designer" to details.designer, "Gauge" to details.gauge,
        "Materials" to details.yarn, "Tools" to details.tools, "Sizes" to details.sizes,
        "Measurements" to details.measurements, "Techniques" to details.techniques, "Glossary" to details.abbreviations))

    fun notes(guide: StructuredGuide): String? = sections(listOf(
        "Materials" to guide.materials.joinToString("\n").ifBlank { null },
        "Glossary" to guide.abbreviations.joinToString("\n") { "${it.term}: ${it.meaning}" }.ifBlank { null },
        "Notes" to guide.notes.joinToString("\n").ifBlank { null }))

    private fun sections(values: List<Pair<String, String?>>): String? = values.mapNotNull { (title, text) ->
        text?.takeIf { it.isNotBlank() }?.let { "## $title\n$it" }
    }.joinToString("\n\n").ifBlank { null }
}
