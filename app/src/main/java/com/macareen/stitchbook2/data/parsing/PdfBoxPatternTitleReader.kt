package com.macareen.stitchbook2.data.parsing

import android.content.Context
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.ExtractedLine
import com.macareen.stitchbook2.domain.parsing.PatternDocumentTitle
import com.macareen.stitchbook2.domain.parsing.SourceReference
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

/** Only the first digital page is inspected during explicit folder import; no OCR or network. */
class PdfBoxPatternTitleReader(context: Context) {
    init { if (!PDFBoxResourceLoader.isReady()) PDFBoxResourceLoader.init(context.applicationContext) }
    fun read(input: InputStream): String? = PDDocument.load(input).use { document ->
        if (document.numberOfPages == 0) return@use null
        val stripper = PDFTextStripper().apply { startPage = 1; endPage = 1 }
        val lines = stripper.getText(document).lineSequence().mapIndexedNotNull { index, line ->
            line.trim().takeIf(String::isNotEmpty)?.let { ExtractedLine(it, SourceReference(1, index + 1)) }
        }.toList()
        PatternDocumentTitle.find(ExtractedDocument(document.numberOfPages, lines, document.documentInformation.title))
    }
}
