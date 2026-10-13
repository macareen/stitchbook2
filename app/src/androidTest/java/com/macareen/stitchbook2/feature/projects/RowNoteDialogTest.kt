package com.macareen.stitchbook2.feature.projects

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.JournalNoteTag
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RowNoteDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun noteSurvivesSaveFailureInLightMode() = check(false, 1f)
    @Test fun noteSurvivesSaveFailureInDarkMode() = check(true, 1f)
    @Test fun noteIsUsableAtActualLargeSystemText() = check(true, 1.8f)
    private fun check(dark: Boolean, scale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = android.provider.Settings.System.getFloat(instrumentation.targetContext.contentResolver,
            android.provider.Settings.System.FONT_SCALE, 1f)
        val changeScale = kotlin.math.abs(original - scale) > .05f
        if (changeScale) {
            assertTrue("System font changes must be emulator-only", android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            setScale(scale); compose.activityRule.scenario.recreate()
        }
        try {
            var state by mutableStateOf(RowNoteSaveState.IDLE)
            var saved: Triple<String, JournalNoteTag, String>? = null
            var attempts = 0
            compose.setContent { StitchbookTheme(darkTheme = dark) {
                RowNoteDialog("Body \u00b7 row 24", state, { body, tag, context ->
                    saved = Triple(body, tag, context); attempts++
                    state = if (attempts == 1) RowNoteSaveState.SAVE_FAILED else RowNoteSaveState.SAVING
                }, {})
            } }
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithText("Adjustment").performScrollTo().performClick()
            compose.onNodeWithText("Guide position").performScrollTo().performTextReplacement("Body row 25")
            compose.onNodeWithText("Note").performScrollTo().performTextReplacement("Keep the body longer.")
            compose.onNodeWithText("Note").performImeAction()
            compose.onNodeWithText("Save").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(Triple("Keep the body longer.", JournalNoteTag.ADJUSTMENT, "Body row 25"), saved) }
            compose.onNodeWithText("Could not save this note. Your text is still here; please try again.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Keep the body longer.").assertExists()
            compose.onNodeWithText("Body row 25").assertExists()
            capture(dark, scale)
            compose.onNodeWithText("Save").performClick()
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithText("Cancel").assertIsNotEnabled()
            compose.runOnIdle { assertEquals(2, attempts) }
        } finally { if (changeScale) { setScale(original); compose.activityRule.scenario.recreate() } }
    }
    private fun setScale(scale: Float) {
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "settings put system font_scale $scale")).use { it.readBytes() }
    }
    private fun capture(dark: Boolean, scale: Float) {
        compose.onNodeWithText("Save").captureToImage()
        if (InstrumentationRegistry.getArguments().getString("captureRowNote") != "true") return
        val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/stitchbook-row-note-$mode.png")).use { it.readBytes() }
    }
}
