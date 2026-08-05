package com.macareen.stitchbook2.domain.parsing

/**
 * Points back at one line of one page in the original PDF a piece of
 * extracted or parsed text came from. Both values are 1-based so they can be
 * shown to the user directly (see ROADMAP.md's Phase 12 "source-page/region
 * references" requirement and EXECUTION_ENGINE_SPEC.md's "source references").
 */
data class SourceReference(val pageNumber: Int, val lineNumber: Int) {
    init {
        require(pageNumber >= 1) { "Page number must be 1 or greater." }
        require(lineNumber >= 1) { "Line number must be 1 or greater." }
    }
}

/** One line of extracted text and where it came from. */
data class ExtractedLine(val text: String, val source: SourceReference) {
    init {
        require(text.isNotBlank()) { "Extracted line text must not be blank." }
    }
}

/**
 * The result of extracting digital text from a PDF. [pageCount] is always the
 * document's true page count, even for pages that contributed no lines (for
 * example a scanned image page with no text layer) -- later parsing stages
 * need that distinction to decide whether OCR fallback is required for a
 * given page, per ROADMAP.md's Slice 6 pipeline.
 */
data class ExtractedDocument(val pageCount: Int, val lines: List<ExtractedLine>) {
    init {
        require(pageCount >= 0) { "Page count must not be negative." }
        require(lines.all { it.source.pageNumber <= pageCount }) {
            "An extracted line's page number must not exceed the document's page count."
        }
    }

    /** True when no page in the document yielded any extracted text. */
    val hasNoExtractableText: Boolean get() = lines.isEmpty()

    /** Pages (1-based) that contributed no extracted lines. */
    fun pagesWithoutText(): List<Int> {
        val pagesWithText = lines.map { it.source.pageNumber }.toSet()
        return (1..pageCount).filterNot { it in pagesWithText }
    }
}
