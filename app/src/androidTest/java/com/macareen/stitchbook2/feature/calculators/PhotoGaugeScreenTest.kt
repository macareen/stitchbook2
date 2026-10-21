package com.macareen.stitchbook2.feature.calculators

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhotoGaugeScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun calibratedPhotoSurvivesRecreationWithoutChangingOriginal() = measure(false, 1f)
    @Test fun calibratedPhotoIsReadableInDarkLargeText() = measure(true, 1.8f)
    private fun measure(dark: Boolean, scale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("synthetic-swatch-", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
            for (y in 0 until 240) for (x in 0 until 240) bitmap.setPixel(x, y, if ((x / 12 + y / 12) % 2 == 0) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val original = file.readBytes()
            val restore = StateRestorationTester(compose)
            restore.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    StitchbookTheme(darkTheme = dark) { Surface(Modifier.systemBarsPadding()) { PhotoGaugeScreen({}, Uri.fromFile(file).toString()) } }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Measured width").fetchSemanticsNodes().isNotEmpty() }
            if (InstrumentationRegistry.getArguments().getString("capturePhotoGauge") == "true") {
                compose.onNodeWithContentDescription("Swatch photo with adjustable counting grid").performScrollTo()
                val mode = if (dark) "dark-large" else "light"
                android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "screencap -p /sdcard/Download/stitchbook-photo-grid-$mode.png")).use { it.readBytes() }
            }
            compose.onNodeWithText("Measured width").performScrollTo().performTextInput("10")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Measured height").performScrollTo().performTextInput("10")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Across: 20.0 per 10 cm · 20.3 per 4 in\nVertical: 28.0 per 10 cm · 28.4 per 4 in").performScrollTo().assertIsDisplayed()
            if (InstrumentationRegistry.getArguments().getString("capturePhotoGauge") == "true") {
                val mode = if (dark) "dark-large" else "light"
                android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "screencap -p /sdcard/Download/stitchbook-photo-gauge-$mode.png")).use { it.readBytes() }
            }
            restore.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Across: 20.0 per 10 cm · 20.3 per 4 in\nVertical: 28.0 per 10 cm · 28.4 per 4 in").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Measured area").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Left edge").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.2f) }
            compose.onNodeWithText("Vertical: 28.0 per 10 cm · 28.4 per 4 in").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Inches").performScrollTo().performClick()
            compose.onNodeWithText("Share gauge").assertDoesNotExist()
            assertArrayEquals(original, file.readBytes())
        } finally { file.delete() }
    }
}
