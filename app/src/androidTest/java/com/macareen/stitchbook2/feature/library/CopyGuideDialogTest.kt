package com.macareen.stitchbook2.feature.library

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

class CopyGuideDialogTest {
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
            var busy by mutableStateOf(false)
            var failed by mutableStateOf(false)
            var target: String? = null
            var attempts = 0
            compose.setContent { StitchbookTheme(darkTheme = dark) {
                CopyGuideDialog("M", emptyList(), listOf("M"), busy, failed, { size ->
                    target = size; attempts++
                    if (attempts == 1) failed = true else busy = true
                }, {})
            } }
            compose.onNodeWithText("Copy into a draft").assertIsNotEnabled()
            compose.onNodeWithText("Size (as the pattern names it)").performScrollTo().performTextReplacement("m")
            compose.onNode(isToggleable()).performScrollTo().performClick()
            compose.onNodeWithText("Copy into a draft").assertIsNotEnabled()
            compose.onNodeWithText("Size (as the pattern names it)").performScrollTo().performTextReplacement("L")
            compose.onNodeWithText("Size (as the pattern names it)").performImeAction()
            compose.onNodeWithText("Copy into a draft").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals("L", target) }
            compose.onNodeWithText("L").assertExists()
            compose.onNodeWithText("Copy into a draft").performClick()
            compose.onNodeWithText("Copy into a draft").assertIsNotEnabled()
            compose.onNodeWithText("Cancel").assertIsNotEnabled()
            compose.runOnIdle { assertEquals(2, attempts) }
        } finally { if (changeScale) { setScale(original); compose.activityRule.scenario.recreate() } }
    }
    private fun setScale(scale: Float) {
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "settings put system font_scale $scale")).use { it.readBytes() }
    }
}
