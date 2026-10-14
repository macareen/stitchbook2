package com.macareen.stitchbook2.feature.focus

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.guide.DefinitionRevision
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GuideRevisionReviewDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun lightReviewRetainsChoicesAndSavedPlaceActions() = check(false, 1f)
    @Test fun darkReviewRetainsChoicesAndSavedPlaceActions() = check(true, 1f)
    @Test fun largeSystemTextKeepsReviewErrorsAndActionsReachable() = check(true, 1.8f)
    private fun check(dark: Boolean, scale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = android.provider.Settings.System.getFloat(instrumentation.targetContext.contentResolver,
            android.provider.Settings.System.FONT_SCALE, 1f)
        val changeScale = kotlin.math.abs(original - scale) > .05f
        if (changeScale) { assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish")); setScaleAndWait(scale) }
        try {
            instrumentation.runOnMainSync {
                androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).apply {
                    isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
                }
            }
            val a = NodeId("a"); val b = NodeId("b")
            val source = GuideDefinition(GuideId("g"), DefinitionRevisionId("r1"), listOf(a, b), listOf(Instruction(a, "Knit."), Instruction(b, "Purl.")))
            val target = GuideDefinition(source.guideId, DefinitionRevisionId("r2"), source.rootNodeIds, listOf(Instruction(a, "Knit."), Instruction(b, "Turn.")))
            val sourceAddresses = setOf(ExecutionAddress(source.revisionId, a), ExecutionAddress(source.revisionId, b))
            val work = PersistedExecution(ExecutionState(ExecutionId("current"), source.guideId, source.revisionId,
                ExecutionAddress(source.revisionId, b), sourceAddresses, ExecutionStatus.ACTIVE), 2, 1, 2, null)
            val revision = DefinitionRevision(source.revisionId, source.guideId, 1, 1, source)
            val comparison = RevisionProgressComparison.preview(source, target, sourceAddresses)
            val saved = SavedGuidePlace(work.copy(state = work.state.copy(executionId = ExecutionId("saved"))), revision, "Body · round 2", "Purl.")
            val panel = GuideRevisionPanel(work, revision, ReviewedRevisionChange("p", work, source, 1, target, 2),
                comparison.candidates.map { ReviewMatch(it, "Body · round 1") },
                comparison.unmatched.map { ReviewUnmatched(it, "Body · round 2", "Turn.") }, listOf(saved))
            var state by mutableStateOf(GuideRevisionReviewState(loading = false, panel = panel, selected = comparison.candidates.map { it.target }.toSet()))
            var resumed: String? = null
            var attempts = 0
            compose.setContent { StitchbookTheme(darkTheme = dark) { Surface(Modifier.fillMaxSize()) { Box(Modifier.systemBarsPadding()) {
                GuideRevisionReviewDialog(state,
                    onSelect = { address -> state = state.copy(selected = if (address in state.selected) state.selected - address else state.selected + address) },
                    onSelectAll = { all -> state = state.copy(selected = if (all) comparison.candidates.map { it.target }.toSet() else emptySet()) },
                    onApply = { attempts++; state = state.copy(failed = true, busy = attempts > 1) },
                    onResume = { resumed = it.work.state.executionId.value }, onClose = {})
            } } } }
            compose.onNodeWithTag("revision_review_list").performScrollToNode(hasText("Resume saved place"))
            compose.onNodeWithText("Resume saved place").performClick()
            compose.runOnIdle { assertEquals("saved", resumed) }
            compose.onNodeWithTag("revision_review_list").performScrollToNode(hasTestTag("revision_match_0"))
            compose.onNodeWithTag("revision_match_0").assertIsOn().performClick().assertIsOff()
            compose.onNodeWithText("Use revision 2").assertIsDisplayed().performClick()
            compose.onNodeWithText("Could not change versions. Your work is kept.").assertIsDisplayed()
            compose.runOnIdle { assertTrue(state.selected.isEmpty()) }
            capture(dark, scale)
            compose.onNodeWithText("Use revision 2").performClick()
            compose.onNodeWithText("Use revision 2").assertIsNotEnabled()
            compose.onNodeWithText("Keep current").assertIsNotEnabled()
            compose.runOnIdle { assertEquals(2, attempts) }
        } finally { if (changeScale) setScaleAndWait(original) }
    }
    private fun capture(dark: Boolean, scale: Float) {
        if (InstrumentationRegistry.getArguments().getString("captureRevisionReview") != "true") return
        assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync(); Thread.sleep(500)
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
        assertTrue("Clear emulator System UI hangs before capturing", root?.findAccessibilityNodeInfosByText("System UI isn't responding").isNullOrEmpty())
        val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/stitchbook-revision-review-$mode.png")).use { it.readBytes() }
    }
    private fun setScaleAndWait(scale: Float) {
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "settings put system font_scale $scale")).use { it.readBytes() }
        compose.waitUntil(10_000) {
            runCatching { compose.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                kotlin.math.abs(compose.activity.resources.configuration.fontScale - scale) < .05f }.getOrDefault(false)
        }
    }
}
