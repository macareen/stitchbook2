package com.macareen.stitchbook2.feature.sessions

import android.os.ParcelFileDescriptor
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.SessionTimer
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SessionReviewDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun reviewIsUsableInLightMode() = check(false, 1f)
    @Test fun reviewIsUsableInDarkMode() = check(true, 1f)
    @Test fun reviewScrollsWithLargeText() = check(true, 1.8f)
    private fun check(dark: Boolean, scale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val originalScale = android.provider.Settings.System.getFloat(instrumentation.targetContext.contentResolver,
            android.provider.Settings.System.FONT_SCALE, 1f)
        val changeScale = scale > 1f && kotlin.math.abs(originalScale - scale) > 0.05f
        if (changeScale) {
            assertTrue("System-font changes are emulator-only", android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            setSystemScale(scale)
            compose.activityRule.scenario.recreate()
        }
        try {
        var corrected: String? = null
        var kept = 0
        var deferred = 0
        val state = SessionReviewUiState(PendingSessionReview(SessionTimer.start("s", "p", 0, "UTC"), "My cardigan", 900))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) {
                    SessionReviewDialog(state, { kept++ }, { corrected = it }, { deferred++ })
                }
            }
        }
        compose.onNodeWithText("Check your timer").assertIsDisplayed()
        compose.onNodeWithText("Correct and pause").assertIsNotEnabled()
        compose.onNodeWithText("Crafting minutes").performScrollTo().performTextReplacement("10")
        compose.onNodeWithText("Crafting minutes").performImeAction()
        compose.onNodeWithText("Correct and pause").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("10", corrected) }
        compose.onNodeWithText("Count all time").assertIsDisplayed().performClick()
        compose.onNodeWithText("Later").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, kept); assertEquals(1, deferred) }
        val correctBounds = compose.onNodeWithText("Correct and pause").fetchSemanticsNode().boundsInRoot
        val keepBounds = compose.onNodeWithText("Count all time").fetchSemanticsNode().boundsInRoot
        val laterBounds = compose.onNodeWithText("Later").fetchSemanticsNode().boundsInRoot
        assertTrue("Timer actions must not overlap", correctBounds.bottom <= keepBounds.top && keepBounds.bottom <= laterBounds.top)
        compose.onNodeWithText("Check your timer").captureToImage()
        if (InstrumentationRegistry.getArguments().getString("captureTimerReview") == "true") {
            val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/stitchbook-timer-review-$mode.png")).use { it.readBytes() }
        }
        } finally {
            if (changeScale) {
                setSystemScale(originalScale)
                compose.activityRule.scenario.recreate()
            }
        }
    }
    private fun setSystemScale(scale: Float) {
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "settings put system font_scale $scale")).use { it.readBytes() }
    }
}
