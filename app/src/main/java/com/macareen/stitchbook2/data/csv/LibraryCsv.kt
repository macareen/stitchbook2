package com.macareen.stitchbook2.data.csv

import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.normalizedLibraryItemTags
import com.macareen.stitchbook2.domain.model.normalizedLibraryItemTitle
import java.util.UUID

/**
 * Stitchbook Library CSV schema, version 1. Column order and names are the
 * public contract for this format -- do not reorder or rename a column
 * without bumping [LIBRARY_CSV_SCHEMA_VERSION] and updating this doc
 * comment and [libraryCsvTemplate].
 *
 * Columns: id, title, craft, author, sourceUrl, tags, notes, bookmarked
 *
 * - `id`: stable identifier. Blank on import means "create a new item"; an
 *   id matching an existing item's id means "update that item in place,
 *   preserving its original createdAt" -- the same duplicate-handling
 *   strategy Stash and Tools CSV already use: match by id, last import
 *   wins, re-importing a previously exported (and possibly hand-edited)
 *   file is always safe.
 * - `craft`: one of KNITTING, CROCHET, TUNISIAN_CROCHET, LOOM_KNITTING,
 *   OTHER (case-insensitive).
 * - `tags`: comma-joined within the one column (an individual tag can
 *   never itself contain a comma, per [normalizedLibraryItemTags]), quoted
 *   automatically by RFC 4180 escaping since the joined value contains
 *   literal commas.
 * - `bookmarked`: "true" or "false"; anything else is treated as false.
 * - Every other column is free text; blank means absent (null).
 * - The PDF attachment (`pdfUri`/`pdfFileName`/`pdfLastViewedPage`) is
 *   deliberately not a CSV column -- a `content://` URI is tied to this
 *   device's SAF permission grant and isn't portable the way the rest of
 *   this metadata is. Updating an existing item by `id` always preserves
 *   its current attachment untouched; only the in-app Library form
 *   attaches or changes a PDF.
 *
 * A malformed row is reported and skipped -- it never discards or corrupts
 * the other, valid rows in the same import.
 */
const val LIBRARY_CSV_SCHEMA_VERSION = 1

private val CSV_HEADER = listOf("id", "title", "craft", "author", "sourceUrl", "tags", "notes", "bookmarked")

data class LibraryCsvRowError(val rowNumber: Int, val message: String)

data class LibraryCsvImportReport(
    val validItems: List<LibraryItem>,
    val rowErrors: List<LibraryCsvRowError>
) {
    val importedCount: Int get() = validItems.size
    val hasErrors: Boolean get() = rowErrors.isNotEmpty()
}

fun libraryItemsToCsv(items: List<LibraryItem>): String {
    val builder = StringBuilder()
    builder.append(CSV_HEADER.joinToString(",") { it.csvEscape() }).append("\r\n")
    items.forEach { item ->
        builder.append(
            listOf(
                item.id,
                item.title,
                item.craft.storageValue,
                item.author.orEmpty(),
                item.sourceUrl.orEmpty(),
                item.tags.joinToString(","),
                item.notes.orEmpty(),
                item.bookmarked.toString()
            ).joinToString(",") { it.csvEscape() }
        ).append("\r\n")
    }
    return builder.toString()
}

/** A single-example-row CSV a user can download, edit, and re-import as a starting point. */
fun libraryCsvTemplate(): String = libraryItemsToCsv(
    listOf(
        LibraryItem(
            id = "",
            title = "Everyday Cardigan",
            craft = Craft.KNITTING,
            author = "Jane Designer",
            sourceUrl = "https://example.com/patterns/everyday-cardigan",
            tags = listOf("cardigan", "worsted"),
            notes = "Example row -- replace or delete before importing",
            bookmarked = false,
            createdAt = 0,
            updatedAt = 0
        )
    )
)

fun parseLibraryCsv(
    csv: String,
    existingItemsById: Map<String, LibraryItem>,
    newId: () -> String = { UUID.randomUUID().toString() },
    now: () -> Long = System::currentTimeMillis
): LibraryCsvImportReport {
    val rows = parseCsvRows(csv)
    if (rows.isEmpty()) return LibraryCsvImportReport(emptyList(), emptyList())

    val columnIndex = rows.first().mapIndexed { index, value -> value.trim() to index }.toMap()
    val missingColumns = CSV_HEADER.filterNot { columnIndex.containsKey(it) }
    if (missingColumns.isNotEmpty()) {
        return LibraryCsvImportReport(
            emptyList(),
            listOf(
                LibraryCsvRowError(
                    rowNumber = 1,
                    message = "Missing required column(s): ${missingColumns.joinToString(", ")}"
                )
            )
        )
    }

    val validItems = mutableListOf<LibraryItem>()
    val errors = mutableListOf<LibraryCsvRowError>()

    rows.drop(1).forEachIndexed { offset, row ->
        val rowNumber = offset + 2 // 1-indexed; row 1 is the header
        if (row.all { it.isBlank() }) return@forEachIndexed

        fun cell(name: String): String = columnIndex[name]?.let { row.getOrNull(it) }?.trim().orEmpty()

        val title = normalizedLibraryItemTitle(cell("title"))
        if (title == null) {
            errors += LibraryCsvRowError(rowNumber, "Missing required \"title\".")
            return@forEachIndexed
        }

        val craftRaw = cell("craft")
        val craft = Craft.entries.firstOrNull { it.storageValue.equals(craftRaw, ignoreCase = true) }
        if (craft == null) {
            errors += LibraryCsvRowError(
                rowNumber,
                "Unrecognized craft \"$craftRaw\". Expected one of " +
                    Craft.entries.joinToString(", ") { it.storageValue } + "."
            )
            return@forEachIndexed
        }

        val idCell = cell("id")
        val id = idCell.ifBlank { newId() }
        val existing = existingItemsById[id]
        val timestamp = now()

        validItems += LibraryItem(
            id = id,
            title = title,
            craft = craft,
            author = cell("author").ifBlank { null },
            sourceUrl = cell("sourceUrl").ifBlank { null },
            tags = normalizedLibraryItemTags(cell("tags").split(",")),
            notes = cell("notes").ifBlank { null },
            bookmarked = cell("bookmarked").equals("true", ignoreCase = true),
            createdAt = existing?.createdAt ?: timestamp,
            updatedAt = timestamp,
            pdfUri = existing?.pdfUri,
            pdfFileName = existing?.pdfFileName,
            pdfLastViewedPage = existing?.pdfLastViewedPage
        )
    }

    return LibraryCsvImportReport(validItems, errors)
}
