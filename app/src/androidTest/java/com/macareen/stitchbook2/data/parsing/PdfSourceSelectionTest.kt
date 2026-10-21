package com.macareen.stitchbook2.data.parsing

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.parsing.*
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PdfSourceSelectionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val noOcr = object : PdfPageOcr {
        override suspend fun recognizeText(page: Bitmap): List<String> = emptyList()
    }

    @Test fun selectedColumnsExcludeClutterAndFollowReviewedOrderWithOriginalPageNumbers() = runBlocking {
        val extractor = PdfBoxTextExtractor(context, noOcr)
        val bytes = PdfFixtures.buildColumnReviewPdf()
        val left = PdfSourceRegion(2, 0, 0, 50, 50)
        val right = PdfSourceRegion(2, 50, 0, 100, 50)
        val expected = listOf("Cast on 20 (24) sts.", "Row 1: Knit.", "Row 2: Purl.", "Bind off.")
        val selected = extractor.extract(ByteArrayInputStream(bytes), PdfSourceSelection(listOf(left, right)))
        assertEquals(expected, selected.lines.map { it.text })
        assertEquals(listOf(2, 2, 2, 2), selected.lines.map { it.source.pageNumber })
        assertEquals(listOf(1, 2, 3, 4), selected.lines.map { it.source.lineNumber })
        val reversed = extractor.extract(ByteArrayInputStream(bytes), PdfSourceSelection(listOf(right, left)))
        assertEquals(expected.drop(2) + expected.take(2), reversed.lines.map { it.text })
        assertEquals(2, selected.pageCount)
    }

    @Test fun ocrReceivesOnlyTheSelectedAreaAndEmptyRegionsAreRefused() = runBlocking {
        var dimensions: Pair<Int, Int>? = null
        val ocr = object : PdfPageOcr {
            override suspend fun recognizeText(page: Bitmap): List<String> {
                dimensions = page.width to page.height
                return listOf("Cast on 24 sts.")
            }
        }
        val bytes = PdfFixtures.buildSyntheticPages(listOf(emptyList()))
        val selection = PdfSourceSelection(listOf(PdfSourceRegion(1, 0, 0, 50, 50)))
        val text = PdfBoxTextExtractor(context, ocr).extract(ByteArrayInputStream(bytes), selection)
        assertEquals("Cast on 24 sts.", text.lines.single().text)
        assertTrue(dimensions!!.first in 800..900)
        assertTrue(dimensions!!.second in 1050..1150)
        try {
            PdfBoxTextExtractor(context, noOcr).extract(ByteArrayInputStream(bytes), selection)
            fail("Empty custom region must not silently disappear")
        } catch (_: PdfTextExtractionException) { }
    }

    @Test fun rotatedPagesAllowWholePageReviewButRejectAmbiguousCustomCoordinates() = runBlocking {
        val extractor = PdfBoxTextExtractor(context, noOcr)
        val original = PdfFixtures.buildSyntheticPages(listOf(listOf("Cast on 24 sts.")))
        val rotated = com.tom_roush.pdfbox.pdmodel.PDDocument.load(original).use { document ->
            document.getPage(0).rotation = 90
            java.io.ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
        assertFalse(extractor.extract(ByteArrayInputStream(rotated), PdfSourceSelection(listOf(PdfSourceRegion(1)))).hasNoExtractableText)
        try {
            extractor.extract(ByteArrayInputStream(rotated), PdfSourceSelection(listOf(PdfSourceRegion(1, right = 50))))
            fail("Unsupported custom coordinates must not silently select a different area")
        } catch (error: PdfTextExtractionException) { assertTrue(error.message!!.contains("unrotated")) }
    }
}
