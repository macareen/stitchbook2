package com.macareen.stitchbook2.feature.library

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PatternRemovalDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val item = LibraryItem("pattern", "Sample pattern", Craft.CROCHET, null, null, emptyList(), null, false, 0, 0)
    private val preview = PatternRemovalPreview(item, listOf("first", "second"), (1..15).map {
        PatternRemovalProject("project-$it", "Project $it with a longer title", ProjectStatus.ACTIVE)
    })

    @Test fun dependenciesAreReadableInLightMode() = check(false, 1f)
    @Test fun dependenciesAreReadableInDarkMode() = check(true, 1f)
    @Test fun dependenciesAndActionsRemainReachableAtLargeText() = check(true, 1.8f)
    @Test fun busyRemovalDisablesBothActions() {
        compose.setContent { StitchbookTheme {
            PatternRemovalDialog(PatternRemovalUiState(item, preview, isLoading = false, isRemoving = true), {}, {})
        } }
        compose.onNodeWithText("Remove reference").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    private fun check(dark: Boolean, scale: Float) {
        val original = compose.activity.resources.configuration.fontScale
        val changeScale = scale > 1f
        if (changeScale) {
            assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            setScaleAndWait(scale)
        }
        try { checkDialog(dark, scale) } finally { if (changeScale) setScaleAndWait(original) }
    }

    private fun checkDialog(dark: Boolean, scale: Float) {
        var confirmations = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) {
                    PatternRemovalDialog(PatternRemovalUiState(item, preview, isLoading = false,
                        problem = PatternRemovalProblem.CHANGED), {}, { confirmations++ })
                }
            }
        }
        compose.onNodeWithText("15 projects reference this pattern or its guides:").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Project 1 with a longer title · Active").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Project 15 with a longer title · Active").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("References changed while this dialog was open. Review the updated list before removing.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Remove reference").assertIsDisplayed().assertIsEnabled()
        if (InstrumentationRegistry.getArguments().getString("capturePatternRemoval") == "true") {
            assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            compose.onNodeWithText("Project 15 with a longer title · Active").performScrollTo()
            compose.onNode(isDialog()).captureToImage()
            val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            assertTrue(root?.findAccessibilityNodeInfosByText("System UI isn't responding").isNullOrEmpty())
            val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/stitchbook-pattern-removal-$mode.png")).use { it.readBytes() }
        }
        compose.onNodeWithText("Remove reference").performClick()
        compose.runOnIdle { assertEquals(1, confirmations) }
    }

    private fun setScaleAndWait(scale: Float) {
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put system font_scale $scale")).use { it.readBytes() }
        compose.waitUntil(10_000) {
            runCatching { compose.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                kotlin.math.abs(compose.activity.resources.configuration.fontScale - scale) < .05f }.getOrDefault(false)
        }
    }
}
