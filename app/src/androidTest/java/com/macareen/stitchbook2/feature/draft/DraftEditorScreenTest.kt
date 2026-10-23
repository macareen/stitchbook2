package com.macareen.stitchbook2.feature.draft

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
    @Test fun reviewWarningsExpandWithoutWorkActionsAtLargeText() {
        val warning = "Check this condition against the PDF; no target was inferred: " +
            "Keep the complete original unsupported wording visible for review. ".repeat(4) + "(p.2 l.3)"
        setContent(DraftEditorUiState.Content(guideName = "A long sample guide name for a selected size", rows = listOf(
            DraftOutlineRow(com.macareen.stitchbook2.domain.guide.DraftNode(
                com.macareen.stitchbook2.domain.execution.NodeId("warning"),
                com.macareen.stitchbook2.domain.guide.DraftNodeType.INSTRUCTION,
                instructionText = "Review needed: $warning"), 0, false, false)
        )), largeText = true)
        composeTestRule.onNodeWithText("Needs review").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Repeat until a target").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Repeat this instruction").assertCountEquals(0)
        composeTestRule.onNodeWithText("Show full wording").performClick()
        composeTestRule.onNodeWithText(warning).assertExists()
        composeTestRule.onNodeWithText("Show less").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Show full wording").performScrollTo().assertIsDisplayed()
        composeTestRule.runOnIdle {
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "pdf-body-editor-large.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun nestedDraftKeepsActionsOnSeparateLinesAndShowsContainerTypes() {
        val id = com.macareen.stitchbook2.domain.execution.NodeId("step")
        setContent(DraftEditorUiState.Content(guideName = "Sample cardigan", rows = listOf(
            DraftOutlineRow(com.macareen.stitchbook2.domain.guide.DraftNode(
                id = com.macareen.stitchbook2.domain.execution.NodeId("repeat"),
                type = com.macareen.stitchbook2.domain.guide.DraftNodeType.REPEAT,
                repeatCount = 9, children = listOf(id)), 0, false, false),
            DraftOutlineRow(com.macareen.stitchbook2.domain.guide.DraftNode(
                id = id, type = com.macareen.stitchbook2.domain.guide.DraftNodeType.INSTRUCTION,
                instructionText = "Knit to marker 1, m1r, slip marker 1, knit to end."), 1, false, false)
        )))
        composeTestRule.onNodeWithText("Repeat").assertIsDisplayed()
        val first = composeTestRule.onNodeWithText("Knit to marker 1").fetchSemanticsNode().boundsInRoot
        val second = composeTestRule.onNodeWithText("m1r").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(second.top > first.bottom)
        composeTestRule.onNodeWithText("Publish").assertIsDisplayed()
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureActionLines") == "true") {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/stitchbook-draft-hierarchy.png")).use { it.readBytes() }
        }
    }


    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(state: DraftEditorUiState, largeText: Boolean = false) {
        composeTestRule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, if (largeText) 1.8f else density.fontScale)) {
            StitchbookTheme {
                Surface(Modifier.systemBarsPadding()) {
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
