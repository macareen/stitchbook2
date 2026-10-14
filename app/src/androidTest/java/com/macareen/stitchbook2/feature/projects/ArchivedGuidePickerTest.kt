package com.macareen.stitchbook2.feature.projects

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ArchivedGuidePickerTest {
    @get:Rule val compose = createComposeRule()
    @Test fun archivedOrphanIsHiddenByDefaultButCanBeExplicitlyRestoredAndUsed() {
        val current = Guide(GuideId("current"), null, "Current guide", null, 1, 2, "pattern", "M")
        val kept = Guide(GuideId("kept"), null, "Kept guide", null, 1, 2, null, "L", true)
        var chosen: Guide? = null
        compose.setContent { StitchbookTheme { PatternGuidePicker(listOf(current, kept), { chosen = it }, {}) } }
        compose.onNodeWithText("Current guide", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Kept guide", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Show archived guides").performClick()
        compose.onNodeWithText("Archived · restore and use", substring = true).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(kept, chosen) }
    }
}
