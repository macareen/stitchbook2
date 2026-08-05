package com.macareen.stitchbook2.data.parsing

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real PdfBox-Android extraction path end to end against
 * synthetic, hand-written fixture PDFs (see [PdfFixtures]) -- this needs the
 * Android runtime's `AssetManager` (via [PDFBoxResourceLoader]) and so cannot
 * run as a plain JVM unit test; see [PdfBoxTextExtractor]'s doc comment.
 */
@RunWith(AndroidJUnit4::class)
class PdfBoxTextExtractorTest {

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
    }

    private fun extractor(ocr: PdfPageOcr = NeverCalledPdfPageOcr): PdfBoxTextExtractor {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return PdfBoxTextExtractor(context, ocr)
    }

    @Test
    fun extractsEachLineWithItsPageAndLineNumber() = runBlocking {
        val bytes = PdfFixtures.buildTwoPageSyntheticPatternPdf()

        val document = extractor().extract(ByteArrayInputStream(bytes))

        assertEquals(PdfFixtures.EXPECTED_PAGE_COUNT, document.pageCount)
        assertEquals(PdfFixtures.expectedLines, document.lines)
    }

    @Test
    fun pagesWithoutTextIsEmptyForAFixtureWithATextLayer() = runBlocking {
        val bytes = PdfFixtures.buildTwoPageSyntheticPatternPdf()

        val document = extractor().extract(ByteArrayInputStream(bytes))

        assertEquals(emptyList<Int>(), document.pagesWithoutText())
    }

    @Test
    fun aPageWithOnlyAnImageFallsBackToOcr() = runBlocking {
        val bytes = PdfFixtures.buildImageOnlyPagePdf()

        val document = extractor(MlKitPdfPageOcr()).extract(ByteArrayInputStream(bytes))

        assertEquals(1, document.pageCount)
        assertTrue(
            "Expected OCR to recognize \"${PdfFixtures.IMAGE_ONLY_PAGE_WORD}\", got: ${document.lines}",
            document.lines.any { it.text.contains(PdfFixtures.IMAGE_ONLY_PAGE_WORD, ignoreCase = true) }
        )
    }
}

/** Fails the test if invoked -- these fixtures have a real digital text layer, so OCR must never run. */
private object NeverCalledPdfPageOcr : PdfPageOcr {
    override suspend fun recognizeText(page: Bitmap): List<String> =
        throw AssertionError("OCR should not run when the page already has a digital text layer")
}
