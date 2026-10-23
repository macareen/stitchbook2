package com.macareen.stitchbook2.feature.draft

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ManualTargetDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun sectionBodyRequiresReviewAndShowsAllSourceStepsAtLargeText() {
        var saved: ManualTarget? = null
        val target = ManualTarget(TargetUnit.CENTIMETRES, 12.0)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                StitchbookTheme(darkTheme = true) { ManualTargetDialog(target, false, null, { saved = it }, {},
                    sourceText = "Body\nRow 1: Knit. (p.2 l.1)\nRow 2: Purl. (p.2 l.2)", bodyReview = true) }
            }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Body\nRow 1: Knit. (p.2 l.1)\nRow 2: Purl. (p.2 l.2)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("I checked the source, selected size, cycle and target").performScrollTo().performClick()
        compose.runOnIdle {
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "target-body-review-large.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(target, saved) }
    }
    @Test fun suggestionNeedsExplicitSourceReviewAndCancelDoesNotConvert() {
        var saved: ManualTarget? = null
        var dismissed = false
        val suggestion = ManualTarget(TargetUnit.CENTIMETRES, 12.0)
        compose.setContent { StitchbookTheme { ManualTargetDialog(null, false, null, { saved = it }, { dismissed = true },
            suggestion, "Knit all stitches until piece measures 12 cm. (p.2 l.3)") } }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Knit all stitches until piece measures 12 cm. (p.2 l.3)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("I checked the source, selected size, cycle and target").performScrollTo().performClick()
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(saved); assertTrue(dismissed) }
    }

    @Test fun reviewedSuggestionSavesTheSuggestedValue() {
        var saved: ManualTarget? = null
        val suggestion = ManualTarget(TargetUnit.STITCHES, 20.0, TargetComparison.AT_MOST)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                StitchbookTheme(darkTheme = true) { ManualTargetDialog(null, false, null, { saved = it }, {}, suggestion,
                    "Decrease one stitch every row until 20 stitches remain. (p.2 l.4)") }
            }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("I checked the source, selected size, cycle and target").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.runOnIdle {
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "pdf-target-review-large.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(suggestion, saved) }
    }
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
