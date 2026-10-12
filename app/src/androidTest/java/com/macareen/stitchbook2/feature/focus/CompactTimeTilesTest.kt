package com.macareen.stitchbook2.feature.focus

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Rule
import org.junit.Test

class CompactTimeTilesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lessThanAMinuteUsesAShortLabel() {
        show(30_000)
        compose.onAllNodesWithText("<1 min").assertCountEquals(2)
        screenshot("short")
    }
    @Test fun hoursUseZeroPaddedMinutesAtLargeFontScale() {
        show(65 * 60_000L, scale = 1.8f, dark = true)
        compose.onAllNodesWithText("1h 05m").assertCountEquals(2)
        compose.onNodeWithText("Previous").assertIsDisplayed()
        compose.onNodeWithText("Complete").assertIsDisplayed()
        screenshot("large-dark")
    }
    @Test fun estimatedStepTimeRetainsItsLabelAtLargeFontScale() {
        show(65 * 60_000L, pause = 5 * 60_000L, stepStart = 60 * 60_000L, scale = 1.8f)
        compose.onNodeWithText("about 4 min").assertIsDisplayed()
        compose.onNodeWithText("1h 00m").assertIsDisplayed()
        screenshot("estimated-large")
    }
    private fun show(duration: Long, pause: Long = 0, stepStart: Long = 0, scale: Float = 1f, dark: Boolean = false) {
        val session = CraftingSession("s", "project", 0, duration, null, pause, "UTC", null, null, null, 0, 0)
        val state = GuideFocusUiState.InProgress(
            guideName = "Sample cardigan", projectId = "project", executionId = ExecutionId("e"), version = 0,
            instructionText = "Knit all stitches.", breadcrumbs = listOf("Body"), positions = emptyList(),
            timeSessions = listOf(session), stepStartedAt = stepStart
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        GuideFocusScreen(state, {}, {}, {}, {}, {}, {}, {},
                            modifier = Modifier.systemBarsPadding().widthIn(max = 320.dp))
                    }
                }
            }
        }
    }
    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureTimeTiles") != "true") return
        compose.waitForIdle()
        val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/stitchbook-time-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(pipe).use { it.readBytes() }
    }
}
