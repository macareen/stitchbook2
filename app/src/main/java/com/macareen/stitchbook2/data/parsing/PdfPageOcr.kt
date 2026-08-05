package com.macareen.stitchbook2.data.parsing

import android.graphics.Bitmap

/**
 * On-device OCR for one rendered PDF page, used only as a fallback by
 * [PdfBoxTextExtractor] when a page has no digital text layer at all (see
 * ROADMAP.md's "OCR only if no text layer" pipeline). A `Bitmap`-typed
 * contract lives in the data layer rather than domain, since OCR inherently
 * operates on rendered pixels -- there is no portable domain concept to
 * express it in terms of instead, unlike `PdfTextExtractor`'s `InputStream`.
 *
 * Returns recognized text as an ordered list of lines; an empty list means
 * no text was recognized (a genuinely blank page, or a page OCR could not
 * make sense of) -- this mirrors `PdfTextExtractor`'s own "a page with no
 * extractable text simply contributes no lines" rule rather than failing.
 */
interface PdfPageOcr {
    suspend fun recognizeText(page: Bitmap): List<String>
}
