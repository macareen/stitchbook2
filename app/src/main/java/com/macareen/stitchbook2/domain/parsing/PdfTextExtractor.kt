package com.macareen.stitchbook2.domain.parsing

import java.io.IOException
import java.io.InputStream

/**
 * Extracts a PDF's digital text layer, with page/line provenance, so a later
 * deterministic parsing stage (ROADMAP.md's "Parser foundation" item) can
 * turn it into a reviewable Draft. Takes a plain [InputStream] rather than a
 * `Uri`/`Context` so the contract stays platform-agnostic per
 * ARCHITECTURE.md's "platform types ... should not leak unnecessarily into
 * domain models" -- opening the PDF's `content://` URI is a caller concern.
 *
 * This step never invents text: a page with no extractable text layer simply
 * contributes no lines (see [ExtractedDocument.pagesWithoutText]) rather than
 * failing the whole document, since OCR fallback for those pages is separate,
 * later work.
 */
interface PdfTextExtractor {
    /** @throws PdfTextExtractionException if [input] is not a readable PDF. */
    suspend fun extract(input: InputStream): ExtractedDocument
}

class PdfTextExtractionException(message: String, cause: Throwable? = null) : IOException(message, cause)
