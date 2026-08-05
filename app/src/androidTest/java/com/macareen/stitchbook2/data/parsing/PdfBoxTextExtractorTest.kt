package com.macareen.stitchbook2.data.parsing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real PdfBox-Android extraction path end to end against a
 * synthetic, hand-written fixture PDF (see [PdfFixtures]) -- this needs the
 * Android runtime's `AssetManager` (via [PDFBoxResourceLoader]) and so cannot
 * run as a plain JVM unit test; see [PdfBoxTextExtractor]'s doc comment.
 */
@RunWith(AndroidJUnit4::class)
class PdfBoxTextExtractorTest {

    private lateinit var extractor: PdfBoxTextExtractor

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        extractor = PdfBoxTextExtractor(context)
    }

    @Test
    fun extractsEachLineWithItsPageAndLineNumber() {
        val bytes = PdfFixtures.buildTwoPageSyntheticPatternPdf()

        val document = extractor.extract(ByteArrayInputStream(bytes))

        assertEquals(PdfFixtures.EXPECTED_PAGE_COUNT, document.pageCount)
        assertEquals(PdfFixtures.expectedLines, document.lines)
    }

    @Test
    fun pagesWithoutTextIsEmptyForAFixtureWithATextLayer() {
        val bytes = PdfFixtures.buildTwoPageSyntheticPatternPdf()

        val document = extractor.extract(ByteArrayInputStream(bytes))

        assertEquals(emptyList<Int>(), document.pagesWithoutText())
    }
}
