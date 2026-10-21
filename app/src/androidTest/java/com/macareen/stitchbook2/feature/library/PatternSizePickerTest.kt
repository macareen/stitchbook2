package com.macareen.stitchbook2.feature.library

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PatternSizePickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ninePdfSizesAreVisibleAndTheLastChoiceUsesQuickPdfImportByDefault() = checkPicker(false, false)
    @Test fun sourcePageReviewIsAnExplicitOptionalChoice() = checkPicker(false, true)
    @Test fun ninePdfSizesAreVisibleInDarkMode() = checkPicker(true, false)

    private fun checkPicker(dark: Boolean, reviewSource: Boolean) {
        val sizes = listOf("XS", "S", "M", "L", "XL", "2XL", "3XL", "4XL", "5XL")
        val pattern = LibraryItem("pattern", "Sample cardigan", Craft.KNITTING, null, null,
            emptyList(), null, false, 0, 0, pdfUri = "content://sample.pdf")
        var selected: String? = null
        var selectedReviewSource: Boolean? = null
        compose.setContent {
            StitchbookTheme(darkTheme = dark) {
                PatternGuidesScreen(
                    PatternGuidesUiState.Content(pattern, emptyList(), false, false, pdfSizeChoices = sizes),
                    onOpenPdf = {}, onEditGuide = {},
                    onCreateGuide = { size, _, review -> selected = size; selectedReviewSource = review }
                )
            }
        }
        compose.onNodeWithText("New guide for a size").performScrollTo().performClick()
        sizes.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("5XL").performClick()
        compose.onNodeWithText("Choose pages and areas first").assertIsDisplayed()
        if (reviewSource) compose.onNodeWithTag("pattern-guides-review-source").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Guide name").assertDoesNotExist()
        compose.onNodeWithText("Sample cardigan \u00b7 5XL").assertIsDisplayed()
        // Optional visual QA artifact, written only when requested by the test runner.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureSizePicker") == "true") {
            val mode = if (dark) "dark" else "light"
            val screenshot = instrumentation.uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/stitchbook-size-picker-$mode.png"
            )
            ParcelFileDescriptor.AutoCloseInputStream(screenshot).use { it.readBytes() }
        }
        compose.onNodeWithText("Create and edit").performClick()
        compose.runOnIdle {
            assertEquals("5XL", selected)
            assertEquals(reviewSource, selectedReviewSource)
        }
    }
}
