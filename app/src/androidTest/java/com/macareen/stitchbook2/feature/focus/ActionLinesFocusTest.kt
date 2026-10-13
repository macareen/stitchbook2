package com.macareen.stitchbook2.feature.focus

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
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ActionLinesFocusTest {
    @get:Rule val compose = createComposeRule()
    @Test fun actionsAreSpacedInLightMode() = check(false, 1f)
    @Test fun actionsAreSpacedInDarkMode() = check(true, 1f)
    @Test fun longRowsScrollAndCompleteStaysReachableAtLargeTextSize() = check(true, 1.8f)
    private fun check(dark: Boolean, scale: Float) {
        var completed = 0
        val state = GuideFocusUiState.InProgress("Sample cardigan", "project", ExecutionId("e"), 0,
            "Row 1 (RS): Knit to marker 1, m1r, slip marker 1, knit 2, m1l, slip marker 2, knit to end. There are 60 stitches.",
            listOf("Yoke"), listOf(StructuralPosition.RepeatPosition(null, 1, 9)))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) {
                    Surface(Modifier.systemBarsPadding()) {
                        GuideFocusScreen(state, {}, { completed++ }, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Repeat 1 of 9").assertIsDisplayed()
        compose.onNodeWithText("Row 1 (RS): Knit to marker 1").assertIsDisplayed()
        compose.onNodeWithText("m1r").assertIsDisplayed()
        val first = compose.onNodeWithText("Row 1 (RS): Knit to marker 1").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("m1r").fetchSemanticsNode().boundsInRoot
        assertTrue("Actions must occupy separate lines with a visible gap", second.top > first.bottom)
        compose.onNodeWithText("Previous").assertIsDisplayed()
        compose.onNodeWithText("Complete").assertIsDisplayed()
        capture(dark, scale, "top")
        compose.onNodeWithText("knit to end. There are 60 stitches.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Complete").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, completed) }
        capture(dark, scale, "end")
    }
    private fun capture(dark: Boolean, scale: Float, location: String) {
        compose.onRoot().captureToImage()
        if (InstrumentationRegistry.getArguments().getString("captureActionLines") != "true") return
        val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/stitchbook-action-lines-$mode-$location.png")).use { it.readBytes() }
    }
}
