package com.macareen.stitchbook2.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import java.io.File
import org.junit.Assert.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeDashboardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun photoAndContinueWorkInLightMode() = check(false, 1f, true)
    @Test fun photoAndContinueWorkInDarkMode() = check(true, 1f, true)
    @Test fun photoAndActionsWorkAtLargeTextSize() = check(true, 1.8f, true)
    @Test fun activeProjectWithoutAnExecutionOpensTheProject() = check(false, 1f, false)
    private fun check(dark: Boolean, scale: Float, resumable: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "original-home-test-photo.png")
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(224, 231, 214))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(161, 95, 113) }
            drawRoundRect(150f, 70f, 450f, 340f, 30f, 30f, paint)
            paint.color = Color.rgb(224, 231, 214)
            drawRect(292f, 70f, 308f, 340f, paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val project = Project("current", "Autumn cardigan", Craft.CROCHET, ProjectType.CARDIGAN, ProjectStatus.ACTIVE, null, 1, 2)
        val photo = Photo("photo", project.id, null, android.net.Uri.fromFile(file).toString(), file.name, null, null, null, PhotoRole.NONE, 1, 1)
        assertNotNull("Original test photo must decode before UI checks", runBlocking { com.macareen.stitchbook2.data.photos.PhotoThumbnailLoader.load(context, photo.uri, 1024) })
        val resume = if (resumable) ResumeGuide("guide", "Body guide", project.name, project.id, 23) else null
        var opened: String? = null
        var resumed: ResumeGuide? = null
        var destination: String? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) {
                    Surface(Modifier.systemBarsPadding()) {
                        HomeScreen(HomeUiState.Content(1, 1, 1, listOf(project), resume, project, photo),
                            onNewProject = { destination = "new" }, onOpenProject = { opened = it }, onOpenProjects = {},
                            onOpenLibrary = { destination = "library" }, onOpenStash = { destination = "stash" },
                            onResumeGuide = { resumed = it }, onOpenCounters = { destination = "counters" },
                            onOpenCalculators = { destination = "calculators" })
                    }
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Current project photo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Current project photo").assertIsDisplayed()
        compose.onNodeWithText(if (resumable) "Continue" else "Open project").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { if (resumable) assertEquals(resume, resumed) else assertEquals(project.id, opened) }
        compose.onRoot().captureToImage()
        if (InstrumentationRegistry.getArguments().getString("captureHomeDashboard") == "true") {
            val mode = if (!resumable) "open" else if (scale > 1f) "large" else if (dark) "dark" else "light"
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/stitchbook-home-dashboard-$mode.png")).use { it.readBytes() }
        }
        listOf("Library" to "library", "Stash" to "stash", "Counters" to "counters", "Calculators" to "calculators").forEach { (text, target) ->
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(target, destination) }
        }
    }
}
