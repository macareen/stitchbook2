package com.macareen.stitchbook2.data.csv

import com.macareen.stitchbook2.domain.model.StashCategory
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.normalizedStashItemName
import java.util.UUID

/**
 * Stitchbook Stash CSV schema, version 1. Column order and names are the
 * public contract for this format -- do not reorder or rename a column
 * without bumping [STASH_CSV_SCHEMA_VERSION] and updating this doc comment
 * and [stashCsvTemplate].
 *
 * Columns: id, name, category, brand, colorway, dyeLot, weightCategory,
 * fiberContent, quantity, unitLabel, yardagePerUnit, notes
 *
 * - `id`: stable identifier. Blank on import means "create a new item";
 *   an id matching an existing item's id means "update that item in place,
 *   preserving its original createdAt" -- this is the whole duplicate
 *   handling strategy: match by id, last import wins, re-importing a
 *   previously exported (and possibly hand-edited) file is always safe.
 * - `category`: one of YARN, NEEDLES_HOOKS, NOTIONS, MATERIALS
 *   (case-insensitive).
 * - `quantity`: a non-negative number. Required.
 * - `yardagePerUnit`: a non-negative number, or blank.
 * - Every other column is free text; blank means absent (null).
 *
 * A malformed row is reported and skipped -- it never discards or corrupts
 * the other, valid rows in the same import.
 */
const val STASH_CSV_SCHEMA_VERSION = 1

private val CSV_HEADER = listOf(
    "id", "name", "category", "brand", "colorway", "dyeLot",
    "weightCategory", "fiberContent", "quantity", "unitLabel",
    "yardagePerUnit", "notes"
)

data class StashCsvRowError(val rowNumber: Int, val message: String)

data class StashCsvImportReport(
    val validItems: List<StashItem>,
    val rowErrors: List<StashCsvRowError>
) {
    val importedCount: Int get() = validItems.size
    val hasErrors: Boolean get() = rowErrors.isNotEmpty()
}

fun stashItemsToCsv(items: List<StashItem>): String {
    val builder = StringBuilder()
    builder.append(CSV_HEADER.joinToString(",") { it.csvEscape() }).append("\r\n")
    items.forEach { item ->
        builder.append(
            listOf(
                item.id,
                item.name,
                item.category.storageValue,
                item.brand.orEmpty(),
                item.colorway.orEmpty(),
                item.dyeLot.orEmpty(),
                item.weightCategory.orEmpty(),
                item.fiberContent.orEmpty(),
                formatCsvNumber(item.quantity),
                item.unitLabel,
                item.yardagePerUnit?.let { formatCsvNumber(it) }.orEmpty(),
                item.notes.orEmpty()
            ).joinToString(",") { it.csvEscape() }
        ).append("\r\n")
    }
    return builder.toString()
}

/** A single-example-row CSV a user can download, edit, and re-import as a starting point. */
fun stashCsvTemplate(): String = stashItemsToCsv(
    listOf(
        StashItem(
            id = "",
            name = "Cascade 220",
            category = StashCategory.YARN,
            brand = "Cascade Yarns",
            colorway = "Ivory",
            dyeLot = "12345",
            weightCategory = "Worsted",
            fiberContent = "100% Peruvian Highland Wool",
            quantity = 6.0,
            unitLabel = "skeins",
            yardagePerUnit = 220.0,
            notes = "Example row -- replace or delete before importing",
            createdAt = 0,
            updatedAt = 0
        )
    )
)

fun parseStashCsv(
    csv: String,
    existingItemsById: Map<String, StashItem>,
    newId: () -> String = { UUID.randomUUID().toString() },
    now: () -> Long = System::currentTimeMillis
): StashCsvImportReport {
    val rows = parseCsvRows(csv)
    if (rows.isEmpty()) return StashCsvImportReport(emptyList(), emptyList())

    val columnIndex = rows.first().mapIndexed { index, value -> value.trim() to index }.toMap()
    val missingColumns = CSV_HEADER.filterNot { columnIndex.containsKey(it) }
    if (missingColumns.isNotEmpty()) {
        return StashCsvImportReport(
            emptyList(),
            listOf(
                StashCsvRowError(
                    rowNumber = 1,
                    message = "Missing required column(s): ${missingColumns.joinToString(", ")}"
                )
            )
        )
    }

    val validItems = mutableListOf<StashItem>()
    val errors = mutableListOf<StashCsvRowError>()

    rows.drop(1).forEachIndexed { offset, row ->
        val rowNumber = offset + 2 // 1-indexed; row 1 is the header
        if (row.all { it.isBlank() }) return@forEachIndexed

        fun cell(name: String): String = columnIndex[name]?.let { row.getOrNull(it) }?.trim().orEmpty()

        val name = normalizedStashItemName(cell("name"))
        if (name == null) {
            errors += StashCsvRowError(rowNumber, "Missing required \"name\".")
            return@forEachIndexed
        }

        val categoryRaw = cell("category")
        val category = StashCategory.entries.firstOrNull { it.storageValue.equals(categoryRaw, ignoreCase = true) }
        if (category == null) {
            errors += StashCsvRowError(
                rowNumber,
                "Unrecognized category \"$categoryRaw\". Expected one of " +
                    StashCategory.entries.joinToString(", ") { it.storageValue } + "."
            )
            return@forEachIndexed
        }

        val quantityRaw = cell("quantity")
        val quantity = quantityRaw.toDoubleOrNull()
        if (quantity == null || quantity < 0) {
            errors += StashCsvRowError(rowNumber, "Invalid quantity \"$quantityRaw\" -- must be a non-negative number.")
            return@forEachIndexed
        }

        val yardageRaw = cell("yardagePerUnit")
        val yardage = if (yardageRaw.isBlank()) null else yardageRaw.toDoubleOrNull()
        if (yardageRaw.isNotBlank() && yardage == null) {
            errors += StashCsvRowError(rowNumber, "Invalid yardagePerUnit \"$yardageRaw\" -- must be a number.")
            return@forEachIndexed
        }
        if (yardage != null && yardage < 0) {
            errors += StashCsvRowError(rowNumber, "Invalid yardagePerUnit \"$yardageRaw\" -- must not be negative.")
            return@forEachIndexed
        }

        val idCell = cell("id")
        val id = idCell.ifBlank { newId() }
        val existing = existingItemsById[id]
        val timestamp = now()

        validItems += StashItem(
            id = id,
            name = name,
            category = category,
            brand = cell("brand").ifBlank { null },
            colorway = cell("colorway").ifBlank { null },
            dyeLot = cell("dyeLot").ifBlank { null },
            weightCategory = cell("weightCategory").ifBlank { null },
            fiberContent = cell("fiberContent").ifBlank { null },
            quantity = quantity,
            unitLabel = cell("unitLabel").ifBlank { "skeins" },
            yardagePerUnit = yardage,
            notes = cell("notes").ifBlank { null },
            createdAt = existing?.createdAt ?: timestamp,
            updatedAt = timestamp
        )
    }

    return StashCsvImportReport(validItems, errors)
}
