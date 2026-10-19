package com.macareen.stitchbook2.feature.library

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PatternPdfChangeDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val item = LibraryItem("pattern", "Sample pattern", Craft.CROCHET, null, null, emptyList(), null, false, 0, 0)
    private val review = PatternPdfReview(item.copy(pdfUri = "content://old", pdfFileName = "Original sample pattern.pdf"),
        PickedDocument("content://new", "Replacement sample pattern with a longer filename.pdf"))

    @Test fun replaceReviewIsReadableInLightMode() = check(false, 1f)
    @Test fun replaceReviewIsReadableInDarkMode() = check(true, 1f)
    @Test fun replaceReviewIsReadableAtLargeText() = check(true, 1.8f)
    @Test fun unlinkShowsPreservationAndBusySaveDisablesActions() {
        compose.setContent { StitchbookTheme {
            PatternPdfChangeDialog(review.copy(document = null, isSaving = true), {}, {})
        } }
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("This removes the PDF link from this pattern. You can attach a document again later.")
            .performScrollTo().assertIsDisplayed()
    }
    @Test fun missingPatternCannotBeConfirmed() {
        compose.setContent { StitchbookTheme {
            PatternPdfChangeDialog(review.copy(problem = PdfChangeProblem.MISSING), {}, {})
        } }
        compose.onAllNodesWithText("Replace PDF link").filter(hasClickAction()).onFirst().assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsEnabled()
    }

    @Test fun patternPageOffersReplaceAndUnlinkActions() {
        var unlinkRequests = 0
        var editRequests = 0
        compose.setContent { StitchbookTheme {
            PatternGuidesScreen(PatternGuidesUiState.Content(review.expected, emptyList(), false, false),
                {}, {}, { _, _ -> }, onUnlinkPdf = { unlinkRequests++ }, onEditPattern = { editRequests++ })
        } }
        compose.onNodeWithText("PDF actions").performScrollTo().performClick()
        compose.onNodeWithText("Replace PDF link").assertIsDisplayed()
        compose.onNodeWithText("Unlink PDF").performClick()
        compose.onNodeWithText("Edit pattern details").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, unlinkRequests); assertEquals(1, editRequests) }
    }

    private fun check(dark: Boolean, scale: Float) {
        val original = compose.activity.resources.configuration.fontScale
        val changeScale = scale > 1f
        if (changeScale) {
            assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            setScaleAndWait(scale)
        }
        try {
            var confirmations = 0
            compose.setContent { StitchbookTheme(darkTheme = dark) {
                PatternPdfChangeDialog(review.copy(problem = PdfChangeProblem.CHANGED), {}, { confirmations++ })
            } }
            compose.onNodeWithText("This pattern changed. Review the current PDF shown below before confirming again.")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Current PDF: Original sample pattern.pdf").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Selected PDF: Replacement sample pattern with a longer filename.pdf")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Original files stay in their current locations. Existing guides, published versions and project progress stay as they are. To use new instructions, create and review a new guide draft.")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Cancel").assertIsDisplayed().assertIsEnabled()
            val confirm = compose.onAllNodesWithText("Replace PDF link").filter(hasClickAction()).onFirst()
            confirm.assertIsDisplayed().assertIsEnabled()
            if (InstrumentationRegistry.getArguments().getString("capturePatternPdf") == "true") {
                assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
                compose.onNode(isDialog()).captureToImage()
                val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                assertTrue(root?.findAccessibilityNodeInfosByText("System UI isn't responding").isNullOrEmpty())
                val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
                ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("screencap -p /sdcard/Download/stitchbook-pattern-pdf-$mode.png")).use { it.readBytes() }
            }
            confirm.performClick()
            compose.runOnIdle { assertEquals(1, confirmations) }
        } finally { if (changeScale) setScaleAndWait(original) }
    }

    private fun setScaleAndWait(scale: Float) {
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put system font_scale $scale")).use { it.readBytes() }
        compose.waitUntil(10_000) {
            runCatching { compose.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                kotlin.math.abs(compose.activity.resources.configuration.fontScale - scale) < .05f }.getOrDefault(false)
        }
    }
}
