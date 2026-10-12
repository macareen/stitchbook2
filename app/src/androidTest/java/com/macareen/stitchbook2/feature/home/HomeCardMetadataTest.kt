package com.macareen.stitchbook2.feature.home

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import java.text.DateFormat
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeCardMetadataTest {
    @get:Rule val compose = createComposeRule()
    @Test fun compactMetadataIsReadableInLightMode() = checkMetadata(false, 1f)
    @Test fun compactMetadataIsReadableInDarkMode() = checkMetadata(true, 1f)
    @Test fun metadataAndQuickLinksRemainReadableAtLargeFontScale() = checkMetadata(true, 1.8f)

    private fun checkMetadata(dark: Boolean, scale: Float) {
        val timestamp = 1791806400000L
        val project = Project("sample", "Autumn Tunisian cardigan", Craft.TUNISIAN_CROCHET,
            ProjectType.CARDIGAN, ProjectStatus.ACTIVE, null, timestamp, timestamp)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) {
                    Surface(modifier = Modifier.systemBarsPadding()) {
                        HomeScreen(HomeUiState.Content(1, 1, 1, listOf(project), null),
                            onNewProject = {}, onOpenProject = {}, onOpenProjects = {}, onOpenLibrary = {},
                            onOpenStash = {}, onResumeGuide = {})
                    }
                }
            }
        }
        val updated = "Updated " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(timestamp))
        listOf("Tunisian crochet", updated).forEach { text ->
            compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("Metadata wraps: $text", 1, layouts.single().lineCount)
            assertTrue("Metadata glyphs overflow: $text", layouts.single().getLineRight(0) <= layouts.single().size.width + 1f)
        }
        compose.onNodeWithText("Calculators").performScrollTo().assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureHomeMetadata") == "true") {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            compose.onRoot().captureToImage()
            instrumentation.waitForIdleSync()
            val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/stitchbook-home-metadata-$mode.png")).use { it.readBytes() }
        }
    }
}
