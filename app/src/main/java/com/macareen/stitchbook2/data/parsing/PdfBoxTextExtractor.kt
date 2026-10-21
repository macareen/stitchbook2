package com.macareen.stitchbook2.data.parsing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.macareen.stitchbook2.domain.parsing.PdfSourceSelection
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.ExtractedLine
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractionException
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.parsing.SourceReference
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

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
                metadataTitle = doc.documentInformation.title,
                lines = lines.sortedWith(compareBy({ it.source.pageNumber }, { it.source.lineNumber }))
            )
        }
    }

    override suspend fun extract(input: InputStream, selection: PdfSourceSelection): ExtractedDocument {
        try {
            PDDocument.load(input).use { doc ->
                selection.validatePageCount(doc.numberOfPages)
                val renderer = PDFRenderer(doc)
                val lines = mutableListOf<ExtractedLine>()
                val lineNumbers = mutableMapOf<Int, Int>()
                for (region in selection.regions) {
                    val page = doc.getPage(region.page - 1)
                    // Region extraction and the preview use displayed crop-box coordinates.
                    // Refuse unsupported rotations instead of selecting a different area silently.
                    if (!region.isWholePage && page.rotation != 0) {
                        throw PdfTextExtractionException("Region review requires an unrotated page.")
                    }
                    val width = page.cropBox.width
                    val height = page.cropBox.height
                    val rawText = if (region.isWholePage) {
                        PDFTextStripper().apply {
                            startPage = region.page; endPage = region.page; sortByPosition = true
                        }.getText(doc)
                    } else {
                        val stripper = PDFTextStripperByArea().apply {
                            sortByPosition = true
                            addRegion("selected", RectF(width * region.left / 100f, height * region.top / 100f,
                                width * region.right / 100f, height * region.bottom / 100f))
                        }
                        stripper.extractRegions(page)
                        stripper.getTextForRegion("selected")
                    }
                    var text = rawText.lines().map(String::trim).filter(String::isNotEmpty)
                    if (text.isEmpty()) {
                        val bitmap = renderer.renderImageWithDPI(region.page - 1, OCR_RENDER_DPI)
                        try {
                            val x = bitmap.width * region.left / 100
                            val y = bitmap.height * region.top / 100
                            val crop = Bitmap.createBitmap(bitmap, x, y,
                                (bitmap.width * region.right / 100 - x).coerceAtLeast(1),
                                (bitmap.height * region.bottom / 100 - y).coerceAtLeast(1))
                            try { text = pdfPageOcr.recognizeText(crop).map(String::trim).filter(String::isNotEmpty) }
                            finally { if (currentCoroutineContext().isActive && crop !== bitmap) crop.recycle() }
                        } finally { if (currentCoroutineContext().isActive) bitmap.recycle() }
                    }
                    if (text.isEmpty() && !region.isWholePage) {
                        throw PdfTextExtractionException("No readable text in the selected region on page ${region.page}. Check for charts or missing instructions.")
                    }
                    text.forEach { value ->
                        val number = (lineNumbers[region.page] ?: 0) + 1
                        lineNumbers[region.page] = number
                        lines += ExtractedLine(value, SourceReference(region.page, number))
                    }
                }
                return ExtractedDocument(doc.numberOfPages, lines, doc.documentInformation.title)
            }
        } catch (error: CancellationException) { throw error
        } catch (error: PdfTextExtractionException) { throw error
        } catch (error: Exception) { throw PdfTextExtractionException("Unable to read selected PDF regions.", error) }
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
