package com.macareen.stitchbook2.feature.library

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PatternEditDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val item = LibraryItem("pattern", "Sample pattern", Craft.TUNISIAN_CROCHET, "A designer", null, emptyList(),
        "Personal notes", true, 0, 0, pdfUri = "content://original", pdfFileName = "original.pdf")

    @Test fun failedSaveKeepsTypedTitleForRetry() {
        val state = mutableStateOf(PatternEditReview(item))
        var savedTitle = ""
        compose.setContent { StitchbookTheme {
            PatternEditDialog(state.value, {}, {}, { title, _, _, _, _, _, _ ->
                savedTitle = title
                state.value = state.value.copy(problem = PatternEditProblem.FAILED)
            })
        } }
        compose.onNodeWithText("Pattern Title").performTextReplacement("My edited title")
        compose.onNodeWithText("Save Pattern").performClick()
        compose.onNodeWithText("The details could not be saved. Your edits are kept; try again.").assertIsDisplayed()
        compose.onNodeWithText("Pattern Title").assertTextContains("My edited title")
        compose.onNodeWithText("Save Pattern").performClick()
        compose.runOnIdle { assertEquals("My edited title", savedTitle) }
    }

    @Test fun changedPatternRequiresExplicitReloadAndBusyActionsAreDisabled() {
        val state = mutableStateOf(PatternEditReview(item, problem = PatternEditProblem.CHANGED))
        var reloads = 0
        compose.setContent { StitchbookTheme {
            PatternEditDialog(state.value, {}, { reloads++ }, { _, _, _, _, _, _, _ -> })
        } }
        compose.onNodeWithText("Save Pattern").assertIsNotEnabled()
        compose.onNodeWithText("Reload saved details").performClick()
        compose.runOnIdle { assertEquals(1, reloads); state.value = state.value.copy(isSaving = true) }
        compose.onNodeWithText("Saving\u2026").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Pattern Title").assertIsNotEnabled()
    }

    @Test fun longFormAndValidationRemainReachableAtLargeText() {
        val original = compose.activity.resources.configuration.fontScale
        assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        setScaleAndWait(1.8f)
        try {
            compose.setContent { StitchbookTheme(darkTheme = true) {
                PatternEditDialog(PatternEditReview(item), {}, {}, { _, _, _, _, _, _, _ -> })
            } }
            compose.onNodeWithText("Ravelry Pattern ID").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Yardage Required").performScrollTo().performTextReplacement("NaN")
            compose.onNodeWithText("Save Pattern").assertIsNotEnabled()
            compose.onNodeWithText("Enter a positive number, or leave this empty.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Yardage Required").performScrollTo().performTextReplacement("123.5")
            compose.onNodeWithText("Save Pattern").assertIsEnabled()
            // Close the keyboard so the optional capture shows the dialog's available viewport.
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Pattern Title").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Cancel").assertIsDisplayed()
            if (InstrumentationRegistry.getArguments().getString("capturePatternEdit") == "true") {
                compose.onNode(isDialog()).captureToImage()
                val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                assertTrue(root?.findAccessibilityNodeInfosByText("System UI isn't responding").isNullOrEmpty())
                ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("screencap -p /sdcard/Download/stitchbook-pattern-edit-large.png")).use { it.readBytes() }
            }
        } finally { setScaleAndWait(original) }
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
