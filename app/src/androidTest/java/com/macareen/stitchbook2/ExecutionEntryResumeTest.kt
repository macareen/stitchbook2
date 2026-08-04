package com.macareen.stitchbook2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Drives the real, production-wired app (`MainActivity` -> `StitchbookNavHost`
 * -> the real `AppContainer` repositories over the on-device Room database) --
 * not a preview, an isolated composable harness, or debug-only tooling.
 *
 * The "Executable guide" fixture used by the Complete/Previous/resume tests
 * below is still seeded directly through the repository (a 2-row Range is
 * more than the in-app Draft editor's own tests need to cover, and keeping
 * one guide pre-published keeps those tests focused on execution, not
 * authoring). The authoring path itself -- Add Guide, the Draft editor, and
 * Publish -- is exercised directly through real navigation by the tests
 * below that create their own Guide from scratch.
 */
@RunWith(AndroidJUnit4::class)
class ExecutionEntryResumeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val projectId = "entry-resume-project-${UUID.randomUUID()}"
    private val projectName = "Entry Resume Project ${UUID.randomUUID()}"

    @Before
    fun seedProjectAndGuides(): Unit = runBlocking {
        val container = composeTestRule.activity.applicationContext.let {
            (it as StitchbookApplication).container
        }

        container.projectRepository.saveProject(
            Project(
                id = projectId,
                name = projectName,
                craft = Craft.KNITTING,
                projectType = ProjectType.OTHER,
                status = ProjectStatus.ACTIVE,
                notes = null,
                createdAt = 0,
                updatedAt = 0
            )
        )

        val executableGuide = container.guideRepository.createGuide(projectId, "Executable guide")
        val draft = checkNotNull(container.guideRepository.loadDraft(executableGuide.id))
        container.guideRepository.saveDraft(
            draft.copy(
                // A 2-row Range (rather than a single bare Instruction) so
                // Complete/Previous have somewhere real to move to and from,
                // and their persisted position is independently verifiable
                // via the "Row x of 1-2" structural context line.
                rootNodeIds = listOf(NodeId("range")),
                nodes = listOf(
                    DraftNode(
                        id = NodeId("range"),
                        type = DraftNodeType.RANGE,
                        rangeUnitLabel = "row",
                        rangeStartInclusive = 1,
                        rangeEndInclusive = 2,
                        children = listOf(NodeId("instruction"))
                    ),
                    DraftNode(
                        id = NodeId("instruction"),
                        type = DraftNodeType.INSTRUCTION,
                        instructionText = "Cast on 40 stitches"
                    )
                )
            )
        )
        container.guideRepository.publishDraft(executableGuide.id)

        container.guideRepository.createGuide(projectId, "Draft only guide")
    }

    @After
    fun removeSeededProject(): Unit = runBlocking {
        val container = composeTestRule.activity.applicationContext.let {
            (it as StitchbookApplication).container
        }
        container.projectRepository.deleteProject(
            Project(
                id = projectId,
                name = projectName,
                craft = Craft.KNITTING,
                projectType = ProjectType.OTHER,
                status = ProjectStatus.ACTIVE,
                notes = null,
                createdAt = 0,
                updatedAt = 0
            )
        )
    }

    @Test
    fun startingAGuideNavigatesToFocusModeAndResumesAfterRecreation() {
        openProject()

        composeTestRule.onNodeWithText("Executable guide").assertIsDisplayed()
        composeTestRule.onNode(hasText("Start") and hasClickAction()).assertIsDisplayed()

        composeTestRule.onNodeWithText("Executable guide").performClick()
        composeTestRule.onNodeWithText("Ready to start").assertIsDisplayed()

        composeTestRule.onNode(hasText("Start") and hasClickAction()).performClick()
        composeTestRule.onNodeWithText("Cast on 40 stitches").assertIsDisplayed()

        // Recreate the Activity (and every ViewModel/composable with it) to
        // simulate returning after process death or a configuration change.
        // Focus Mode must re-derive its state from Room, not from any
        // in-memory or navigation-carried copy.
        composeTestRule.activityRule.scenario.recreate()

        composeTestRule.onNodeWithText("Cast on 40 stitches").assertIsDisplayed()

        // Back out to the Guide's own entry point and confirm it now offers
        // Continue -- never Start again -- for the same still-ACTIVE Execution.
        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.onNodeWithText("Executable guide").assertIsDisplayed()
        composeTestRule.onNode(hasText("Continue") and hasClickAction()).assertIsDisplayed()

        composeTestRule.onNodeWithText("Executable guide").performClick()
        composeTestRule.onNodeWithText("Cast on 40 stitches").assertIsDisplayed()
    }

    @Test
    fun completingAndRewindingPersistThroughRealNavigationAndSurviveRecreation() {
        openProject()
        composeTestRule.onNodeWithText("Executable guide").performClick()
        composeTestRule.onNode(hasText("Start") and hasClickAction()).performClick()

        composeTestRule.onNodeWithText("Row 1 of 1–2").assertIsDisplayed()

        composeTestRule.onNodeWithText("Complete").performClick()
        composeTestRule.onNodeWithText("Row 2 of 1–2").assertIsDisplayed()

        composeTestRule.onNodeWithText("Previous").performClick()
        composeTestRule.onNodeWithText("Row 1 of 1–2").assertIsDisplayed()

        // Recreate the Activity to confirm the post-Complete-then-Previous
        // position (not just the freshly-Started one) is what Room actually
        // persisted, not something the ViewModel merely held in memory.
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.onNodeWithText("Row 1 of 1–2").assertIsDisplayed()
    }

    @Test
    fun addingAGuideAndAuthoringAStepPersistsThroughRealNavigation() {
        // Project -> Add Guide -> Draft editor -> add one Instruction ->
        // Done -> back on the Guide list (still Draft-only, so still
        // "Edit draft") -> reopen -> the authored step is what Room
        // actually persisted, not something the editor merely held.
        openProject()

        composeTestRule.onNode(hasText("Add Guide") and hasClickAction()).performClick()
        composeTestRule.onNodeWithText("Guide name").performTextInput("Sleeve")
        composeTestRule.onNode(hasText("Create") and hasClickAction()).performClick()

        composeTestRule.onNodeWithText("Sleeve").assertIsDisplayed()

        composeTestRule.onNode(hasText("Add step") and hasClickAction()).performClick()
        composeTestRule.onNode(hasText("Instruction") and hasClickAction()).performClick()
        composeTestRule.onNodeWithText("What to knit").performTextInput("Cast on 10 stitches")
        composeTestRule.onNode(hasText("Add") and hasClickAction()).performClick()

        composeTestRule.onNodeWithText("Cast on 10 stitches").assertIsDisplayed()

        composeTestRule.onNode(hasText("Done") and hasClickAction()).performClick()

        composeTestRule.onNodeWithText("Sleeve").assertIsDisplayed()
        composeTestRule.onNode(hasText("Edit draft") and hasClickAction()).assertIsDisplayed()

        composeTestRule.onNodeWithText("Sleeve").performClick()
        composeTestRule.onNodeWithText("Cast on 10 stitches").assertIsDisplayed()
    }

    @Test
    fun publishingAGuideThroughRealNavigationReachesFocusMode() {
        openProject()

        composeTestRule.onNode(hasText("Add Guide") and hasClickAction()).performClick()
        composeTestRule.onNodeWithText("Guide name").performTextInput("Hat")
        composeTestRule.onNode(hasText("Create") and hasClickAction()).performClick()

        composeTestRule.onNode(hasText("Add step") and hasClickAction()).performClick()
        composeTestRule.onNode(hasText("Instruction") and hasClickAction()).performClick()
        composeTestRule.onNodeWithText("What to knit").performTextInput("Cast on 60 stitches")
        composeTestRule.onNode(hasText("Add") and hasClickAction()).performClick()

        composeTestRule.onNode(hasText("Publish") and hasClickAction()).performClick()

        composeTestRule.onNode(hasText("Start Knitting") and hasClickAction()).performClick()

        // Landed on Focus Mode's own Ready-to-start screen -- the Draft
        // editor only navigates here, it never creates the Execution
        // itself; that remains Focus Mode's own Start action.
        composeTestRule.onNodeWithText("Ready to start").assertIsDisplayed()
        composeTestRule.onNode(hasText("Start") and hasClickAction()).performClick()

        composeTestRule.onNodeWithText("Cast on 60 stitches").assertIsDisplayed()
    }

    @Test
    fun draftOnlyGuideOffersEditDraftAndOpensTheEditor() {
        // A Draft-only Guide (no published Revision) can never offer
        // Continue/Start -- Drafts are never executable -- so its entry
        // point is the Draft editor instead of Focus Mode, which would have
        // nothing to execute yet.
        openProject()

        composeTestRule.onNodeWithText("Draft only guide").assertIsDisplayed()
        composeTestRule.onNode(hasText("Edit draft") and hasClickAction()).assertIsDisplayed()

        composeTestRule.onNodeWithText("Draft only guide").performClick()

        composeTestRule.onNodeWithText("Draft only guide").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Let's write your pattern. Try a Section to name a part (like Body), " +
                "or jump straight to an Instruction if it's simple."
        ).assertIsDisplayed()
    }

    private fun openProject() {
        composeTestRule.navigationItem("Projects").performClick()
        composeTestRule.onNodeWithText(projectName).performClick()
    }
}

private fun AndroidComposeTestRule<*, MainActivity>.navigationItem(label: String) =
    onNode(hasText(label) and hasClickAction())
