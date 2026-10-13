package com.macareen.stitchbook2.feature.settings

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.preferences.UserPreferences
import com.macareen.stitchbook2.data.preferences.SharedPreferencesUserPreferencesRepository
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimerReviewPreferencesTest {
    @get:Rule val compose = createComposeRule()
    @Test fun customIntervalWorksInLightMode() = check(false, 1f)
    @Test fun customIntervalWorksInDarkMode() = check(true, 1f)
    @Test fun customIntervalWorksWithLargeText() = check(true, 1.8f)
    @Test fun intervalSurvivesRepositoryRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = SharedPreferencesUserPreferencesRepository(context)
        val original = repo.preferences.value.timerReviewMinutes
        try {
            repo.setTimerReviewMinutes(45)
            assertEquals(45, SharedPreferencesUserPreferencesRepository(context).preferences.value.timerReviewMinutes)
            try { repo.setTimerReviewMinutes(0); fail("Zero must be rejected") } catch (_: IllegalArgumentException) { }
            assertEquals(45, repo.preferences.value.timerReviewMinutes)
        } finally { repo.setTimerReviewMinutes(original) }
    }
    private fun check(dark: Boolean, scale: Float) {
        var saved: Int? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) {
                    Surface(Modifier.systemBarsPadding()) {
                        SettingsScreen(SettingsUiState(), UserPreferences(), {}, {}, {}, {}, {}, {}, {}, {},
                            onTimerReviewMinutesChanged = { saved = it })
                    }
                }
            }
        }
        compose.onNodeWithText("Minutes").performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("Save timer check").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Minutes").performScrollTo().performTextReplacement("45")
        compose.onNodeWithText("Minutes").performImeAction()
        compose.onNodeWithText("Save timer check").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(45, saved) }
        compose.onNodeWithText("Save timer check").captureToImage()
        if (InstrumentationRegistry.getArguments().getString("captureTimerReview") == "true") {
            val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/stitchbook-timer-preferences-$mode.png")).use { it.readBytes() }
        }
    }
}
