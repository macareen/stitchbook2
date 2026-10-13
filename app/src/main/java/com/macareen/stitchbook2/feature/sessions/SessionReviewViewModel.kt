package com.macareen.stitchbook2.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.model.SessionTimeReview
import com.macareen.stitchbook2.domain.preferences.UserPreferencesRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SessionReviewError { INVALID_MINUTES, SAVE_FAILED, TIMER_CHANGED }
data class PendingSessionReview(val session: CraftingSession, val projectName: String?, val workedMinutes: Long)
data class SessionReviewUiState(val review: PendingSessionReview? = null, val busy: Boolean = false, val error: SessionReviewError? = null)

class SessionReviewViewModel(
    private val sessions: SessionRepository,
    private val projects: ProjectRepository,
    private val preferences: UserPreferencesRepository,
    externalScope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val scope = externalScope ?: viewModelScope
    private data class Operation(val busy: Boolean = false, val error: SessionReviewError? = null,
        val deferred: Map<String, Long> = emptyMap())
    private val operation = MutableStateFlow(Operation())
    private val now = MutableStateFlow(clock())
    val uiState = combine(sessions.observeSessions(), projects.observeProjects(), preferences.preferences, now, operation) {
        recorded, projectList, prefs, timestamp, action ->
        val due = recorded.filter { SessionTimeReview.needsReview(it, timestamp, prefs.timerReviewMinutes) &&
            timestamp >= (action.deferred[it.id] ?: Long.MIN_VALUE) }
            .minWithOrNull(compareBy<CraftingSession> { it.startedAt }.thenBy { it.id })
        SessionReviewUiState(due?.let { PendingSessionReview(it, projectList.firstOrNull { p -> p.id == it.projectId }?.name,
            it.workedMillis(timestamp) / 60_000L) }, action.busy, action.error)
    }.catch { emit(SessionReviewUiState(error = SessionReviewError.SAVE_FAILED)) }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SessionReviewUiState())

    fun checkNow() { now.value = clock() }
    fun later(expectedId: String) {
        val review = uiState.value.review ?: return
        if (operation.value.busy || review.session.id != expectedId) return
        operation.value = operation.value.copy(error = null, deferred = operation.value.deferred +
            (review.session.id to (clock() + preferences.preferences.value.timerReviewMinutes * 60_000L)))
    }
    fun keepAll(expectedId: String) = save(expectedId) { session, timestamp -> SessionTimeReview.keepAll(session, timestamp) }
    fun correctAndPause(expectedId: String, minutes: String) {
        if (operation.value.busy) return
        val value = minutes.trim().toLongOrNull()
        if (value == null || value < 0) { operation.value = operation.value.copy(error = SessionReviewError.INVALID_MINUTES); return }
        save(expectedId) { session, timestamp -> SessionTimeReview.correctAndPause(session, value, timestamp) }
    }
    private fun save(expectedId: String, transform: (CraftingSession, Long) -> CraftingSession?) {
        val expected = uiState.value.review?.session ?: return
        if (operation.value.busy) return
        if (expected.id != expectedId) { operation.value = operation.value.copy(busy = false, error = SessionReviewError.TIMER_CHANGED); return }
        operation.value = operation.value.copy(busy = true, error = null)
        scope.launch {
            try {
                val latest = sessions.observeSessions().first().firstOrNull { it.id == expected.id }
                if (latest?.id != expected.id || latest != expected || !latest.isRunning) {
                    operation.value = operation.value.copy(busy = false, error = SessionReviewError.TIMER_CHANGED)
                    return@launch
                }
                val timestamp = clock()
                val changed = transform(latest, timestamp)
                if (changed == null) { operation.value = operation.value.copy(busy = false, error = SessionReviewError.INVALID_MINUTES); return@launch }
                if (!sessions.saveIfUnchanged(latest, changed)) {
                    operation.value = operation.value.copy(busy = false, error = SessionReviewError.TIMER_CHANGED)
                    return@launch
                }
                now.value = timestamp
                operation.value = operation.value.copy(busy = false, error = null, deferred = operation.value.deferred - expected.id)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { operation.value = operation.value.copy(busy = false, error = SessionReviewError.SAVE_FAILED) }
        }
    }
    companion object {
        fun factory(sessions: SessionRepository, projects: ProjectRepository, preferences: UserPreferencesRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { SessionReviewViewModel(sessions, projects, preferences) } }
    }
}
