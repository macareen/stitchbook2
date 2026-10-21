package com.macareen.stitchbook2.domain.library

import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.parsing.PatternDetails

/** Unselected PDF metadata belongs to the pattern, never to a size's execution. */
data class PatternPdfInfo(val title: String?, val details: PatternDetails, val sizes: List<String>)

object PatternSuggestions {
    fun apply(item: LibraryItem, info: PatternPdfInfo): LibraryItem {
        val details = info.details
        val automaticTitle = item.pdfFileName?.let { PatternFolderPlanner.titleFor(it) == item.title } == true
        val notes = appendMissing(item.notes, listOf("Description" to details.description,
            "Suggested yarn" to details.yarn, "Measurements" to details.measurements,
            "Techniques" to details.techniques, "Glossary" to details.abbreviations))
        return item.copy(title = if (automaticTitle) info.title ?: item.title else item.title,
            author = item.author?.takeIf(String::isNotBlank) ?: details.designer,
            gauge = item.gauge?.takeIf(String::isNotBlank) ?: details.gauge,
            sizes = item.sizes?.takeIf(String::isNotBlank) ?: details.sizes ?: info.sizes.takeIf { it.isNotEmpty() }?.joinToString(", "),
            recommendedTools = item.recommendedTools?.takeIf(String::isNotBlank) ?: details.tools,
            yardageRequired = item.yardageRequired ?: details.yardage, notes = notes)
    }

    fun sections(notes: String?): List<Pair<String, String>> = notes?.let {
        Regex("""(?ms)^## +([^\n]+)\n(.*?)(?=^## |\z)""").findAll(it).map { match ->
            match.groupValues[1].trim() to match.groupValues[2].trim()
        }.toList()
    }.orEmpty()

    fun description(item: LibraryItem): String? {
        val sections = sections(item.notes)
        return if (sections.isEmpty()) item.notes else sections.firstOrNull { it.first.equals("Description", true) }?.second
            ?: item.notes?.substringBefore("## ")?.trim()?.takeIf(String::isNotBlank)
    }

    fun reference(item: LibraryItem): String? = listOfNotNull(
        item.gauge?.let { "## Gauge\n$it" }, item.recommendedTools?.let { "## Tools\n$it" },
        item.sizes?.let { "## Sizes\n$it" }, item.notes).joinToString("\n\n").ifBlank { null }

    private fun appendMissing(notes: String?, fields: List<Pair<String, String?>>): String? {
        val existing = sections(notes).map { it.first.lowercase() }.toSet()
        val added = fields.mapNotNull { (heading, text) -> text?.trim()?.takeIf { it.isNotEmpty() && heading.lowercase() !in existing && notes?.contains(it) != true }
            ?.let { "## $heading\n$it" } }
        return (listOfNotNull(notes?.takeIf(String::isNotBlank)) + added).joinToString("\n\n").ifBlank { null }
    }
}
