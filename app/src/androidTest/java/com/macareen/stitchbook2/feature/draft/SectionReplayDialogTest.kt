package com.macareen.stitchbook2.feature.draft

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.guide.*
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SectionReplayDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun previewRequiresSourceAndReviewBeforeReplacingReminder() = check(false, 1f)
    @Test fun previewWorksInDarkMode() = check(true, 1f)
    @Test fun previewWorksAtLargeText() = check(true, 1.8f)

    private fun setScale(scale: Float) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put system font_scale $scale")).use { it.readBytes() }
        compose.waitUntil(10_000) { runCatching {
            compose.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                kotlin.math.abs(compose.activity.resources.configuration.fontScale - scale) < .05f
        }.getOrDefault(false) }
    }

    private fun check(dark: Boolean, scale: Float) {
        val originalScale = compose.activity.resources.configuration.fontScale
        if (scale > 1f) { assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish")); setScale(scale) }
        try {
            val source = NodeId("source")
            val target = NodeId("target")
            val nodes = listOf(DraftNode(source, DraftNodeType.SECTION, title = "First sleeve", children = listOf(NodeId("co"), NodeId("repeat"), target)),
                DraftNode(NodeId("co"), DraftNodeType.INSTRUCTION, instructionText = "Cast on 24 sts. (p.2)"),
                DraftNode(NodeId("repeat"), DraftNodeType.REPEAT, repeatCount = 3, repeatLabel = "Cuff", children = listOf(NodeId("range"))),
                DraftNode(NodeId("range"), DraftNodeType.RANGE, rangeUnitLabel = "row", rangeStartInclusive = 1, rangeEndInclusive = 2, children = listOf(NodeId("knit"))),
                DraftNode(NodeId("knit"), DraftNodeType.INSTRUCTION, instructionText = "Knit. (p.2)"),
                DraftNode(target, DraftNodeType.INSTRUCTION, instructionText = "Repeat for the second sleeve. (p.3)"))
            val draft = GuideDraft(DraftId("draft"), GuideId("guide"), null, 0, 0, 0, listOf(source), nodes)
            val review = SectionReplayReview(draft, target, DraftSectionReplay.choices(draft, target))
            var confirmations = 0
            compose.setContent { StitchbookTheme(darkTheme = dark) {
                SectionReplayDialog(review, false, null, { assertEquals(source, it); confirmations++ }, {})
            } }
            compose.onNodeWithText("Replace reminder").assertIsNotEnabled()
            compose.onNodeWithText("First sleeve").performScrollTo().performClick()
            compose.onNodeWithText("Cast on 24 sts. (p.2)").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Cuff: ×3").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Knit. (p.2)").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Only the steps between the source heading", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Replace reminder").assertIsNotEnabled()
            compose.onNode(isToggleable()).performScrollTo().performClick()
            compose.onNodeWithText("Replace reminder").assertIsEnabled().performClick()
            compose.runOnIdle { assertEquals(1, confirmations) }
            if (InstrumentationRegistry.getArguments().getString("captureSectionReplay") == "true") {
                assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                java.io.File(context.getExternalFilesDir(null), "synthetic-section-replay-$dark-$scale.png").outputStream().use {
                    compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally { if (scale > 1f) setScale(originalScale) }
    }
}
