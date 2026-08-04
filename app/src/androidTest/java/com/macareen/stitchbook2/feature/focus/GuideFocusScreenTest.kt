package com.macareen.stitchbook2.feature.focus

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.ExecutionAddress
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Rule
import org.junit.Test

/**
 * Presentation-only tests: these exercise [GuideFocusScreen] with hand-built
 * [GuideFocusUiState] values, never a real [GuideFocusViewModel] or
 * persistence. Execution semantics are covered by the existing engine and
 * ViewModel test suites; this file only verifies what the visual refinement
 * changed.
 */
class GuideFocusScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(state: GuideFocusUiState) {
        composeTestRule.setContent {
            StitchbookTheme {
                GuideFocusScreen(
                    uiState = state,
                    onStart = {},
                    onComplete = {},
                    onPrevious = {},
                    onJumpToFirstIncomplete = {},
                    onStartNext = {}
                )
            }
        }
    }

    @Test
    fun standardActiveExecution_showsInstructionAndBothActions() {
        setContent(
            GuideFocusUiState.InProgress(
                guideName = "Everyday cardigan",
                executionId = ExecutionId("execution-1"),
                version = 0,
                instructionText = "Knit all stitches",
                breadcrumbs = listOf("Body"),
                positions = emptyList()
            )
        )

        composeTestRule.onNodeWithText("Everyday cardigan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Body").assertIsDisplayed()
        composeTestRule.onNodeWithText("Knit all stitches").assertIsDisplayed()
        composeTestRule.onNodeWithText("Previous").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithText("Complete").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun firstOccurrence_previousRemainsVisibleAndEnabled() {
        // The engine treats Previous as a no-op at the first occurrence
        // rather than hiding it (see docs/EXECUTION_ENGINE_SPEC.md section
        // 9.2); the UI must not second-guess that by disabling or hiding it.
        setContent(
            GuideFocusUiState.InProgress(
                guideName = "Everyday cardigan",
                executionId = ExecutionId("execution-1"),
                version = 0,
                instructionText = "Cast on 40 stitches",
                breadcrumbs = listOf("Cast on"),
                positions = emptyList()
            )
        )

        composeTestRule.onNodeWithText("Previous").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun rangeContext_rendersUnitValueAndBounds() {
        setContent(
            GuideFocusUiState.InProgress(
                guideName = "Everyday cardigan",
                executionId = ExecutionId("execution-1"),
                version = 0,
                instructionText = "Knit all stitches",
                breadcrumbs = listOf("Body"),
                positions = listOf(
                    StructuralPosition.RangePosition(
                        unitLabel = "round",
                        currentValue = 4,
                        startInclusive = 1,
                        endInclusive = 10
                    )
                )
            )
        )

        composeTestRule.onNodeWithText("Round 4 of 1–10").assertIsDisplayed()
    }

    @Test
    fun repeatContext_rendersIterationAndCount() {
        setContent(
            GuideFocusUiState.InProgress(
                guideName = "Everyday cardigan",
                executionId = ExecutionId("execution-1"),
                version = 0,
                instructionText = "Lace row A",
                breadcrumbs = listOf("Lace panel"),
                positions = listOf(
                    StructuralPosition.RepeatPosition(label = null, currentIteration = 3, count = 6)
                )
            )
        )

        composeTestRule.onNodeWithText("Repeat 3 of 6").assertIsDisplayed()
    }

    @Test
    fun nestedRepeatAndRange_bothPositionsRenderTogether() {
        setContent(
            GuideFocusUiState.InProgress(
                guideName = "Everyday cardigan",
                executionId = ExecutionId("execution-1"),
                version = 0,
                instructionText = "Work texture round",
                breadcrumbs = listOf("Textured band"),
                positions = listOf(
                    StructuralPosition.RepeatPosition(label = "Band", currentIteration = 2, count = 3),
                    StructuralPosition.RangePosition(
                        unitLabel = "round",
                        currentValue = 3,
                        startInclusive = 1,
                        endInclusive = 4
                    )
                )
            )
        )

        composeTestRule.onNodeWithText("Band: repeat 2 of 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Round 3 of 1–4").assertIsDisplayed()
    }

    @Test
    fun longInstruction_displaysFullyAndActionsRemainReachable() {
        val longInstruction = "Yarn over, knit two together, knit to the last three " +
            "stitches of the round, slip one, knit one, pass the slipped stitch over, " +
            "then repeat the sequence from the beginning of the round for every " +
            "remaining repeat before continuing to the border."

        setContent(
            GuideFocusUiState.InProgress(
                guideName = "Everyday cardigan",
                executionId = ExecutionId("execution-1"),
                version = 0,
                instructionText = longInstruction,
                breadcrumbs = listOf("Lace panel"),
                positions = emptyList(),
                jumpToFirstIncompleteTarget = ExecutionAddress(
                    definitionRevisionId = DefinitionRevisionId("revision-1"),
                    instructionNodeId = NodeId("instruction-1")
                )
            )
        )

        composeTestRule.onNodeWithText(longInstruction).assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithText("Previous").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithText("Resume at earliest incomplete step").assertIsDisplayed()
    }

    @Test
    fun completedExecution_showsCompletionMessageAndStartNext() {
        setContent(GuideFocusUiState.Completed(guideName = "Everyday cardigan"))

        composeTestRule.onNodeWithText("Guide complete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start new").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun staleExecutionFeedback_isSurfacedToTheUser() {
        setContent(
            GuideFocusUiState.InProgress(
                guideName = "Everyday cardigan",
                executionId = ExecutionId("execution-1"),
                version = 1,
                instructionText = "Knit all stitches",
                breadcrumbs = emptyList(),
                positions = emptyList(),
                feedback = FocusFeedback.STALE_EXECUTION_STATE
            )
        )

        composeTestRule.onNodeWithText("This changed elsewhere. Showing the current step.")
            .assertIsDisplayed()
    }

    @Test
    fun busyStateDisablesCompletePreviousAndJumpControls() {
        // isBusy means a transition write is already in flight; the screen
        // must not let a second tap queue another one on top of it.
        setContent(
            GuideFocusUiState.InProgress(
                guideName = "Everyday cardigan",
                executionId = ExecutionId("execution-1"),
                version = 0,
                instructionText = "Knit all stitches",
                breadcrumbs = emptyList(),
                positions = emptyList(),
                jumpToFirstIncompleteTarget = ExecutionAddress(
                    definitionRevisionId = DefinitionRevisionId("revision-1"),
                    instructionNodeId = NodeId("instruction-1")
                ),
                isBusy = true
            )
        )

        composeTestRule.onNodeWithText("Complete").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Previous").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Resume at earliest incomplete step")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun primaryAndSecondaryActions_meetMinimumTouchTarget() {
        setContent(
            GuideFocusUiState.InProgress(
                guideName = "Everyday cardigan",
                executionId = ExecutionId("execution-1"),
                version = 0,
                instructionText = "Knit all stitches",
                breadcrumbs = emptyList(),
                positions = emptyList()
            )
        )

        composeTestRule.onNodeWithText("Complete").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithText("Previous").assertHeightIsAtLeast(48.dp)
    }
}
