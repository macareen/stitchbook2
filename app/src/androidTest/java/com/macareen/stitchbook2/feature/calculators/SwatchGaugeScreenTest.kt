package com.macareen.stitchbook2.feature.calculators

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SwatchGaugeScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun axesWorkIndependentlyInLightMode() = check(false, 1f)
    @Test fun axesWorkIndependentlyInDarkMode() = check(true, 1f)
    @Test fun measurementsRemainReachableAtLargeText() = check(true, 1.8f)

    private fun enter(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextInput(text)
        Espresso.closeSoftKeyboard()
    }

    private fun check(dark: Boolean, scale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                StitchbookTheme(darkTheme = dark) { Surface(Modifier.systemBarsPadding()) { CalculatorsScreen() } }
            }
        }
        enter("swatch_stitches", "20.5")
        enter("swatch_width", "10")
        compose.onNodeWithText("Across: 20.5 per 10 cm").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Across: 20.8 per 4 in").performScrollTo().assertIsDisplayed()
        enter("swatch_height_count", "30")
        enter("swatch_height", "8")
        compose.onNodeWithText("Height: 37.5 per 10 cm").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Height: 38.1 per 4 in").performScrollTo().assertIsDisplayed()
        capture(dark, scale)
        compose.onNodeWithText("Inches").performScrollTo().performClick()
        assertValue("swatch_stitches", "20.5")
        assertValue("swatch_width", "")
        assertValue("swatch_height_count", "30")
        assertValue("swatch_height", "")
        enter("swatch_width", "4")
        compose.onNodeWithText("Across: 20.5 per 4 in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Across: 20.2 per 10 cm").performScrollTo().assertIsDisplayed()
    }

    private fun assertValue(tag: String, value: String) {
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)))
    }

    private fun capture(dark: Boolean, scale: Float) {
        if (InstrumentationRegistry.getArguments().getString("captureSwatchGauge") != "true") return
        assertTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        compose.onRoot().captureToImage()
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
        assertTrue("Clear emulator System UI hangs before capturing",
            root?.findAccessibilityNodeInfosByText("System UI isn't responding").isNullOrEmpty())
        val mode = if (scale > 1f) "large" else if (dark) "dark" else "light"
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/stitchbook-swatch-gauge-$mode.png")).use { it.readBytes() }
    }
}
