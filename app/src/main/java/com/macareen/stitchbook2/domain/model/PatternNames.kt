package com.macareen.stitchbook2.domain.model

/** Consistent names for pattern projects and automatically created PDF guides. */
object PatternNames {
    fun pdf(fileName: String?, fallback: String = "Pattern"): String {
        val name = fileName?.trim().orEmpty()
        val stem = name.substringBeforeLast('.', name).trim()
        return stem.ifBlank { fallback.trim().ifBlank { "Pattern" } }
    }

    fun title(title: String, pdfFileName: String?): String = title.trim().ifBlank { pdf(pdfFileName) }

    fun guide(title: String, size: String, pdfFileName: String? = null): String =
        "${title(title, pdfFileName)} \u00b7 ${size.trim()}"
}
