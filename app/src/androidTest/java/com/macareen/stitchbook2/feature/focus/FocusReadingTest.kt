package com.macareen.stitchbook2.feature.focus

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.instruction
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FocusReadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun groupedSentenceUsesOneReadingStyle() {
        compose.setContent { StitchbookTheme { Surface {
            InstructionBlocks("Work (k1, p1) to end.", MaterialTheme.typography.instruction)
        } } }
        val layouts = listOf("Work (", "k1", "p1", ") to end.").map { text ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            results.single().layoutInput.style
        }
        assertTrue("Sentence, actions and suffix must share a reading style", layouts.all { it == layouts.first() })
    }

    @Test fun longInstructionsAreDiscoverableInLightMode() = checkReading(false, 1f)
    @Test fun longInstructionsAreDiscoverableInDarkMode() = checkReading(true, 1f)
    @Test fun longInstructionsAreDiscoverableAtLargeText() = checkReading(true, 1.8f)

    private fun checkReading(dark: Boolean, scale: Float) {
        val text = (1..45).joinToString("\n") { "Instruction $it: Work the next stitch." }
        val state = mutableStateOf(GuideFocusUiState.InProgress(
            "Sample project", "project", ExecutionId("reading"), 1, text, emptyList(), emptyList(),
            pattern = PatternPage("pattern", 2)
        ))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) { Surface(Modifier.systemBarsPadding()) {
                    GuideFocusScreen(state.value, {}, {}, {}, {}, {}, {}, {})
                } }
            }
        }
        compose.onNodeWithText("Instruction 1: Work the next stitch.").assertIsDisplayed()
        compose.onNodeWithText("More below ↓").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Instruction 1: Work the next stitch.").assertIsNotDisplayed()
        compose.onNodeWithText("Instruction 45: Work the next stitch.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("More below ↓").assertDoesNotExist()
        compose.onNodeWithText("Complete").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText("Instruction 45: Work the next stitch.").assertIsDisplayed()
        // The next repeated occurrence can have exactly the same instruction text.
        compose.runOnIdle { state.value = state.value.copy(version = 2) }
        compose.waitForIdle()
        compose.onNodeWithText("Instruction 1: Work the next stitch.").assertIsDisplayed()
        compose.onNodeWithText("More below ↓").assertIsDisplayed()
        compose.onNodeWithText("Previous").assertIsDisplayed()
        compose.onNodeWithText("Pattern, p.2").assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureFocusReading") == "true") {
            assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            compose.onRoot().captureToImage()
            val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            assertTrue("Clear emulator System UI hangs before capturing",
                root?.findAccessibilityNodeInfosByText("System UI isn't responding").isNullOrEmpty())
            val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/stitchbook-focus-reading-$mode.png")).use { it.readBytes() }
        }
    }
}
