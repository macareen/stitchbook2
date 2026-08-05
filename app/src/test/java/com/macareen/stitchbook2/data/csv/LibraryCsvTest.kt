package com.macareen.stitchbook2.data.csv

import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val HEADER_ROW = "id,title,craft,author,sourceUrl,tags,notes,bookmarked"

/** Builds one data row aligned to [HEADER_ROW]'s 8 columns -- avoids hand-counting commas. */
private fun row(
    id: String = "",
    title: String = "",
    craft: String = "KNITTING",
    author: String = "",
    sourceUrl: String = "",
    tags: String = "",
    notes: String = "",
    bookmarked: String = "false"
): String = listOf(id, title, craft, author, sourceUrl, tags, notes, bookmarked).joinToString(",")

class LibraryCsvTest {

    private val item = LibraryItem(
        id = "library-1",
        title = "Everyday Cardigan",
        craft = Craft.KNITTING,
        author = "Jane Designer",
        sourceUrl = "https://example.com/patterns/everyday-cardigan",
        tags = listOf("cardigan", "worsted"),
        notes = "Reserved for \"the good yarn\"\nSize medium.",
        bookmarked = true,
        createdAt = 100,
        updatedAt = 200
    )

    @Test
    fun exportedCsvRoundTripsBackToTheSameItem() {
        val csv = libraryItemsToCsv(listOf(item))
        val report = parseLibraryCsv(
            csv,
            existingItemsById = mapOf(item.id to item),
            now = { item.updatedAt }
        )

        assertEquals(emptyList<LibraryCsvRowError>(), report.rowErrors)
        assertEquals(item, report.validItems.single())
    }

    @Test
    fun quotedFieldsWithCommasAndNewlinesSurviveARoundTrip() {
        val csv = libraryItemsToCsv(listOf(item))
        val report = parseLibraryCsv(csv, existingItemsById = emptyMap())

        val parsed = report.validItems.single()
        assertEquals(listOf("cardigan", "worsted"), parsed.tags)
        assertEquals("Reserved for \"the good yarn\"\nSize medium.", parsed.notes)
    }

    @Test
    fun importingWithAnExistingIdPreservesCreatedAtAndTheExistingPdfAttachment() {
        val existing = item.copy(
            title = "Old Title",
            createdAt = 1L,
            pdfUri = "content://existing-pdf",
            pdfFileName = "pattern.pdf",
            pdfLastViewedPage = 3
        )
        val csv = libraryItemsToCsv(listOf(item.copy(id = existing.id)))

        val report = parseLibraryCsv(csv, existingItemsById = mapOf(existing.id to existing), now = { 999L })

        val updated = report.validItems.single()
        assertEquals(existing.id, updated.id)
        assertEquals(1L, updated.createdAt)
        assertEquals(999L, updated.updatedAt)
        assertEquals("Everyday Cardigan", updated.title)
        // CSV never carries the PDF attachment -- an update-in-place must
        // not clear it just because the file doesn't mention it.
        assertEquals("content://existing-pdf", updated.pdfUri)
        assertEquals("pattern.pdf", updated.pdfFileName)
        assertEquals(3, updated.pdfLastViewedPage)
    }

    @Test
    fun blankIdGeneratesANewIdAndUsesCurrentTimeForCreatedAt() {
        val csv = libraryItemsToCsv(listOf(item.copy(id = "")))

        val report = parseLibraryCsv(
            csv,
            existingItemsById = emptyMap(),
            newId = { "generated-id" },
            now = { 555L }
        )

        val created = report.validItems.single()
        assertEquals("generated-id", created.id)
        assertEquals(555L, created.createdAt)
        assertEquals(555L, created.updatedAt)
        assertEquals(null, created.pdfUri)
    }

    @Test
    fun missingTitleIsReportedAndSkippedWithoutAffectingOtherRows() {
        val csv = listOf(
            HEADER_ROW,
            row(title = ""),
            row(id = "good-id", title = "Good Pattern")
        ).joinToString("\n")

        val report = parseLibraryCsv(csv, existingItemsById = emptyMap())

        assertEquals(1, report.validItems.size)
        assertEquals("Good Pattern", report.validItems.single().title)
        assertTrue(report.rowErrors.single().message.contains("title"))
        assertEquals(2, report.rowErrors.single().rowNumber)
    }

    @Test
    fun unrecognizedCraftIsReportedWithTheOffendingRowNumber() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", title = "Mystery Pattern", craft = "SPACE_KNITTING")
        ).joinToString("\n")

        val report = parseLibraryCsv(csv, existingItemsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertTrue(report.rowErrors.single().message.contains("SPACE_KNITTING"))
    }

    @Test
    fun bookmarkedAcceptsCaseInsensitiveTrueAndDefaultsToFalse() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", title = "Bookmarked", bookmarked = "TRUE"),
            row(id = "id-2", title = "Not bookmarked", bookmarked = "nonsense")
        ).joinToString("\n")

        val report = parseLibraryCsv(csv, existingItemsById = emptyMap())

        val byTitle = report.validItems.associateBy { it.title }
        assertEquals(true, byTitle.getValue("Bookmarked").bookmarked)
        assertEquals(false, byTitle.getValue("Not bookmarked").bookmarked)
    }

    @Test
    fun missingRequiredColumnsFailsTheWholeImportWithOneClearError() {
        val csv = "id,title,craft\nid-1,Some Pattern,KNITTING"

        val report = parseLibraryCsv(csv, existingItemsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertEquals(1, report.rowErrors.size)
        assertTrue(report.rowErrors.single().message.contains("author"))
    }

    @Test
    fun templateProducesExactlyOneExampleRowThatParsesCleanly() {
        val report = parseLibraryCsv(libraryCsvTemplate(), existingItemsById = emptyMap())

        assertEquals(1, report.validItems.size)
        assertEquals(emptyList<LibraryCsvRowError>(), report.rowErrors)
    }

    @Test
    fun blankLinesBetweenRowsAreIgnored() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", title = "First"),
            "",
            row(id = "id-2", title = "Second")
        ).joinToString("\n")

        val report = parseLibraryCsv(csv, existingItemsById = emptyMap())

        assertEquals(listOf("First", "Second"), report.validItems.map { it.title })
        assertEquals(emptyList<LibraryCsvRowError>(), report.rowErrors)
    }
}
