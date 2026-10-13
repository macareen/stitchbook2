package com.macareen.stitchbook2.feature.library

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.library.PdfCoverCache
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PatternPageLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun patternPageWorksInLightMode() = checkPage(false, 1f)
    @Test fun patternPageWorksInDarkMode() = checkPage(true, 1f)
    @Test fun patternPageWorksAtLargeFontScale() = checkPage(true, 1.8f)

    private fun checkPage(dark: Boolean, scale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "original-test-cover.pdf")
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(420, 560, 1).create())
            page.canvas.drawColor(Color.rgb(245, 229, 222))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(94, 71, 70); textSize = 28f }
            page.canvas.drawText("AUTUMN CARDIGAN", 30f, 65f, paint)
            paint.color = Color.rgb(211, 153, 126)
            page.canvas.drawRoundRect(100f, 130f, 320f, 470f, 28f, 28f, paint)
            paint.color = Color.rgb(245, 229, 222)
            page.canvas.drawRect(202f, 130f, 218f, 470f, paint)
            pdf.finishPage(page)
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val uri = android.net.Uri.fromFile(file).toString()
        assertNotNull(runBlocking { PdfCoverCache.get(context).cover(uri, 300) })
        val notes = "A soft cardigan for everyday walks. ".repeat(15)
        val pattern = LibraryItem("pattern", "Autumn Cardigan", Craft.KNITTING, "A. Example", null,
            emptyList(), notes, false, 0, 0, pdfUri = uri, sizes = "XS, S, M, L, XL, 2XL, 3XL, 4XL, 5XL",
            gauge = "20 stitches and 28 rows per 10 cm", recommendedTools = "4 mm circular needles", yardageRequired = 1200.0)
        val guide = Guide(GuideId("medium"), null, "Autumn Cardigan \u00b7 M", null, 0, 0,
            libraryItemId = pattern.id, sizeLabel = "M")
        var selected: String? = null
        var opened: String? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) {
                    Surface(Modifier.systemBarsPadding()) {
                        PatternGuidesScreen(PatternGuidesUiState.Content(pattern, listOf(PatternGuideEntry(guide, true)), false, false, completedProjects = 2),
                            onOpenPdf = {}, onEditGuide = { opened = it }, onCreateGuide = { size, _ -> selected = size })
                    }
                }
            }
        }
        compose.onNodeWithText("Autumn Cardigan").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Made in 2 completed projects").assertIsDisplayed()
        capture(dark, scale, "top")
        compose.onNodeWithText("Read more").performScrollTo().performClick()
        compose.onNodeWithText("Read less").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("pattern-guide-medium").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("medium", opened) }
        compose.onNodeWithText("Size M").assertIsDisplayed()
        compose.onNodeWithText("Ready").assertIsDisplayed()
        capture(dark, scale, "sizes")
        compose.onNodeWithTag("build-guide-5XL").performScrollTo().performClick()
        compose.onNodeWithText("Autumn Cardigan \u00b7 5XL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Create and edit").performClick()
        compose.runOnIdle { assertEquals("5XL", selected) }
        compose.onNodeWithText("Yarn needed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("About 1200 yards").performScrollTo().assertIsDisplayed()
        capture(dark, scale, "details")
    }

    private fun capture(dark: Boolean, scale: Float, location: String) {
        if (InstrumentationRegistry.getArguments().getString("capturePatternPage") != "true") return
        compose.mainClock.advanceTimeBy(100)
        compose.onRoot().captureToImage()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/stitchbook-pattern-page-$mode-$location.png")).use { it.readBytes() }
    }
}
