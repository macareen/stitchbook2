package com.macareen.stitchbook2.data.csv

import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.model.normalizedToolItemName
import java.util.UUID

/**
 * Stitchbook Tools CSV schema, version 1. Column order and names are the
 * public contract for this format -- do not reorder or rename a column
 * without bumping [TOOLS_CSV_SCHEMA_VERSION] and updating this doc comment
 * and [toolsCsvTemplate].
 *
 * One row per individual [ToolItem]; a grouped [ToolSet] is never its own
 * row. Columns: id, name, category, brand, material, sizeMetricMm,
 * sizeLabel, lengthMm, statedCableLengthMm, cableLengthDefinition,
 * approximateAssembledLengthMm, connectorFamily, compatibilityNotes,
 * quantity, storageLocation, notes, setId, setName
 *
 * - `id`: stable identifier. Blank on import means "create a new item"; an
 *   id matching an existing item's id means "update that item in place,
 *   preserving its original createdAt" -- the same duplicate-handling
 *   strategy [parseStashCsv] uses.
 * - `category`: one of [ToolCategory]'s storage values (case-insensitive).
 * - `quantity`: a non-negative whole number. Required.
 * - `sizeMetricMm`, `lengthMm`, `statedCableLengthMm`,
 *   `approximateAssembledLengthMm`: a number, or blank.
 * - `setId`/`setName` together reconstitute grouped-set membership without a
 *   separate set row: `setId` is authoritative when present and must match
 *   either an already-persisted set or another row's `setId` in the same
 *   import that resolved a set by name. When `setId` is blank but `setName`
 *   is not, a set is resolved by case-insensitive name match against
 *   existing sets, or created once per distinct name and shared by every
 *   row using that same name -- so hand-typing the same `setName` on
 *   several rows groups them without needing to already know a set's id.
 *   Both blank means the item is not part of any set.
 * - Every other column is free text; blank means absent (null).
 *
 * A malformed row is reported and skipped -- it never discards or corrupts
 * the other, valid rows in the same import.
 */
const val TOOLS_CSV_SCHEMA_VERSION = 1

private val CSV_HEADER = listOf(
    "id", "name", "category", "brand", "material", "sizeMetricMm",
    "sizeLabel", "lengthMm", "statedCableLengthMm", "cableLengthDefinition",
    "approximateAssembledLengthMm", "connectorFamily", "compatibilityNotes",
    "quantity", "storageLocation", "notes", "setId", "setName"
)

data class ToolsCsvRowError(val rowNumber: Int, val message: String)

data class ToolsCsvImportReport(
    val validItems: List<ToolItem>,
    val newSets: List<ToolSet>,
    val rowErrors: List<ToolsCsvRowError>
) {
    val importedCount: Int get() = validItems.size
    val hasErrors: Boolean get() = rowErrors.isNotEmpty()
}

fun toolItemsToCsv(items: List<ToolItem>, setsById: Map<String, ToolSet>): String {
    val builder = StringBuilder()
    builder.append(CSV_HEADER.joinToString(",") { it.csvEscape() }).append("\r\n")
    items.forEach { item ->
        builder.append(
            listOf(
                item.id,
                item.name,
                item.category.storageValue,
                item.brand.orEmpty(),
                item.material.orEmpty(),
                item.sizeMetricMm?.let { formatCsvNumber(it) }.orEmpty(),
                item.sizeLabel.orEmpty(),
                item.lengthMm?.let { formatCsvNumber(it) }.orEmpty(),
                item.statedCableLengthMm?.let { formatCsvNumber(it) }.orEmpty(),
                item.cableLengthDefinition.orEmpty(),
                item.approximateAssembledLengthMm?.let { formatCsvNumber(it) }.orEmpty(),
                item.connectorFamily.orEmpty(),
                item.compatibilityNotes.orEmpty(),
                item.quantity.toString(),
                item.storageLocation.orEmpty(),
                item.notes.orEmpty(),
                item.setId.orEmpty(),
                item.setId?.let { setsById[it]?.name }.orEmpty()
            ).joinToString(",") { it.csvEscape() }
        ).append("\r\n")
    }
    return builder.toString()
}

/** A single-example-row CSV a user can download, edit, and re-import as a starting point. */
fun toolsCsvTemplate(): String = toolItemsToCsv(
    listOf(
        ToolItem(
            id = "",
            name = "4.5 mm crochet hook",
            category = ToolCategory.CROCHET_HOOK,
            brand = "Clover",
            material = "Bamboo",
            sizeMetricMm = 4.5,
            sizeLabel = "US 7",
            lengthMm = null,
            statedCableLengthMm = null,
            cableLengthDefinition = null,
            approximateAssembledLengthMm = null,
            connectorFamily = null,
            compatibilityNotes = null,
            quantity = 1,
            storageLocation = "Hook case",
            notes = "Example row -- replace or delete before importing",
            setId = null,
            createdAt = 0,
            updatedAt = 0
        )
    ),
    setsById = emptyMap()
)

