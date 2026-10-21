package com.macareen.stitchbook2.feature.sessions

import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.domain.preferences.*
import com.macareen.stitchbook2.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class SessionReviewViewModelTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var now = 900 * 60_000L
    private val store = ReviewSessions()
    private val preferences = ReviewPreferences()
    private fun vm() = SessionReviewViewModel(store, ReviewProjects(), preferences, scope, { now }).also { model ->
        scope.launch { model.uiState.collect {} }
    }
    @Test fun restoredOvernightSessionCanBeCorrectedTo10Minutes() {
        val model = vm()
        assertEquals(900L, model.uiState.value.review!!.workedMinutes)
        assertEquals("My cardigan", model.uiState.value.review!!.projectName)
        model.correctAndPause("s", "10")
        assertEquals(600_000L, store.active.value!!.workedMillis(now))
        assertTrue(store.active.value!!.isPaused)
        assertNull(model.uiState.value.review)
    }
    @Test fun keepAllPersistsCheckpointAndAsksAfterTheNextInterval() {
        val model = vm()
        model.keepAll("s")
        assertNull(model.uiState.value.review)
        assertEquals(now, store.active.value!!.updatedAt)
        assertEquals(now, store.active.value!!.workedMillis(now))
        now += 31 * 60_000
        model.checkNow()
        assertNotNull(model.uiState.value.review)
    }
    @Test fun invalidInputAndSaveFailureKeepOriginalTime() {
        val model = vm()
        model.correctAndPause("s", "999999999999999999999")
        assertEquals(SessionReviewError.INVALID_MINUTES, model.uiState.value.error)
        store.fail = true
        model.correctAndPause("s", "10")
        assertEquals(SessionReviewError.SAVE_FAILED, model.uiState.value.error)
        assertEquals(now, store.active.value!!.workedMillis(now))
    }
    @Test fun staleDialogCannotChangeAnotherProjectTimer() {
        val model = vm()
        store.active.value = SessionTimer.start("other", "other-project", 0, "UTC")
        model.correctAndPause("s", "10")
        assertEquals(SessionReviewError.TIMER_CHANGED, model.uiState.value.error)
        assertEquals("other", store.active.value!!.id)
        assertEquals(now, store.active.value!!.workedMillis(now))
    }
    @Test fun laterDefersWithoutDiscardingTime() {
        val model = vm()
        model.later("s")
        assertNull(model.uiState.value.review)
        assertEquals(now, store.active.value!!.workedMillis(now))
        now += 31 * 60_000
        model.checkNow()
        assertNotNull(model.uiState.value.review)
    }
    @Test fun configurationAndPausesAreApplied() {
        preferences.setTimerReviewMinutes(1000)
        val model = vm()
        assertNull(model.uiState.value.review)
        preferences.setTimerReviewMinutes(30)
        assertNotNull(model.uiState.value.review)
        store.active.value = SessionTimer.pause(store.active.value!!, now)
        assertNull(model.uiState.value.review)
    }
    @Test fun olderRecoveredTimerIsReviewedWithoutChangingNewerTimer() {
        store.active.value = store.active.value!!.copy(updatedAt = now)
        val newer = store.active.value
        store.extra.value = listOf(SessionTimer.start("old", "other-project", -60_000, "UTC"))
        val model = vm()
        assertEquals("old", model.uiState.value.review!!.session.id)
        model.correctAndPause("old", "10")
        assertEquals(newer, store.active.value)
        assertEquals(600_000L, store.extra.value.single().workedMillis(now))
        assertTrue(store.extra.value.single().isPaused)
        assertNull(model.uiState.value.review)
    }
    @Test fun restoredTimersHaveIndependentDeferrals() {
        store.extra.value = listOf(SessionTimer.start("first", null, -120_000, "UTC"), SessionTimer.start("second", null, -60_000, "UTC"))
        val model = vm()
        model.later("first"); assertEquals("second", model.uiState.value.review!!.session.id)
        model.later("second"); assertEquals("s", model.uiState.value.review!!.session.id)
        model.later("s"); assertNull(model.uiState.value.review)
        now += 31 * 60_000; model.checkNow()
        assertEquals("first", model.uiState.value.review!!.session.id)
    }
    @Test fun rejectedAtomicReviewSaveLeavesOriginalRecordAndReportsTimerChange() {
        store.rejectComparison = true
        val model = vm()
        val original = store.active.value
        model.correctAndPause("s", "10")
        assertEquals(original, store.active.value)
        assertEquals(SessionReviewError.TIMER_CHANGED, model.uiState.value.error)
        assertTrue(!model.uiState.value.busy)
    }

    @Test fun invalidClicksCannotUnlockAnInFlightSave() {
        val model = vm()
        val gate = CompletableDeferred<Unit>()
        store.gate = gate
        model.keepAll("s")
        assertTrue(model.uiState.value.busy)
        model.correctAndPause("s", "bad")
        assertTrue(model.uiState.value.busy)
        gate.complete(Unit)
        assertNull(model.uiState.value.review)
    }
}
private class ReviewSessions : SessionRepository {
    val active = MutableStateFlow<CraftingSession?>(SessionTimer.start("s", "p", 0, "UTC"))
    val extra = MutableStateFlow<List<CraftingSession>>(emptyList())
    var rejectComparison = false
    var fail = false
    var gate: CompletableDeferred<Unit>? = null
    override fun observeActiveSession(): Flow<CraftingSession?> = active
    override fun observeSessions(): Flow<List<CraftingSession>> = combine(active, extra) { current, more -> listOfNotNull(current) + more }
    override fun observeSessionsForProject(projectId: String): Flow<List<CraftingSession>> = observeSessions().map { it.filter { s -> s.projectId == projectId } }
    override suspend fun saveIfUnchanged(expected: CraftingSession, changed: CraftingSession): Boolean =
        if (rejectComparison) false else super<SessionRepository>.saveIfUnchanged(expected, changed)
    override suspend fun saveSession(session: CraftingSession) {
        gate?.await(); if (fail) error("save failed")
        if (session.id == active.value?.id) active.value = session else extra.value = extra.value.map { if (it.id == session.id) session else it }
    }
    override suspend fun deleteSession(session: CraftingSession) = Unit
}
private class ReviewProjects : ProjectRepository {
    private val project = Project("p", "My cardigan", Craft.KNITTING, ProjectType.CARDIGAN, ProjectStatus.ACTIVE, null, 0, 0)
    override fun observeProjects(): Flow<List<Project>> = flowOf(listOf(project))
    override fun observeProject(id: String): Flow<Project?> = flowOf(project.takeIf { it.id == id })
    override suspend fun saveProject(project: Project) = Unit
    override suspend fun deleteProject(project: Project) = Unit
}
private class ReviewPreferences : UserPreferencesRepository {
    override val preferences = MutableStateFlow(UserPreferences())
    override fun setTimerReviewMinutes(minutes: Int) { preferences.value = preferences.value.copy(timerReviewMinutes = minutes) }
    override fun setNavigationStyle(style: BottomNavigationStyle) = Unit
    override fun setThemeMode(mode: ThemeMode) = Unit
    override fun setMeasurementSystem(system: MeasurementSystem) = Unit
    override fun setToolSizeDisplay(display: com.macareen.stitchbook2.domain.preferences.ToolSizeDisplay) = Unit
}
