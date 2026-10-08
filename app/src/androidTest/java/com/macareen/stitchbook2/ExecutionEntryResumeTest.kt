package com.macareen.stitchbook2

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertEquals
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

        nodeWithText("Executable guide").performScrollTo().assertIsDisplayed()
        node(hasText("Start") and hasClickAction()).performScrollTo().assertIsDisplayed()

        nodeWithText("Executable guide").performScrollTo().performClick()
        nodeWithText("Ready to start").awaitDisplayed()

        node(hasText("Start") and hasClickAction()).performClick()
        nodeWithText("Cast on 40 stitches").awaitDisplayed()

        // Recreate the Activity (and every ViewModel/composable with it) to
        // simulate returning after process death or a configuration change.
        // Focus Mode must re-derive its state from Room, not from any
        // in-memory or navigation-carried copy.
        composeTestRule.activityRule.scenario.recreate()

        nodeWithText("Cast on 40 stitches").awaitDisplayed()

        // Back out to the Guide's own entry point and confirm it now offers
        // Continue -- never Start again -- for the same still-ACTIVE Execution.
        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        nodeWithText("Executable guide").performScrollTo().assertIsDisplayed()
        node(hasText("Continue") and hasClickAction()).performScrollTo().assertIsDisplayed()

        nodeWithText("Executable guide").performScrollTo().performClick()
        nodeWithText("Cast on 40 stitches").awaitDisplayed()
    }

    @Test
    fun completingAndRewindingPersistThroughRealNavigationAndSurviveRecreation() {
        openProject()
        nodeWithText("Executable guide").performScrollTo().performClick()
        node(hasText("Start") and hasClickAction()).performClick()

        nodeWithText("Row 1 of 1–2").awaitDisplayed()

        nodeWithText("Complete").performClick()
        nodeWithText("Row 2 of 1–2").awaitDisplayed()

        nodeWithText("Previous").performClick()
        nodeWithText("Row 1 of 1–2").awaitDisplayed()

        // Recreate the Activity to confirm the post-Complete-then-Previous
        // position (not just the freshly-Started one) is what Room actually
        // persisted, not something the ViewModel merely held in memory.
        composeTestRule.activityRule.scenario.recreate()
        nodeWithText("Row 1 of 1–2").awaitDisplayed()
    }

    @Test
    fun addingAGuideAndAuthoringAStepPersistsThroughRealNavigation() {
        // Project -> Add Guide -> Draft editor -> add one Instruction ->
        // Done -> back on the Guide list (still Draft-only, so still
        // "Edit draft") -> reopen -> the authored step is what Room
        // actually persisted, not something the editor merely held.
        openProject()

        node(hasText("Add Guide") and hasClickAction()).performScrollTo().performClick()
        nodeWithText("Guide name").performTextInput("Sleeve")
        node(hasText("Create") and hasClickAction()).performClick()

        nodeWithText("Sleeve").awaitDisplayed()

        node(hasText("Add step") and hasClickAction()).performClick()
        node(hasText("Instruction") and hasClickAction()).performClick()
        nodeWithText("What to knit").performTextInput("Cast on 10 stitches")
        node(hasText("Add") and hasClickAction()).performClick()

        nodeWithText("Cast on 10 stitches").awaitDisplayed()

        node(hasText("Done") and hasClickAction()).performClick()

        nodeWithText("Sleeve").performScrollTo().assertIsDisplayed()
        // Both draft-only guides (the seeded one and "Sleeve") offer Edit draft.
        assertEquals(2, composeTestRule.onAllNodes(hasText("Edit draft") and hasClickAction()).fetchSemanticsNodes().size)

        nodeWithText("Sleeve").performScrollTo().performClick()
        nodeWithText("Cast on 10 stitches").awaitDisplayed()
    }

    @Test
    fun publishingAGuideThroughRealNavigationReachesFocusMode() {
        openProject()

        node(hasText("Add Guide") and hasClickAction()).performScrollTo().performClick()
        nodeWithText("Guide name").performTextInput("Hat")
        node(hasText("Create") and hasClickAction()).performClick()

        node(hasText("Add step") and hasClickAction()).performClick()
        node(hasText("Instruction") and hasClickAction()).performClick()
        nodeWithText("What to knit").performTextInput("Cast on 60 stitches")
        node(hasText("Add") and hasClickAction()).performClick()

        node(hasText("Publish") and hasClickAction()).performClick()

        node(hasText("Start Knitting") and hasClickAction()).performClick()

        // Landed on Focus Mode's own Ready-to-start screen -- the Draft
        // editor only navigates here, it never creates the Execution
        // itself; that remains Focus Mode's own Start action.
        nodeWithText("Ready to start").awaitDisplayed()
        node(hasText("Start") and hasClickAction()).performClick()

        nodeWithText("Cast on 60 stitches").awaitDisplayed()
    }

    @Test
    fun draftOnlyGuideOffersEditDraftAndOpensTheEditor() {
        // A Draft-only Guide (no published Revision) can never offer
        // Continue/Start -- Drafts are never executable -- so its entry
        // point is the Draft editor instead of Focus Mode, which would have
        // nothing to execute yet.
        openProject()

        nodeWithText("Draft only guide").performScrollTo().assertIsDisplayed()
        node(hasText("Edit draft") and hasClickAction()).performScrollTo().assertIsDisplayed()

        nodeWithText("Draft only guide").performScrollTo().performClick()

        nodeWithText("Draft only guide").awaitDisplayed()
        nodeWithText(
            "Let's write your pattern. Try a Section to name a part (like Body), " +
                "or jump straight to an Instruction if it's simple."
        ).awaitDisplayed()
    }

    private fun openProject() {
        composeTestRule.navigationItem("Projects").performClick()
        nodeWithText(projectName).performClick()
    }

    /**
     * Screens load from Room on a background dispatcher that Compose's idling
     * doesn't track, so each step waits for its node to exist rather than
     * assuming the first idle frame already shows loaded content.
     */
    private fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        composeTestRule.waitUntil(WAIT_MILLIS) {
            composeTestRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        return composeTestRule.onNode(matcher)
    }

    private fun nodeWithText(text: String): SemanticsNodeInteraction = node(hasText(text))

    /** Waits until the node is on screen, which also covers navigation transitions. */
    private fun SemanticsNodeInteraction.awaitDisplayed(): SemanticsNodeInteraction {
        composeTestRule.waitUntil(WAIT_MILLIS) { runCatching { assertIsDisplayed() }.isSuccess }
        return assertIsDisplayed()
    }
}

private const val WAIT_MILLIS = 10_000L

private fun AndroidComposeTestRule<*, MainActivity>.navigationItem(label: String) =
    onNode(hasText(label) and hasClickAction())
