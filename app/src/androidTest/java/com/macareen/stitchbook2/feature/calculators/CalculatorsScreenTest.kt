package com.macareen.stitchbook2.feature.calculators

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalculatorsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(dark: Boolean = false) = compose.setContent {
        StitchbookTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { CalculatorsScreen(Modifier.systemBarsPadding()) } }
    }
    private fun enter(label: String, text: String) {
        compose.onNodeWithText(label).performScrollTo().performTextInput(text)
        Espresso.closeSoftKeyboard()
    }
    @Test fun yarnShowsATotalBeforeYourSkeinAmountAndThenShowsTheSkeinCount() {
        show()
        enter("Skeins", "4")
        enter("Per skein", "200")
        compose.onNodeWithText("Total: 874.9 yd").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add the amount in your skein to see how many you need.").assertIsDisplayed()
        enter("Your skein", "250")
        compose.onNodeWithText("You need 4 skeins").performScrollTo().assertIsDisplayed()
        screenshot("yarn")
    }
    @Test fun gaugePairsWorkIndependentlyAndFieldsHaveEqualHeights() = gauge(false)
    @Test fun gaugeAnswersAreReadableInDarkMode() = gauge(true)

    private fun gauge(dark: Boolean) {
        show(dark)
        enter("Pattern sts", "22")
        enter("Your sts", "20")
        compose.onNodeWithText("Stitches: multiply by 0.909").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add a whole number for Sts to adapt to convert a specific count.").performScrollTo().assertIsDisplayed()
        enter("Sts to adapt", "110")
        compose.onNodeWithText("Use 100 stitches").performScrollTo().assertIsDisplayed()
        enter("Pattern rows", "30")
        enter("Your rows", "36")
        enter("Rows to adapt", "80")
        val labels = listOf("Pattern sts", "Pattern rows", "Your sts", "Your rows", "Sts to adapt", "Rows to adapt")
        val heights = labels.map { compose.onNodeWithText(it).fetchSemanticsNode().size.height }
        heights.forEach { assertEquals(heights.first(), it) }
        compose.onNodeWithText("Work 96 rows").performScrollTo().assertIsDisplayed()
        screenshot(if (dark) "gauge-dark" else "gauge-light")
    }
    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureCalculators") != "true") return
        compose.waitForIdle()
        val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/stitchbook-calculator-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(pipe).use { it.readBytes() }
    }
}
