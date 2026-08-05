package com.macareen.stitchbook2.data.parsing

import com.macareen.stitchbook2.domain.parsing.ExtractedLine
import com.macareen.stitchbook2.domain.parsing.SourceReference
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream

/**
 * Builds a small, entirely original, hand-written PDF for testing
 * [PdfBoxTextExtractor] -- deliberately not a real (copyrighted) pattern PDF,
 * per AGENTS.md's prohibition on committing pattern content. Two pages, a
 * handful of lines each, generated on the fly with PdfBox-Android's own
 * writer so no binary test-resource file needs to be committed either.
 */
internal object PdfFixtures {

    private const val LEFT_MARGIN = 72f
    private const val TOP_MARGIN = 700f
    private const val LINE_HEIGHT = 20f
    private const val FONT_SIZE = 12f

    private val pageOneLines = listOf(
        "Section: Body",
        "Cast on 80 stitches.",
        "Row 1: Knit all stitches."
    )

    private val pageTwoLines = listOf(
        "Row 2: Purl all stitches.",
        "Repeat rows 1 and 2 four times."
    )

    /** The lines [buildTwoPageSyntheticPatternPdf] should be extracted back into. */
    val expectedLines: List<ExtractedLine> =
        pageOneLines.mapIndexed { index, text ->
            ExtractedLine(text, SourceReference(pageNumber = 1, lineNumber = index + 1))
        } + pageTwoLines.mapIndexed { index, text ->
            ExtractedLine(text, SourceReference(pageNumber = 2, lineNumber = index + 1))
        }

    const val EXPECTED_PAGE_COUNT = 2

    fun buildTwoPageSyntheticPatternPdf(): ByteArray {
        PDDocument().use { document ->
            addPage(document, pageOneLines)
            addPage(document, pageTwoLines)
            val output = ByteArrayOutputStream()
            document.save(output)
            return output.toByteArray()
        }
    }

    private fun addPage(document: PDDocument, lines: List<String>) {
        val page = PDPage()
        document.addPage(page)
        PDPageContentStream(document, page).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, FONT_SIZE)
            stream.newLineAtOffset(LEFT_MARGIN, TOP_MARGIN)
            lines.forEachIndexed { index, line ->
                if (index > 0) {
                    stream.newLineAtOffset(0f, -LINE_HEIGHT)
                }
                stream.showText(line)
            }
            stream.endText()
        }
    }
}
