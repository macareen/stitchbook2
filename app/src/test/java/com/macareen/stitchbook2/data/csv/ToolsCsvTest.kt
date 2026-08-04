package com.macareen.stitchbook2.data.csv

import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val HEADER_ROW = listOf(
    "id", "name", "category", "brand", "material", "sizeMetricMm", "sizeLabel",
    "lengthMm", "statedCableLengthMm", "cableLengthDefinition",
    "approximateAssembledLengthMm", "connectorFamily", "compatibilityNotes",
    "quantity", "storageLocation", "notes", "setId", "setName"
).joinToString(",")

/** Builds one data row aligned to [HEADER_ROW]'s 18 columns -- avoids hand-counting commas. */
private fun row(
    id: String = "",
    name: String = "",
    category: String = "CROCHET_HOOK",
    brand: String = "",
    material: String = "",
    sizeMetricMm: String = "",
    sizeLabel: String = "",
    lengthMm: String = "",
    statedCableLengthMm: String = "",
    cableLengthDefinition: String = "",
    approximateAssembledLengthMm: String = "",
    connectorFamily: String = "",
    compatibilityNotes: String = "",
    quantity: String = "1",
    storageLocation: String = "",
    notes: String = "",
    setId: String = "",
    setName: String = ""
): String = listOf(
    id, name, category, brand, material, sizeMetricMm, sizeLabel, lengthMm,
    statedCableLengthMm, cableLengthDefinition, approximateAssembledLengthMm,
    connectorFamily, compatibilityNotes, quantity, storageLocation, notes,
    setId, setName
).joinToString(",")

class ToolsCsvTest {

    private val item = ToolItem(
        id = "tool-1",
        name = "US 7 interchangeable tip",
        category = ToolCategory.INTERCHANGEABLE_TIP,
        brand = "ChiaoGoo",
        material = "Stainless steel",
        sizeMetricMm = 4.5,
        sizeLabel = "US 7",
        lengthMm = 127.0,
        statedCableLengthMm = null,
        cableLengthDefinition = null,
        approximateAssembledLengthMm = null,
        connectorFamily = "ChiaoGoo Twist",
        compatibilityNotes = "Twist-compatible only",
        quantity = 2,
        storageLocation = "Tip case, slot 7",
        notes = "Reserved for \"the sweater\"\nSlightly bent.",
        setId = null,
        createdAt = 100,
        updatedAt = 200
    )

    @Test
    fun exportedCsvRoundTripsBackToTheSameItem() {
        val csv = toolItemsToCsv(listOf(item), setsById = emptyMap())
        val report = parseToolsCsv(
            csv,
            existingItemsById = mapOf(item.id to item),
            existingSetsById = emptyMap(),
            now = { item.updatedAt }
        )

        assertEquals(emptyList<ToolsCsvRowError>(), report.rowErrors)
        assertEquals(item, report.validItems.single())
    }

    @Test
    fun quotedFieldsWithCommasAndNewlinesSurviveARoundTrip() {
        val csv = toolItemsToCsv(listOf(item), setsById = emptyMap())
        val report = parseToolsCsv(csv, existingItemsById = emptyMap(), existingSetsById = emptyMap())

        val parsed = report.validItems.single()
        assertEquals("Reserved for \"the sweater\"\nSlightly bent.", parsed.notes)
    }

    @Test
    fun importingWithAnExistingIdPreservesCreatedAtAndUpdatesTheRest() {
        val existing = item.copy(name = "Old Name", createdAt = 1L)
        val csv = toolItemsToCsv(listOf(item.copy(id = existing.id)), setsById = emptyMap())

        val report = parseToolsCsv(
            csv,
            existingItemsById = mapOf(existing.id to existing),
            existingSetsById = emptyMap(),
            now = { 999L }
        )

        val updated = report.validItems.single()
        assertEquals(existing.id, updated.id)
        assertEquals(1L, updated.createdAt)
        assertEquals(999L, updated.updatedAt)
        assertEquals("US 7 interchangeable tip", updated.name)
    }

    @Test
    fun blankIdGeneratesANewIdAndUsesCurrentTimeForCreatedAt() {
        val csv = toolItemsToCsv(listOf(item.copy(id = "")), setsById = emptyMap())

        val report = parseToolsCsv(
            csv,
            existingItemsById = emptyMap(),
            existingSetsById = emptyMap(),
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
            row(id = "good-id", name = "Good Tool", quantity = "2")
        ).joinToString("\n")

        val report = parseToolsCsv(csv, existingItemsById = emptyMap(), existingSetsById = emptyMap())

        assertEquals(1, report.validItems.size)
        assertEquals("Good Tool", report.validItems.single().name)
        assertTrue(report.rowErrors.single().message.contains("name"))
        assertEquals(2, report.rowErrors.single().rowNumber)
    }

