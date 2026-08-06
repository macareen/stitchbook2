package com.macareen.stitchbook2.data.csv

import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val HEADER_ROW =
    "id,name,category,brand,colorway,dyeLot,weightCategory,fiberContent,quantity,unitLabel," +
        "yardagePerUnit,notes,storageLocation,careInstructions,ravelryYarnId,purchaseSource," +
        "purchasePrice,purchaseDate"

/** Builds one data row aligned to [HEADER_ROW]'s 18 columns -- avoids hand-counting commas. */
private fun row(
    id: String = "",
    name: String = "",
    category: String = "YARN",
    brand: String = "",
    colorway: String = "",
    dyeLot: String = "",
    weightCategory: String = "",
    fiberContent: String = "",
    quantity: String = "1",
    unitLabel: String = "skeins",
    yardagePerUnit: String = "",
    notes: String = "",
    storageLocation: String = "",
    careInstructions: String = "",
    ravelryYarnId: String = "",
    purchaseSource: String = "",
    purchasePrice: String = "",
    purchaseDate: String = ""
): String = listOf(
    id, name, category, brand, colorway, dyeLot,
    weightCategory, fiberContent, quantity, unitLabel, yardagePerUnit, notes,
    storageLocation, careInstructions, ravelryYarnId, purchaseSource, purchasePrice, purchaseDate
).joinToString(",")

class StashCsvTest {

    private val item = StashItem(
        id = "stash-1",
        name = "Cascade 220",
        category = StashCategory.YARN,
        brand = "Cascade Yarns",
        colorway = "Ivory, Cream",
        dyeLot = "12345",
        weightCategory = "Worsted",
        fiberContent = "100% Wool",
        quantity = 6.0,
        unitLabel = "skeins",
        yardagePerUnit = 220.0,
        notes = "Reserved for \"the cardigan\"\nBuy more if possible.",
        storageLocation = "Bin 3",
        careInstructions = "Hand wash cold, lay flat to dry",
        ravelryYarnId = "12345",
        purchaseSource = "Local yarn shop",
        purchasePrice = 8.5,
        purchaseDate = "2024-03-15",
        createdAt = 100,
        updatedAt = 200
    )

    @Test
    fun exportedCsvRoundTripsBackToTheSameItem() {
        val csv = stashItemsToCsv(listOf(item))
        val report = parseStashCsv(
            csv,
            existingItemsById = mapOf(item.id to item),
            now = { item.updatedAt }
        )

        assertEquals(emptyList<StashCsvRowError>(), report.rowErrors)
        assertEquals(item, report.validItems.single())
    }

    @Test
    fun quotedFieldsWithCommasAndNewlinesSurviveARoundTrip() {
        val csv = stashItemsToCsv(listOf(item))
        val report = parseStashCsv(csv, existingItemsById = emptyMap())

        val parsed = report.validItems.single()
        assertEquals("Ivory, Cream", parsed.colorway)
        assertEquals("Reserved for \"the cardigan\"\nBuy more if possible.", parsed.notes)
    }

    @Test
    fun importingWithAnExistingIdPreservesCreatedAtAndUpdatesTheRest() {
        val existing = item.copy(name = "Old Name", createdAt = 1L)
        val csv = stashItemsToCsv(listOf(item.copy(id = existing.id)))

        val report = parseStashCsv(csv, existingItemsById = mapOf(existing.id to existing), now = { 999L })

        val updated = report.validItems.single()
        assertEquals(existing.id, updated.id)
        assertEquals(1L, updated.createdAt)
        assertEquals(999L, updated.updatedAt)
        assertEquals("Cascade 220", updated.name)
    }

    @Test
    fun blankIdGeneratesANewIdAndUsesCurrentTimeForCreatedAt() {
        val csv = stashItemsToCsv(listOf(item.copy(id = "")))

        val report = parseStashCsv(
            csv,
            existingItemsById = emptyMap(),
            newId = { "generated-id" },
            now = { 555L }
        )

        val created = report.validItems.single()
        assertEquals("generated-id", created.id)
        assertEquals(555L, created.createdAt)
        assertEquals(555L, created.updatedAt)
    }

    @Test
    fun missingNameIsReportedAndSkippedWithoutAffectingOtherRows() {
        val csv = listOf(
            HEADER_ROW,
            row(name = "", quantity = "1"),
            row(id = "good-id", name = "Good Item", quantity = "2")
        ).joinToString("\n")

        val report = parseStashCsv(csv, existingItemsById = emptyMap())

        assertEquals(1, report.validItems.size)
        assertEquals("Good Item", report.validItems.single().name)
        assertTrue(report.rowErrors.single().message.contains("name"))
        assertEquals(2, report.rowErrors.single().rowNumber)
    }

    @Test
    fun unrecognizedCategoryIsReportedWithTheOffendingRowNumber() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Mystery Fiber", category = "SPACE_WOOL")
        ).joinToString("\n")

        val report = parseStashCsv(csv, existingItemsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertTrue(report.rowErrors.single().message.contains("SPACE_WOOL"))
    }

    @Test
    fun nonNumericQuantityIsReportedRatherThanCrashing() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Some Yarn", quantity = "not-a-number")
        ).joinToString("\n")

        val report = parseStashCsv(csv, existingItemsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertTrue(report.rowErrors.single().message.contains("quantity"))
    }

    @Test
    fun negativeQuantityIsReported() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Some Yarn", quantity = "-3")
        ).joinToString("\n")

        val report = parseStashCsv(csv, existingItemsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertTrue(report.rowErrors.single().message.contains("quantity"))
    }

    @Test
    fun nonNumericPurchasePriceIsReportedRatherThanCrashing() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Some Yarn", purchasePrice = "not-a-number")
        ).joinToString("\n")

        val report = parseStashCsv(csv, existingItemsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertTrue(report.rowErrors.single().message.contains("purchasePrice"))
    }

    @Test
    fun negativePurchasePriceIsReported() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Some Yarn", purchasePrice = "-5")
        ).joinToString("\n")

        val report = parseStashCsv(csv, existingItemsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertTrue(report.rowErrors.single().message.contains("purchasePrice"))
    }

    @Test
    fun blankPurchasePriceIsAllowedAndStoredAsNull() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Some Yarn", purchasePrice = "")
        ).joinToString("\n")

        val report = parseStashCsv(csv, existingItemsById = emptyMap())

        assertEquals(null, report.validItems.single().purchasePrice)
    }

    @Test
    fun missingRequiredColumnsFailsTheWholeImportWithOneClearError() {
        val csv = "id,name,category\nid-1,Some Yarn,YARN"

        val report = parseStashCsv(csv, existingItemsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertEquals(1, report.rowErrors.size)
        assertTrue(report.rowErrors.single().message.contains("quantity"))
    }

    @Test
    fun templateProducesExactlyOneExampleRowThatParsesCleanly() {
        val report = parseStashCsv(stashCsvTemplate(), existingItemsById = emptyMap())

        assertEquals(1, report.validItems.size)
        assertEquals(emptyList<StashCsvRowError>(), report.rowErrors)
    }

    @Test
    fun blankLinesBetweenRowsAreIgnored() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "First", quantity = "1"),
            "",
            row(id = "id-2", name = "Second", quantity = "2")
        ).joinToString("\n")

        val report = parseStashCsv(csv, existingItemsById = emptyMap())

        assertEquals(listOf("First", "Second"), report.validItems.map { it.name })
        assertEquals(emptyList<StashCsvRowError>(), report.rowErrors)
    }
}
