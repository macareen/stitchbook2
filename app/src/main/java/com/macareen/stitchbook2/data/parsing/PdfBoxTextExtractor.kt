package com.macareen.stitchbook2.data.parsing

import android.content.Context
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.ExtractedLine
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractionException
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.parsing.SourceReference
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException

/**
 * [PdfTextExtractor] backed by PdfBox-Android, with on-device OCR
 * ([PdfPageOcr]) as a fallback for any page that has no digital text layer
 * at all -- ROADMAP.md's "PDF -> extract digital text -> OCR only if no
 * text layer" pipeline. PdfBox-Android reads its encoding/glyph resources
 * through [PDFBoxResourceLoader]'s `AssetManager`, so it requires a real
 * Android [Context] and cannot run under a plain JVM unit test -- it is
 * exercised by an instrumented test instead (see PdfBoxTextExtractorTest),
 * matching this project's existing pattern for Android-runtime-only
 * behavior (ARCHITECTURE.md's Compose instrumented-test limitation).
 */
class PdfBoxTextExtractor(
    context: Context,
    private val pdfPageOcr: PdfPageOcr
) : PdfTextExtractor {

    init {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    override suspend fun extract(input: InputStream): ExtractedDocument {
        val document = try {
            PDDocument.load(input)
        } catch (e: IOException) {
            throw PdfTextExtractionException("Unable to read PDF.", e)
        }
        document.use { doc ->
            val pageCount = doc.numberOfPages
            val lines = mutableListOf<ExtractedLine>()
            val pagesNeedingOcr = mutableListOf<Int>()

            for (pageNumber in 1..pageCount) {
                val stripper = PDFTextStripper()
                stripper.startPage = pageNumber
                stripper.endPage = pageNumber
                val pageText = try {
                    stripper.getText(doc)
                } catch (e: IOException) {
                    throw PdfTextExtractionException("Unable to extract text from page $pageNumber.", e)
                }
                val pageLines = pageText.split("\n").mapIndexedNotNull { index, rawLine ->
                    val text = rawLine.trim()
                    text.takeIf { it.isNotEmpty() }?.let {
                        ExtractedLine(text = it, source = SourceReference(pageNumber = pageNumber, lineNumber = index + 1))
                    }
                }
                if (pageLines.isEmpty()) {
                    pagesNeedingOcr += pageNumber
                } else {
                    lines += pageLines
                }
            }

            if (pagesNeedingOcr.isNotEmpty()) {
                val renderer = PDFRenderer(doc)
                for (pageNumber in pagesNeedingOcr) {
                    lines += ocrPage(renderer, pageNumber)
                }
            }

            return ExtractedDocument(
                pageCount = pageCount,
                lines = lines.sortedWith(compareBy({ it.source.pageNumber }, { it.source.lineNumber }))
            )
        }
    }

    /**
     * A rendering or recognition failure for one page is not fatal to the
     * whole document -- it simply contributes no lines, the same as a page
     * digital extraction already found nothing on, per [PdfPageOcr]'s own
     * "no text recognized" contract.
     */
    private suspend fun ocrPage(renderer: PDFRenderer, pageNumber: Int): List<ExtractedLine> {
        val bitmap = try {
            renderer.renderImageWithDPI(pageNumber - 1, OCR_RENDER_DPI)
        } catch (_: IOException) {
            null
        } ?: return emptyList()

        val recognizedLines = try {
            pdfPageOcr.recognizeText(bitmap)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }

        return recognizedLines.mapIndexedNotNull { index, rawLine ->
            val text = rawLine.trim()
            text.takeIf { it.isNotEmpty() }?.let {
                ExtractedLine(text = it, source = SourceReference(pageNumber = pageNumber, lineNumber = index + 1))
            }
        }
    }

    private companion object {
        const val OCR_RENDER_DPI = 200f
    }
}
