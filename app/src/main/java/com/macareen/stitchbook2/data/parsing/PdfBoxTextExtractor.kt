package com.macareen.stitchbook2.data.parsing

import android.content.Context
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.ExtractedLine
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractionException
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.parsing.SourceReference
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.io.InputStream

/**
 * [PdfTextExtractor] backed by PdfBox-Android. PdfBox-Android reads its
 * encoding/glyph resources through [PDFBoxResourceLoader]'s `AssetManager`,
 * so it requires a real Android [Context] and cannot run under a plain JVM
 * unit test -- it is exercised by an instrumented test instead (see
 * PdfBoxTextExtractorTest), matching this project's existing pattern for
 * Android-runtime-only behavior (ARCHITECTURE.md's Compose instrumented-test
 * limitation).
 */
class PdfBoxTextExtractor(context: Context) : PdfTextExtractor {

    init {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    override fun extract(input: InputStream): ExtractedDocument {
        val document = try {
            PDDocument.load(input)
        } catch (e: IOException) {
            throw PdfTextExtractionException("Unable to read PDF.", e)
        }
        document.use { doc ->
            val pageCount = doc.numberOfPages
            val lines = mutableListOf<ExtractedLine>()
            for (pageNumber in 1..pageCount) {
                val stripper = PDFTextStripper()
                stripper.startPage = pageNumber
                stripper.endPage = pageNumber
                val pageText = try {
                    stripper.getText(doc)
                } catch (e: IOException) {
                    throw PdfTextExtractionException("Unable to extract text from page $pageNumber.", e)
                }
                pageText.split("\n").forEachIndexed { index, rawLine ->
                    val text = rawLine.trim()
                    if (text.isNotEmpty()) {
                        lines += ExtractedLine(
                            text = text,
                            source = SourceReference(pageNumber = pageNumber, lineNumber = index + 1)
                        )
                    }
                }
            }
            return ExtractedDocument(pageCount = pageCount, lines = lines)
        }
    }
}
