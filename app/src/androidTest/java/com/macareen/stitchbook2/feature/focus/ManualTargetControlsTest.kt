package com.macareen.stitchbook2.feature.focus

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ManualTargetControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lightTargetFlowRequiresRecordedObservationAndSeparateConfirmation() = check(false, 1f)
    @Test fun darkTargetFlowRequiresRecordedObservationAndSeparateConfirmation() = check(true, 1f)
    @Test fun targetControlsRemainReachableAtLargeText() = check(true, 1.8f)

    private fun check(dark: Boolean, scale: Float) {
        val target = ManualTarget(TargetUnit.CENTIMETRES, 12.5)
        val state = mutableStateOf(GuideFocusUiState.InProgress("Sample", "project", ExecutionId("work"), 1,
            "Work one complete cycle. (p.2)", emptyList(), emptyList(), target = target, occurrenceKey = "cycle"))
        var confirms = 0
        var cycles = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) { Surface(Modifier.systemBarsPadding()) {
                    GuideFocusScreen(state.value, {}, { cycles++ }, {}, {}, {}, {}, {},
                        onObserveTarget = { value -> state.value = state.value.copy(version = state.value.version + 1,
                            targetWork = TargetWork(observations = listOf(TargetObservation(1, value)))) },
                        onConfirmTarget = { confirms++ })
                } }
            }
        }
        compose.onNodeWithText("Complete").assertDoesNotExist()
        compose.onNodeWithText("Cycle done - repeat").assertIsDisplayed()
        compose.onNodeWithText("Target reached").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Observed now (cm)").performScrollTo().performTextInput("12.5")
        compose.onNodeWithText("Record observation").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, confirms); assertEquals(0, cycles) }
        compose.onNodeWithText("Target reached").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(0, confirms) }
        compose.onAllNodesWithText("Target reached").filter(hasClickAction()).onLast().performClick()
        compose.runOnIdle { assertEquals(1, confirms) }
        compose.onNodeWithText("Cycle done - repeat").performClick()
        compose.runOnIdle { assertEquals(1, cycles) }
        compose.onNodeWithText("Previous").assertIsDisplayed()
    }
}
