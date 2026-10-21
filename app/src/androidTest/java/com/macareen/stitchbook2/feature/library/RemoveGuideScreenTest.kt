package com.macareen.stitchbook2.feature.library

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RemoveGuideScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun removalRequiresConfirmationAndCancelKeepsTheGuide() {
        var removed: String? = null
        val pattern = LibraryItem("pattern", "Sample cardigan", Craft.KNITTING, null, null,
            emptyList(), null, false, 0, 0)
        val guide = Guide(GuideId("guide"), null, "Sample cardigan · L", null, 0, 0, "pattern", "L")
        compose.setContent { StitchbookTheme {
            Surface(Modifier.systemBarsPadding()) {
                PatternGuidesScreen(PatternGuidesUiState.Content(pattern, listOf(PatternGuideEntry(guide, false)), false, false),
                    {}, {}, { _, _, _ -> }, onRemoveGuide = { removed = it })
            }
        } }
        compose.onNodeWithText("Remove guide").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(null, removed) }
        compose.onNodeWithText("Remove guide").performScrollTo().performClick()
        compose.onNodeWithText("Remove \"Sample cardigan · L\"?", substring = true).assertIsDisplayed()
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureGuideRemoval") == "true") {
            org.junit.Assert.assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            android.os.SystemClock.sleep(500) // Android dialog window transition, outside the Compose clock.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/stitchbook-guide-removal.png")).use { it.readBytes() }
        }
        compose.onAllNodesWithText("Remove guide").filter(hasClickAction()).onLast().performClick()
        compose.runOnIdle { assertEquals("guide", removed) }
    }
}
