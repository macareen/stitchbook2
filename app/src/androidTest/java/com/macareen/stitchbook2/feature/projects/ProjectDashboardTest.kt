package com.macareen.stitchbook2.feature.projects

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProjectDashboardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun dashboardWorksInLightMode() = check(false, 1f)
    @Test fun dashboardWorksInDarkMode() = check(true, 1f)
    @Test fun dashboardWorksWithLargeText() = check(true, 1.8f)
    private fun check(dark: Boolean, scale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "project-dashboard-original.png")
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(185, 123, 141))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val project = Project("p", "My seaside cardigan", Craft.KNITTING, ProjectType.CARDIGAN, ProjectStatus.ACTIVE,
            null, 1, 2, startDate = "2026-10-08", coverPhotoId = "front")
        val photos = listOf("front", "back").map { Photo(it, project.id, null, Uri.fromFile(file).toString(), file.name,
            if (it == "front") "Front view" else "Back view", null, null, PhotoRole.BEFORE, 1, 2) }
        val pattern = LibraryItem("pattern", "Seaside cardigan", Craft.KNITTING, "Sample Studio", null, emptyList(), null,
            false, 1, 2, pdfUri = "content://test/pattern", pdfFileName = "pattern.pdf")
        val guide = Guide(GuideId("guide"), project.id, "Seaside cardigan - M", null, 1, 2)
        val section = OverviewEntry(NodeId("body"), 0, OverviewEntry.Kind.SECTION, "Body", OverviewStatus.CURRENT,
            3, 8, ExecutionAddress(DefinitionRevisionId("revision"), NodeId("row"), emptyList()))
        val entry = GuideListEntry(guide, GuideEntryAction.CONTINUE, GuideProgressSummary(3, 8, .375, ProgressBasis.STEPS), listOf(section), "Knit to the next marker.", currentContext = "Body row 24")
        val counter = Counter("counter", project.id, "Body rows", "rows", 29, null, 1, 2, null, null, null, false)
        var cover by mutableStateOf(project.coverPhotoId)
        var opened: String? = null
        var incremented = false
        var decremented = false
        var sectionOpened: ProjectSection? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) { Surface(Modifier.systemBarsPadding()) {
                    ProjectDetailScreen(ProjectDetailUiState.Content(project.copy(coverPhotoId = cover), listOf(entry)),
                        onEditProject = {}, onDeleteProject = {}, onOpenGuide = { opened = it }, onEditDraft = {},
                        onCreateGuide = {}, onCreateGuideFromPdf = { _, _ -> }, onUnassignTool = {}, onOpenSection = { sectionOpened = it },
                        hubState = ProjectHubState(counters = listOf(counter), dashboard = ProjectDashboardState(photos = photos,
                            notes = listOf(JournalEntry("note", project.id, "2026-10-09", "Adjustment", "Make the body longer.", 1, 2, JournalNoteTag.ADJUSTMENT, "Body row 24")),
                            patterns = listOf(pattern), sessionCount = 5, workedMillis = 385 * 60_000L)),
                        hubActions = ProjectHubActions(onIncrementCounter = { incremented = true }, onDecrementCounter = { decremented = true },
                            onSetCover = { cover = it.id }), onOpenPattern = { opened = it }, onOpenPdf = { opened = "pdf:$it" })
                } }
            }
        }
        compose.onNodeWithText("My seaside cardigan").assertIsDisplayed()
        capture(dark, scale, "top")
        compose.onNodeWithText("View pattern").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("pattern", opened) }
        if (scale > 1.4f) {
            val author = compose.onNodeWithText("Sample Studio").fetchSemanticsNode().boundsInRoot
            val action = compose.onNodeWithText("View pattern").fetchSemanticsNode().boundsInRoot
            assertTrue("Large-text pattern metadata and actions should stack", author.bottom <= action.top)
        }
        compose.onNodeWithText("View PDF").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("pdf:pattern", opened) }
        compose.onNodeWithText("Next").performScrollTo().performClick()
        compose.onNodeWithText("Back view").assertExists()
        compose.onNodeWithText("Set as cover").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("back", cover) }
        compose.onNodeWithText("Project cover").assertIsNotEnabled()
        capture(dark, scale, "photo")
        compose.onNodeWithText("Previous").performScrollTo().performClick()
        compose.onNodeWithText("Front view").assertExists()
        compose.onNodeWithText("Next: Knit to the next marker.").performScrollTo().assertIsDisplayed()
        capture(dark, scale, "guide")
        compose.onNodeWithText("Make the body longer.").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Adjustment").assertCountEquals(2)
        compose.onNodeWithText("Body row 24").assertExists()
        compose.onNodeWithText("Add note").performScrollTo().performClick()
        compose.onNodeWithText("Guide position").performScrollTo().assertExists()
        compose.onNode(hasText("Body row 24") and hasSetTextAction()).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Add or manage notes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ProjectSection.JOURNAL, sectionOpened) }
        compose.onNodeWithText("+").assertIsDisplayed().performClick()
        compose.onNodeWithText("\u2212").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(incremented); assertTrue(decremented) }
        compose.onNodeWithText("Follow guide").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("guide", opened) }
        assertEquals(PhotoRole.BEFORE, photos.last().role)
    }
    private fun capture(dark: Boolean, scale: Float, part: String) {
        compose.onRoot().captureToImage()
        if (InstrumentationRegistry.getArguments().getString("captureProjectDashboard") != "true") return
        val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/stitchbook-project-$mode-$part.png")).use { it.readBytes() }
    }
}
