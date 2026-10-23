package com.macareen.stitchbook2.feature.draft

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ManualTargetDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun stitchTargetNeedsWholeNumberAndSavesItsComparison() {
        var saved: ManualTarget? = null
        compose.setContent { StitchbookTheme { ManualTargetDialog(null, false, null, { saved = it }, {}) } }
        compose.onNodeWithText("At most").performScrollTo().performClick()
        compose.onNodeWithText("stitches").performScrollTo().performClick()
        compose.onNodeWithText("Target (stitches)").performScrollTo().performTextInput("20.5")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Target (stitches)").performTextReplacement("20")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(ManualTarget(TargetUnit.STITCHES, 20.0, TargetComparison.AT_MOST), saved) }
    }
}
