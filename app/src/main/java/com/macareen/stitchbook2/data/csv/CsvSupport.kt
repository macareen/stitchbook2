package com.macareen.stitchbook2.data.csv

/**
 * RFC 4180 primitives shared by every Stitchbook CSV format (Stash, Tools,
 * ...). Format-specific schemas, headers, and row validation stay in their
 * own files; only the mechanical read/write/escape logic lives here.
 */

/** As an escape sequence, not a literal character, so the source file itself never contains a stray BOM byte sequence (Android Lint's ByteOrderMark check flags that). */
internal const val CSV_UTF8_BOM = "\uFEFF"

internal fun formatCsvNumber(value: Double): String {
    return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}

internal fun String.csvEscape(): String {
    return if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }
}

/**
 * A minimal RFC 4180 reader: quoted fields, embedded commas/newlines inside
 * quotes, and doubled `""` as an escaped quote. Strips a leading UTF-8 BOM
 * (common from spreadsheet-app exports) before parsing.
 */
internal fun parseCsvRows(csv: String): List<List<String>> {
    val text = csv.removePrefix(CSV_UTF8_BOM)
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0

    fun endField() {
        row.add(field.toString())
        field.clear()
    }

    fun endRow() {
        endField()
        rows.add(row)
        row = mutableListOf()
    }

    while (i < text.length) {
        val c = text[i]
        if (inQuotes) {
            when {
                c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> inQuotes = false
                else -> field.append(c)
            }
        } else {
            when (c) {
                '"' -> inQuotes = true
                ',' -> endField()
                '\r' -> if (i + 1 >= text.length || text[i + 1] != '\n') endRow()
                '\n' -> endRow()
                else -> field.append(c)
            }
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()

    return rows.filterNot { it.size == 1 && it[0].isBlank() }
}
