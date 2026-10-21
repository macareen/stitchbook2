package com.macareen.stitchbook2.domain.library

import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.domain.parsing.PatternDetails
import com.macareen.stitchbook2.domain.guide.GuideReference
import org.junit.Assert.*
import org.junit.Test

class PatternSuggestionsTest {
    private val item = LibraryItem("p", "scan", Craft.OTHER, null, null, emptyList(), null, false, 1, 1, pdfFileName = "scan.pdf")
    private val info = PatternPdfInfo("Sample wrap", PatternDetails(description = "A textured wrap.", designer = "Example",
        gauge = "20 stitches over 10 cm", yarn = "DK wool", tools = "4 mm needles", techniques = "Short rows",
        abbreviations = "kfb: Knit front and back"), listOf("S", "M"))
    @Test fun importSetsUpPatternFieldsAndPortableReferenceBeforeAnyGuideExists() {
        val filled = PatternSuggestions.apply(item, info)
        assertEquals("Sample wrap", filled.title)
        assertEquals("4 mm needles", filled.recommendedTools)
        assertEquals("S, M", filled.sizes)
        assertEquals("A textured wrap.", PatternSuggestions.description(filled))
        assertTrue(filled.notes!!.contains("## Suggested yarn\nDK wool"))
        assertTrue(filled.notes!!.contains("## Techniques\nShort rows"))
        assertEquals("kfb", GuideReference.glossary(PatternSuggestions.reference(filled)).single().term)
        assertEquals(filled, PatternSuggestions.apply(filled, info))
    }
    @Test fun manualFieldsAndExistingSectionsAreNeverReplaced() {
        val manual = item.copy(title = "My name", author = "Me", gauge = "My gauge", notes = "My notes\n\n## Description\nMy description")
        val filled = PatternSuggestions.apply(manual, info)
        assertEquals("My name", filled.title)
        assertEquals("Me", filled.author)
        assertEquals("My gauge", filled.gauge)
        assertEquals("My description", PatternSuggestions.description(filled))
        assertTrue(filled.notes!!.startsWith("My notes"))
        assertFalse(filled.notes!!.contains("A textured wrap."))
    }
}
