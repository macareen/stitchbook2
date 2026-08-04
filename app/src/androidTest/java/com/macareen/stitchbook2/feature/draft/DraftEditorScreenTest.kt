package com.macareen.stitchbook2.feature.draft

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Rule
import org.junit.Test

/**
 * Presentation-only tests: hand-built [DraftEditorUiState] values, never a
 * real [DraftEditorViewModel] or persistence -- mirrors the precedent set
 * by GuideFocusScreenTest for exactly this purpose. Covers the revised
 * plain-language copy and the Publish/Start-Continue action hierarchy.
 */
class DraftEditorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(state: DraftEditorUiState) {
        composeTestRule.setContent {
            StitchbookTheme {
                DraftEditorScreen(
                    uiState = state,
                    onAddNode = { _, _, _, _, _, _, _, _, _ -> },
                    onUpdateNode = { _, _, _, _, _, _, _, _ -> },
                    onDeleteNode = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onPublish = {},
                    onDismissError = {},
                    onDone = {},
                    onStartOrContinue = {}
                )
            }
        }
    }

    @Test
    fun emptyDraft_showsPlainLanguageGuidance() {
        setContent(
            DraftEditorUiState.Content(
                guideName = "Everyday cardigan",
                rows = emptyList()
            )
        )

        composeTestRule.onNodeWithText(
            "Let's write your pattern. Try a Section to name a part (like Body), " +
                "or jump straight to an Instruction if it's simple."
        ).assertIsDisplayed()
    }

    @Test
    fun chooseStepType_showsAllFourTypesWithPlainLanguageExplanations() {
        setContent(
            DraftEditorUiState.Content(
                guideName = "Everyday cardigan",
                rows = emptyList()
            )
        )

        composeTestRule.onNodeWithText("Add step").performClick()

        composeTestRule.onNodeWithText("Section").assertIsDisplayed()
        composeTestRule.onNodeWithText("Group steps into a labeled part, like Body or Sleeve")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Row range").assertIsDisplayed()
        composeTestRule.onNodeWithText("A set of rows worked the same way, like Rows 1–4")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Repeat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Do the steps inside a set number of times")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Instruction").assertIsDisplayed()
        composeTestRule.onNodeWithText("One line of what to actually knit").assertIsDisplayed()
    }

    @Test
    fun beforePublication_startKnittingIsNotOffered() {
        setContent(
            DraftEditorUiState.Content(
                guideName = "Everyday cardigan",
                rows = emptyList(),
                isPublished = false
            )
        )

        composeTestRule.onNodeWithText("Publish").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Start Knitting").assertCountEquals(0)
    }

    @Test
    fun afterPublicationWithNoActiveExecution_offersStartKnitting() {
        setContent(
            DraftEditorUiState.Content(
                guideName = "Everyday cardigan",
                rows = emptyList(),
                isPublished = true,
                hasActiveExecution = false
            )
        )

        composeTestRule.onNodeWithText("Start Knitting").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Continue Knitting").assertCountEquals(0)
    }

    @Test
    fun afterPublicationWithActiveExecution_offersContinueKnitting() {
        setContent(
            DraftEditorUiState.Content(
                guideName = "Everyday cardigan",
                rows = emptyList(),
                isPublished = true,
                hasActiveExecution = true
            )
        )

        composeTestRule.onNodeWithText("Continue Knitting").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Start Knitting").assertCountEquals(0)
    }
}
