package com.macareen.stitchbook2.feature.library

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArchiveGuideScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun archivedGuideCanBeFoundAndRestoredWithoutReplacingItsIdentity() {
        val pattern = LibraryItem("pattern", "Sample cardigan", Craft.KNITTING, null, null, emptyList(), null, false, 0, 0)
        val original = Guide(GuideId("guide"), null, "Sample cardigan · L", null, 1, 2, "pattern", "L")
        var guide by mutableStateOf(original)
        var changes = 0
        compose.setContent { StitchbookTheme { Surface(Modifier.systemBarsPadding()) {
            val entry = PatternGuideEntry(guide, true)
            PatternGuidesScreen(PatternGuidesUiState.Content(pattern,
                if (guide.isArchived) emptyList() else listOf(entry), false, false,
                archivedGuides = if (guide.isArchived) listOf(entry) else emptyList()),
                {}, {}, { _, _ -> }, onArchiveGuide = { id, archived ->
                    assertEquals("guide", id); guide = guide.copy(isArchived = archived); changes++
                })
        } } }
        compose.onNodeWithText("Archive guide").performScrollTo().performClick()
        compose.onNodeWithText("Archived guides (1)").performScrollTo().performClick()
        compose.onNodeWithText("Archived guide · progress is kept").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Restore guide").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("No guides yet.", substring = true).assertCountEquals(0)
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureGuideArchive") == "true") {
            assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            compose.waitForIdle()
            android.os.ParcelFileDescriptor.AutoCloseInputStream(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/stitchbook-guide-archive.png")).use { it.readBytes() }
        }
        compose.onNodeWithText("Restore guide").performClick()
        compose.onNodeWithText("Archive guide").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, changes); assertEquals(original, guide) }
    }
}
