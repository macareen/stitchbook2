package com.macareen.stitchbook2.feature.sessions

import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.domain.repository.*
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class ManualTimeViewModelTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z").toEpochMilli()
    private val input = ManualTimeInput("2026-10-13", "11:30", "10", "Forgot timer")
    private fun model(sessions: ManualTestSessions, scope: CoroutineScope): ProjectSessionsViewModel =
        ProjectSessionsViewModel("p", ManualTestProjects(), sessions, externalScope = scope, clock = { now }, zone = { ZoneId.of("UTC") })
            .also { vm -> scope.launch { vm.uiState.collect {} } }

    @Test fun manualEntriesNeverStopOrReplaceAnActiveTimerAndDefaultsFinishBeforeIt() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val live = SessionTimer.start("live", "other", now - 300_000, "UTC")
            val repo = ManualTestSessions(listOf(live)); val vm = model(repo, scope)
            vm.openManualEntry(); assertEquals("11:55", vm.manualEntry.value.defaults.finishedTime)
            vm.addManualTime(input)
            assertEquals(ManualEntryStatus.SAVED, vm.manualEntry.value.status)
            assertEquals(live, repo.items.value.first())
            val manual = repo.items.value.last()
            assertEquals(CraftingSessionSource.MANUAL, manual.source); assertFalse(manual.isActive)
            assertEquals(600_000L, manual.workedMillis(now)); assertEquals("p", manual.projectId)
        } finally { scope.cancel() }
    }
    @Test fun overlapNeedsConfirmationAndFailedSavesCanRetryWithoutConfirmingAgain() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val live = SessionTimer.start("live", "other", now - 3_000_000, "UTC")
            val repo = ManualTestSessions(listOf(live)); val vm = model(repo, scope)
            vm.addManualTime(input)
            assertEquals(ManualTimeError.POSSIBLE_OVERLAP, vm.manualEntry.value.error); assertEquals(1, repo.items.value.size)
            repo.failSave = true; vm.addManualTime(input.copy(confirmOverlap = true))
            assertEquals(ManualTimeError.SAVE_FAILED, vm.manualEntry.value.error)
            repo.failSave = false; vm.addManualTime(input)
            assertEquals(ManualEntryStatus.SAVED, vm.manualEntry.value.status)
            assertEquals(live, repo.items.value.first())
            assertTrue(repo.items.value.last().notes!!.contains("Potential overlap"))
        } finally { scope.cancel() }
    }
    @Test fun staleConfirmationCannotApproveAChangedIntervalAndInvalidInputNeverSaves() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = ManualTestSessions(listOf(SessionTimer.start("live", "p", now - 3_000_000, "UTC")))
            val vm = model(repo, scope)
            vm.addManualTime(input); vm.clearManualFeedback()
            vm.addManualTime(input.copy(minutes = "9", confirmOverlap = true))
            assertEquals(ManualTimeError.POSSIBLE_OVERLAP, vm.manualEntry.value.error)
            vm.addManualTime(input.copy(minutes = "0"))
            assertEquals(ManualTimeError.INVALID_DURATION, vm.manualEntry.value.error)
            assertEquals(1, repo.items.value.size)
        } finally { scope.cancel() }
    }
    @Test fun staleTimerActionCannotReopenAChangedSession() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val live = SessionTimer.start("live", "p", now - 300_000, "UTC")
            val repo = ManualTestSessions(listOf(live)); repo.rejectComparison = true
            val vm = model(repo, scope); vm.pause()
            assertEquals(listOf(live), repo.items.value)
            assertEquals(SessionFeedback.CHANGED, vm.uiState.value.feedback)
        } finally { scope.cancel() }
    }
    @Test fun oldCorrectionCannotOverwriteNewerSessionDetails() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val old = SessionTimer.stop(SessionTimer.start("s", "p", now - 300_000, "UTC"), now)
            val latest = old.copy(notes = "Newer note", updatedAt = now + 1)
            val repo = ManualTestSessions(listOf(latest)); val vm = model(repo, scope)
            vm.saveCorrection(old, SessionCorrectionInput("2026-10-13", "11:55", "10", "", "", "Old edit"))
            assertEquals(listOf(latest), repo.items.value)
            assertEquals(SessionFeedback.CHANGED, vm.uiState.value.feedback)
        } finally { scope.cancel() }
    }
    @Test fun explicitStartClosesAllPreviousScopes() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val old = SessionTimer.start("other", "other", now - 300_000, "UTC")
            val paused = SessionTimer.pause(SessionTimer.start("paused", null, now - 240_000, "UTC"), now - 60_000)
            val repo = ManualTestSessions(listOf(old, paused)); val vm = model(repo, scope)
            vm.start()
            assertEquals(1, repo.items.value.count { it.isActive })
            assertEquals("p", repo.items.value.single { it.isActive }.projectId)
            assertEquals(SessionTimer.stop(old, now), repo.items.value.single { it.id == old.id })
            assertEquals(SessionTimer.stop(paused, now), repo.items.value.single { it.id == paused.id })
            assertEquals(SessionFeedback.STOPPED_OTHER_PROJECT, vm.uiState.value.feedback)
        } finally { scope.cancel() }
    }

    @Test fun busySaveCannotBeDuplicatedOrReset() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = ManualTestSessions(emptyList()); val pending = CompletableDeferred<Unit>(); repo.beforeSave = pending
            val vm = model(repo, scope); vm.addManualTime(input); vm.clearManualFeedback(); vm.openManualEntry()
            vm.addManualTime(input.copy(minutes = "20"))
            assertEquals(ManualEntryStatus.SAVING, vm.manualEntry.value.status)
            pending.complete(Unit)
            assertEquals(ManualEntryStatus.SAVED, vm.manualEntry.value.status)
            assertEquals(600_000L, repo.items.value.single().workedMillis(now))
        } finally { scope.cancel() }
    }
}

private class ManualTestSessions(initial: List<CraftingSession>) : SessionRepository {
    val items = MutableStateFlow(initial)
    var rejectComparison = false
    override suspend fun saveIfUnchanged(expected: CraftingSession, changed: CraftingSession): Boolean =
        if (rejectComparison) false else super<SessionRepository>.saveIfUnchanged(expected, changed)
    var failSave = false
    var beforeSave: CompletableDeferred<Unit>? = null
    override fun observeSessions(): Flow<List<CraftingSession>> = items
    override fun observeSessionsForProject(projectId: String): Flow<List<CraftingSession>> = items.map { list -> list.filter { it.projectId == projectId } }
    override fun observeActiveSession(): Flow<CraftingSession?> = items.map { list -> list.firstOrNull { it.isActive } }
    override suspend fun saveSession(session: CraftingSession) { beforeSave?.await(); if (failSave) error("Save failed"); items.value = items.value.filterNot { it.id == session.id } + session }
    override suspend fun deleteSession(session: CraftingSession) { items.value = items.value.filterNot { it.id == session.id } }
}
private class ManualTestProjects : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> = flowOf(emptyList())
    override fun observeProject(id: String): Flow<Project?> = flowOf(null)
    override suspend fun saveProject(project: Project) = Unit
    override suspend fun deleteProject(project: Project) = Unit
}
