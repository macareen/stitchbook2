package com.macareen.stitchbook2.feature.sessions

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.macareen.stitchbook2.MainActivity
import com.macareen.stitchbook2.StitchbookApplication
import com.macareen.stitchbook2.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SessionReviewIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun homeShowsOvernightTimerAndPersists10MinuteCorrection() {
        val container = (compose.activity.application as StitchbookApplication).container
        container.userPreferencesRepository.setTimerReviewMinutes(30)
        val start = System.currentTimeMillis() - 900 * 60_000L
        runBlocking {
            container.projectRepository.saveProject(Project("review-project", "Timer review sample", Craft.CROCHET,
                ProjectType.CARDIGAN, ProjectStatus.ACTIVE, null, start, start))
            container.sessionRepository.saveSession(SessionTimer.start("review-session", "review-project", start, "UTC"))
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Check your timer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Crafting minutes").performScrollTo().performTextReplacement("10")
        compose.onNodeWithText("Correct and pause").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Check your timer").fetchSemanticsNodes().isEmpty() }
        val corrected = runBlocking { container.sessionRepository.observeActiveSession().first() }!!
        assertTrue(corrected.isPaused)
        assertEquals(start, corrected.startedAt)
        assertEquals(600_000L, corrected.workedMillis(System.currentTimeMillis()))
        assertTrue(corrected.notes!!.contains("Timer correction"))
    }
}