    @Test
    fun unrecognizedCategoryIsReportedWithTheOffendingRowNumber() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Mystery Tool", category = "SPACE_HOOK")
        ).joinToString("\n")

        val report = parseToolsCsv(csv, existingItemsById = emptyMap(), existingSetsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertTrue(report.rowErrors.single().message.contains("SPACE_HOOK"))
    }

    @Test
    fun nonNumericQuantityIsReportedRatherThanCrashing() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Some Hook", quantity = "not-a-number")
        ).joinToString("\n")

        val report = parseToolsCsv(csv, existingItemsById = emptyMap(), existingSetsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertTrue(report.rowErrors.single().message.contains("quantity"))
    }

    @Test
    fun negativeQuantityIsReported() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Some Hook", quantity = "-3")
        ).joinToString("\n")

        val report = parseToolsCsv(csv, existingItemsById = emptyMap(), existingSetsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertTrue(report.rowErrors.single().message.contains("quantity"))
    }

    @Test
    fun invalidNumericFieldIsReported() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Some Hook", sizeMetricMm = "not-a-number")
        ).joinToString("\n")

        val report = parseToolsCsv(csv, existingItemsById = emptyMap(), existingSetsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertTrue(report.rowErrors.single().message.contains("sizeMetricMm"))
    }

    @Test
    fun missingRequiredColumnsFailsTheWholeImportWithOneClearError() {
        val csv = "id,name,category\nid-1,Some Hook,CROCHET_HOOK"

        val report = parseToolsCsv(csv, existingItemsById = emptyMap(), existingSetsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertEquals(1, report.rowErrors.size)
        assertTrue(report.rowErrors.single().message.contains("quantity"))
    }

    @Test
    fun templateProducesExactlyOneExampleRowThatParsesCleanly() {
        val report = parseToolsCsv(toolsCsvTemplate(), existingItemsById = emptyMap(), existingSetsById = emptyMap())

        assertEquals(1, report.validItems.size)
        assertEquals(emptyList<ToolsCsvRowError>(), report.rowErrors)
    }

    @Test
    fun blankLinesBetweenRowsAreIgnored() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "First", quantity = "1"),
            "",
            row(id = "id-2", name = "Second", quantity = "2")
        ).joinToString("\n")

        val report = parseToolsCsv(csv, existingItemsById = emptyMap(), existingSetsById = emptyMap())

        assertEquals(listOf("First", "Second"), report.validItems.map { it.name })
        assertEquals(emptyList<ToolsCsvRowError>(), report.rowErrors)
    }

    @Test
    fun rowsSharingANewSetNameAreGroupedUnderOneCreatedSet() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Tip A", setName = "New Set"),
            row(id = "id-2", name = "Tip B", setName = "new set")
        ).joinToString("\n")

        val report = parseToolsCsv(csv, existingItemsById = emptyMap(), existingSetsById = emptyMap())

        assertEquals(1, report.newSets.size)
        assertEquals("New Set", report.newSets.single().name)
        assertEquals(2, report.validItems.size)
        assertEquals(report.newSets.single().id, report.validItems[0].setId)
        assertEquals(report.newSets.single().id, report.validItems[1].setId)
    }

    @Test
    fun rowNamingAnExistingSetByNameIsAssignedToItWithoutCreatingADuplicate() {
        val existingSet = ToolSet(
            id = "set-1",
            name = "ChiaoGoo Twist Set",
            brand = null,
            notes = null,
            createdAt = 0,
            updatedAt = 0
        )
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Tip A", setName = "chiaogoo twist set")
        ).joinToString("\n")

        val report = parseToolsCsv(
            csv,
            existingItemsById = emptyMap(),
            existingSetsById = mapOf(existingSet.id to existingSet)
        )

        assertEquals(emptyList<ToolSet>(), report.newSets)
        assertEquals(existingSet.id, report.validItems.single().setId)
    }

    @Test
    fun anUnknownSetIdIsReportedAndTheRowIsSkipped() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Tip A", setId = "does-not-exist")
        ).joinToString("\n")

        val report = parseToolsCsv(csv, existingItemsById = emptyMap(), existingSetsById = emptyMap())

        assertEquals(0, report.validItems.size)
        assertTrue(report.rowErrors.single().message.contains("setId"))
    }

    @Test
    fun blankSetIdAndSetNameLeavesTheItemUnassigned() {
        val csv = listOf(
            HEADER_ROW,
            row(id = "id-1", name = "Standalone hook")
        ).joinToString("\n")

        val report = parseToolsCsv(csv, existingItemsById = emptyMap(), existingSetsById = emptyMap())

        assertEquals(null, report.validItems.single().setId)
        assertEquals(emptyList<ToolSet>(), report.newSets)
    }
}
