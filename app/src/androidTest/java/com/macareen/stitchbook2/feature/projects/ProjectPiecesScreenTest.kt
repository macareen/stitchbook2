package com.macareen.stitchbook2.feature.projects

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.ProjectPiece
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProjectPiecesScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun lightPiecesAndSaveFailureRemainReadable() = check(false, 1f)
    @Test fun darkPiecesAndSaveFailureRemainReadable() = check(true, 1f)
    @Test fun actualLargeTextKeepsPieceActionsAndFailedEntryReachable() = check(true, 1.8f)
    private fun check(dark: Boolean, scale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = android.provider.Settings.System.getFloat(instrumentation.targetContext.contentResolver,
            android.provider.Settings.System.FONT_SCALE, 1f)
        val changeScale = kotlin.math.abs(original - scale) > .05f
        if (changeScale) {
            assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            setScaleAndWait(scale)
        }
        try {
            instrumentation.runOnMainSync {
                androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            val left = ProjectPiece("left", "p", "g", "Left sleeve", false, 1, 2)
            val right = left.copy(id = "right", name = "Right sleeve")
            val archived = left.copy(id = "archived", name = "First sleeve attempt", isArchived = true)
            var state by mutableStateOf(ProjectPiecesState(loading = false, entries = listOf(
                PieceEntry(left, "Sleeve guide", 2, true, false, 1),
                PieceEntry(right, "Sleeve guide", 0, false, false, null),
                PieceEntry(archived, "Sleeve guide", 1, true, false, 1)), choices = listOf(PieceGuideChoice("g", "Sleeve guide"))))
            var opened: String? = null
            var saves = 0
            compose.setContent { StitchbookTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) { Box(Modifier.systemBarsPadding()) {
                ProjectPiecesScreen(state,
                    onAdd = { state = state.copy(editor = PieceEditor(name = "New sleeve", guideId = "g")) },
                    onEdit = { state = state.copy(editor = PieceEditor(it, it.name, it.guideId)) },
                    onArchive = { piece -> state = state.copy(entries = state.entries.map { if (it.piece.id == piece.id) it.copy(piece = piece.copy(isArchived = !piece.isArchived)) else it }) },
                    onOpen = { opened = it.id }, onBack = {},
                    onName = { state = state.copy(editor = state.editor?.copy(name = it, error = false)) },
                    onGuide = {},
                    onSave = { saves++; state = state.copy(editor = state.editor?.copy(error = true, busy = saves > 1)) },
                    onDismiss = { state = state.copy(editor = null) }, onRetry = {},
                    onQuantity = { state = state.copy(editor = state.editor?.copy(quantity = it)) },
                    onCopy = { state = state.copy(editor = PieceEditor(guideId = it.guideId, copySource = it)) })
                } }
            } }
            compose.onNodeWithTag("pieces_list").performScrollToNode(hasText("Left sleeve"))
            compose.onNodeWithText("Left sleeve").assertIsDisplayed()
            compose.onNodeWithTag("pieces_list").performScrollToNode(hasText("2 steps completed"))
            compose.onNodeWithText("2 steps completed").assertIsDisplayed()
            capture("list", dark, scale)
            compose.onNodeWithTag("pieces_list").performScrollToNode(hasText("Open piece"))
            compose.onNodeWithText("Open piece").performClick()
            compose.runOnIdle { assertEquals("left", opened) }
            compose.onNodeWithTag("pieces_list").performScrollToNode(hasText("Start piece"))
            compose.onNodeWithText("Start piece").performClick()
            compose.runOnIdle { assertEquals("right", opened) }
            compose.onNodeWithText("First sleeve attempt").assertDoesNotExist()
            compose.onNodeWithTag("pieces_list").performScrollToNode(hasText("Show archived pieces"))
            compose.onNodeWithText("Show archived pieces").performClick()
            compose.onNodeWithTag("pieces_list").performScrollToNode(hasText("Restore"))
            compose.onNodeWithText("Restore").performClick()
            compose.runOnIdle { assertEquals(false, state.entries.last().piece.isArchived) }
            compose.onNodeWithTag("pieces_list").performScrollToIndex(0)
            compose.onNodeWithTag("pieces_list").performScrollToNode(hasText("Make fresh copy"))
            compose.onAllNodesWithText("Make fresh copy").onFirst().performClick()
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithText("Piece name").performTextReplacement("Other sleeve")
            compose.onNodeWithText("Piece name").performImeAction()
            compose.onNodeWithText("Choose a name for the new piece. It uses the same guide and starts unworked on its latest published version. Progress, saved places and archived status are not copied. Instructions are not mirrored.")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Quantity").assertDoesNotExist()
            compose.onNodeWithText("Save").assertIsEnabled()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("pieces_list").performScrollToNode(hasText("Add piece"))
            compose.onNodeWithText("Add piece").performClick()
            compose.onNodeWithText("Piece name").performTextReplacement("Sleeve kept after failure")
            compose.onNodeWithText("Piece name").performImeAction()
            compose.onNodeWithText("Piece name").assertIsNotFocused()
            compose.onNodeWithText("Quantity").performScrollTo().performTextReplacement("2")
            compose.onNodeWithText("Quantity").performImeAction()
            compose.onNodeWithText("2 pieces will be created:").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Sleeve kept after failure 1\nSleeve kept after failure 2").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Save").performClick()
            compose.onNodeWithText("Sleeve kept after failure").assertExists()
            compose.onNodeWithText("Could not save. Your work is kept. Close and reopen this form to review any changes.").assertIsDisplayed()
            capture("dialog", dark, scale)
            compose.onNodeWithText("Save").performClick()
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithText("Cancel").assertIsNotEnabled()
        } finally { if (changeScale) setScaleAndWait(original) }
    }
    private fun capture(kind: String, dark: Boolean, scale: Float) {
        if (InstrumentationRegistry.getArguments().getString("capturePieces") != "true") return
        assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(500)
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
        assertTrue("Clear emulator System UI hangs before capturing", root?.findAccessibilityNodeInfosByText("System UI isn't responding").isNullOrEmpty())
        val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/stitchbook-pieces-$kind-$mode.png")).use { it.readBytes() }
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
