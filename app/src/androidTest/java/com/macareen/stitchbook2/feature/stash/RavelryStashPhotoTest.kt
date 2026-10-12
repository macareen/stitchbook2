package com.macareen.stitchbook2.feature.stash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.photos.RavelryThumbnailCache
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.model.PhotoRole
import com.macareen.stitchbook2.domain.ravelry.RavelryImportPlanner
import com.macareen.stitchbook2.domain.ravelry.RavelryStashEntry
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class RavelryStashPhotoTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cachedPhotoAppearsOnTheStashCard() = checkPhoto(false)
    @Test fun cachedPhotoAppearsOnTheStashCardInDarkMode() = checkPhoto(true)

    private fun checkPhoto(dark: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val url = "https://images.example.test/stash-${if (dark) "dark" else "light"}.jpg"
        val image = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(239, 225, 213))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(161, 101, 125) }
        canvas.drawOval(180f, 50f, 460f, 350f, paint)
        paint.color = Color.rgb(213, 163, 185)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        for (offset in 0..8) canvas.drawOval(185f + offset * 10, 55f, 455f - offset * 10, 345f, paint)
        val bytes = ByteArrayOutputStream().use { out -> image.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
        image.recycle()
        runBlocking { RavelryThumbnailCache.refresh(context, url) { bytes } }
        val entry = RavelryStashEntry(1, null, 1, "Merino worsted", "Sample yarn", "Worsted",
            "Dusty rose", null, null, 3.0, 220.0, 100.0)
        val item = RavelryImportPlanner.plan(listOf(entry), emptyList(), emptyList(), emptyList(), 0).newStash.single()
        val photo = Photo("ravelry-stash-photo-1", null, item.id, url, "Ravelry stash photo",
            null, null, null, PhotoRole.NONE, 0, 0)
        compose.setContent {
            StitchbookTheme(darkTheme = dark) {
                Surface(modifier = Modifier.systemBarsPadding()) {
                    StashScreen(
                        StashUiState.Content(listOf(item), StashFilterState(), true,
                            photosByItemId = mapOf(item.id to listOf(photo))),
                        onSearchQueryChanged = {}, onCategoryFilterChanged = {}, onSaveItem = { _, _ -> },
                        onDeleteItem = {}, onExportCsv = {}, onImportCsv = {}, importReport = null,
                        onDismissImportReport = {}
                    )
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Ravelry stash photo").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Ravelry stash photo").assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureStashPhoto") == "true") {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            val mode = if (dark) "dark" else "light"
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/stitchbook-stash-photo-$mode.png")).use { it.readBytes() }
        }
    }
}
