package com.macareen.stitchbook2.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.parsing.PdfFixtures
import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.domain.parsing.*
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PdfSourceReviewDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun selectingColumnsInvalidatesConfirmationAndKeepsTheOriginalVisible() = check(false, 1f)
    @Test fun sourceReviewWorksInDarkMode() = check(true, 1f)
    @Test fun sourceReviewWorksAtLargeText() = check(true, 1.8f)

    private fun check(dark: Boolean, fontScale: Float) {
        val originalScale = compose.activity.resources.configuration.fontScale
        if (fontScale > 1f) {
            assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            setScaleAndWait(fontScale)
        }
        try { checkContent(dark, fontScale) }
        finally { if (fontScale > 1f) setScaleAndWait(originalScale) }
    }

    private fun setScaleAndWait(scale: Float) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put system font_scale $scale")).use { it.readBytes() }
        compose.waitUntil(10_000) {
            runCatching { compose.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                kotlin.math.abs(compose.activity.resources.configuration.fontScale - scale) < .05f }.getOrDefault(false)
        }
    }

    private fun checkContent(dark: Boolean, fontScale: Float) {
        PDFBoxResourceLoader.init(compose.activity)
        val bytes = PdfFixtures.buildColumnReviewPdf()
        val original = ExtractedDocument(2, listOf(ExtractedLine("Cast on 20 (24) sts.", SourceReference(2, 1))))
        val pattern = LibraryItem("pattern", "Sample wrap", Craft.KNITTING, null, null, emptyList(), null, false, 0, 0)
        var state by mutableStateOf(PdfSourceReview(pattern, "M", bytes, original, listOf(PdfSourceRegion(2))))
        var created = 0
        compose.setContent { StitchbookTheme(darkTheme = dark) {
            PdfSourceReviewDialog(state, { state = state.copy(regions = it, preview = null) }, {
                state = state.copy(preview = ReviewedPdfSource(original, original, PdfSourceSelection(state.regions)))
            }, { created++ }, {})
        } }
        compose.onNodeWithTag("pdf-source-create").assertIsNotEnabled()
        compose.onNodeWithTag("pdf-source-review").performScrollToNode(hasText("Adjust area"))
        compose.onNodeWithText("Adjust area").performClick()
        compose.onNodeWithTag("pdf-source-review").performScrollToIndex(0)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Original PDF page with included areas highlighted", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Original PDF page with included areas highlighted", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Reset zoom").assertIsNotEnabled()
        compose.onNodeWithText("Zoom in").performClick()
        compose.onNodeWithText("Reset zoom").assertIsEnabled().performClick()
        compose.onNodeWithText("Zoom out").assertIsNotEnabled()
        compose.onNodeWithTag("pdf-source-review").performScrollToNode(hasText("Split into left then right columns"))
        compose.onNodeWithText("Split into left then right columns").performClick()
        compose.runOnIdle {
            assertEquals(listOf(PdfSourceRegion(2, right = 50), PdfSourceRegion(2, left = 50)), state.regions)
        }
        compose.onNodeWithTag("pdf-source-review").performScrollToNode(hasTestTag("pdf-source-preview"))
        compose.onNodeWithTag("pdf-source-preview").performClick()
        compose.onNodeWithTag("pdf-source-create").assertIsNotEnabled()
        compose.onNodeWithTag("pdf-source-review").performScrollToNode(hasText("I checked this text", substring = true))
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithTag("pdf-source-create").assertIsEnabled()
        compose.runOnIdle { state = state.copy(regions = state.regions.reversed(), preview = null) }
        compose.onNodeWithTag("pdf-source-create").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, created) }
        if (InstrumentationRegistry.getArguments().getString("capturePdfSource") == "true") {
            assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            compose.onNodeWithTag("pdf-source-review").performScrollToIndex(0)
            compose.waitForIdle()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            java.io.File(context.getExternalFilesDir(null), "synthetic-pdf-source-$dark-$fontScale.png").outputStream().use {
                compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
