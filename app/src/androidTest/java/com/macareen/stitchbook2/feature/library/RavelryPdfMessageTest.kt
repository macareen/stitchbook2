package com.macareen.stitchbook2.feature.library

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.ravelry.DownloadRavelryPdf
import com.macareen.stitchbook2.domain.ravelry.RavelryRequestStage
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import org.junit.Rule
import org.junit.Test

class RavelryPdfMessageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun permissionRemedyAndSafeDiagnosticAreVisible() = checkMessage(false)
    @Test fun permissionRemedyAndSafeDiagnosticAreVisibleInDarkMode() = checkMessage(true)

    private fun checkMessage(dark: Boolean) {
        val pattern = LibraryItem("pattern", "Sample cardigan", Craft.KNITTING, null, null,
            emptyList(), null, false, 0, 0)
        compose.setContent {
            StitchbookTheme(darkTheme = dark) {
                Surface(modifier = Modifier.systemBarsPadding()) {
                PatternGuidesScreen(
                    PatternGuidesUiState.Content(pattern, emptyList(), false, false,
                        canGetRavelryPdf = true,
                        ravelryPdf = RavelryPdfState(result = DownloadRavelryPdf.Result.Failed(
                            DownloadRavelryPdf.Problem.PERMISSION_DENIED, 403, RavelryRequestStage.PDF_LINK))),
                    onOpenPdf = {}, onEditGuide = {}, onCreateGuide = { _, _ -> }
                )
                }
            }
        }
        compose.onNodeWithText("Ravelry denied this request.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("HTTP 403 \u00b7 Requesting the PDF link").assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureRavelryMessage") == "true") {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            val mode = if (dark) "dark" else "light"
            val screenshot = instrumentation.uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/stitchbook-ravelry-message-$mode.png"
            )
            ParcelFileDescriptor.AutoCloseInputStream(screenshot).use { it.readBytes() }
        }
    }
}
