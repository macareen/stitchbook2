package com.macareen.stitchbook2.feature.sessions

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ManualTimeDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun lightModeRetainsFailedEntry() = check(false, 1f)
    @Test fun darkModeRetainsFailedEntry() = check(true, 1f)
    @Test fun actualLargeSystemTextKeepsActionsAccessible() = check(true, 1.8f)
    private fun check(dark: Boolean, scale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = android.provider.Settings.System.getFloat(instrumentation.targetContext.contentResolver,
            android.provider.Settings.System.FONT_SCALE, 1f)
        val changeScale = kotlin.math.abs(original - scale) > .05f
        if (changeScale) {
            assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            setScale(scale); compose.activityRule.scenario.recreate()
        }
        try {
            var state by mutableStateOf(ManualEntryState(ManualTimeDefaults("2026-10-12", "19:30", "UTC")))
            var saved: ManualTimeInput? = null
            var attempts = 0
            compose.setContent { StitchbookTheme(darkTheme = dark) {
                ManualTimeDialog(state, { input ->
                    saved = input; attempts++
                    state = state.copy(status = if (attempts == 1) ManualEntryStatus.ERROR else ManualEntryStatus.SAVING,
                        error = if (attempts == 1) ManualTimeError.SAVE_FAILED else null)
                }, { state = state.copy(status = ManualEntryStatus.IDLE, error = null) }, {})
            } }
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithText("Minutes").performScrollTo().performTextReplacement("10")
            compose.onNodeWithText("Notes").performScrollTo().performTextReplacement("Forgot the timer")
            compose.onNodeWithText("Notes").performImeAction()
            compose.onNodeWithText("Save").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals("10", saved!!.minutes); assertEquals("Forgot the timer", saved!!.notes) }
            compose.onNodeWithText("Forgot the timer").assertExists()
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
}