fun parseToolsCsv(
    csv: String,
    existingItemsById: Map<String, ToolItem>,
    existingSetsById: Map<String, ToolSet>,
    newId: () -> String = { UUID.randomUUID().toString() },
    now: () -> Long = System::currentTimeMillis
): ToolsCsvImportReport {
    val rows = parseCsvRows(csv)
    if (rows.isEmpty()) return ToolsCsvImportReport(emptyList(), emptyList(), emptyList())

    val columnIndex = rows.first().mapIndexed { index, value -> value.trim() to index }.toMap()
    val missingColumns = CSV_HEADER.filterNot { columnIndex.containsKey(it) }
    if (missingColumns.isNotEmpty()) {
        return ToolsCsvImportReport(
            emptyList(),
            emptyList(),
            listOf(
                ToolsCsvRowError(
                    rowNumber = 1,
                    message = "Missing required column(s): ${missingColumns.joinToString(", ")}"
                )
            )
        )
    }

    val validItems = mutableListOf<ToolItem>()
    val newSets = mutableListOf<ToolSet>()
    val errors = mutableListOf<ToolsCsvRowError>()
    val setIdsByName = existingSetsById.values.associateTo(mutableMapOf()) { it.name.lowercase() to it.id }
    val knownSetIds = existingSetsById.keys.toMutableSet()

    rows.drop(1).forEachIndexed { offset, row ->
        val rowNumber = offset + 2 // 1-indexed; row 1 is the header
        if (row.all { it.isBlank() }) return@forEachIndexed

        fun cell(name: String): String = columnIndex[name]?.let { row.getOrNull(it) }?.trim().orEmpty()

        val name = normalizedToolItemName(cell("name"))
        if (name == null) {
            errors += ToolsCsvRowError(rowNumber, "Missing required \"name\".")
            return@forEachIndexed
        }

        val categoryRaw = cell("category")
        val category = ToolCategory.entries.firstOrNull { it.storageValue.equals(categoryRaw, ignoreCase = true) }
        if (category == null) {
            errors += ToolsCsvRowError(
                rowNumber,
                "Unrecognized category \"$categoryRaw\". Expected one of " +
                    ToolCategory.entries.joinToString(", ") { it.storageValue } + "."
            )
            return@forEachIndexed
        }

        val quantityRaw = cell("quantity")
        val quantity = quantityRaw.toIntOrNull()
        if (quantity == null || quantity < 0) {
            errors += ToolsCsvRowError(rowNumber, "Invalid quantity \"$quantityRaw\" -- must be a non-negative whole number.")
            return@forEachIndexed
        }

        val numericFields = mapOf(
            "sizeMetricMm" to cell("sizeMetricMm"),
            "lengthMm" to cell("lengthMm"),
            "statedCableLengthMm" to cell("statedCableLengthMm"),
            "approximateAssembledLengthMm" to cell("approximateAssembledLengthMm")
        )
        val parsedNumbers = mutableMapOf<String, Double?>()
        var hasInvalidNumber = false
        numericFields.forEach { (field, raw) ->
            if (raw.isBlank()) {
                parsedNumbers[field] = null
            } else {
                val parsed = raw.toDoubleOrNull()
                if (parsed == null) {
                    errors += ToolsCsvRowError(rowNumber, "Invalid $field \"$raw\" -- must be a number.")
                    hasInvalidNumber = true
                } else {
                    parsedNumbers[field] = parsed
                }
            }
        }
        if (hasInvalidNumber) return@forEachIndexed

        val setIdCell = cell("setId")
        val setNameCell = cell("setName")
        val setId = when {
            setIdCell.isNotBlank() -> {
                if (setIdCell !in knownSetIds) {
                    errors += ToolsCsvRowError(rowNumber, "Unknown setId \"$setIdCell\".")
                    return@forEachIndexed
                }
                setIdCell
            }

            setNameCell.isNotBlank() -> {
                setIdsByName.getOrPut(setNameCell.lowercase()) {
                    val generatedId = newId()
                    val timestamp = now()
                    newSets += ToolSet(
                        id = generatedId,
                        name = setNameCell,
                        brand = null,
                        notes = null,
                        createdAt = timestamp,
                        updatedAt = timestamp
                    )
                    knownSetIds += generatedId
                    generatedId
                }
            }

            else -> null
        }

        val idCell = cell("id")
        val id = idCell.ifBlank { newId() }
        val existing = existingItemsById[id]
        val timestamp = now()

        validItems += ToolItem(
            id = id,
            name = name,
            category = category,
            brand = cell("brand").ifBlank { null },
            material = cell("material").ifBlank { null },
            sizeMetricMm = parsedNumbers["sizeMetricMm"],
            sizeLabel = cell("sizeLabel").ifBlank { null },
            lengthMm = parsedNumbers["lengthMm"],
            statedCableLengthMm = parsedNumbers["statedCableLengthMm"],
            cableLengthDefinition = cell("cableLengthDefinition").ifBlank { null },
            approximateAssembledLengthMm = parsedNumbers["approximateAssembledLengthMm"],
            connectorFamily = cell("connectorFamily").ifBlank { null },
            compatibilityNotes = cell("compatibilityNotes").ifBlank { null },
            quantity = quantity,
            storageLocation = cell("storageLocation").ifBlank { null },
            notes = cell("notes").ifBlank { null },
            setId = setId,
            createdAt = existing?.createdAt ?: timestamp,
            updatedAt = timestamp
        )
    }

    return ToolsCsvImportReport(validItems, newSets, errors)
}
