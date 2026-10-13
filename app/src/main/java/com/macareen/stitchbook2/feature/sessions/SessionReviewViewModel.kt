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
        val deferredId: String? = null, val deferredUntil: Long = 0)
    private val operation = MutableStateFlow(Operation())
    private val now = MutableStateFlow(clock())
    val uiState = combine(sessions.observeActiveSession(), projects.observeProjects(), preferences.preferences, now, operation) {
        active, projectList, prefs, timestamp, action ->
        val due = active?.takeIf { SessionTimeReview.needsReview(it, timestamp, prefs.timerReviewMinutes) &&
            !(action.deferredId == it.id && timestamp < action.deferredUntil) }
        SessionReviewUiState(due?.let { PendingSessionReview(it, projectList.firstOrNull { p -> p.id == it.projectId }?.name,
            it.workedMillis(timestamp) / 60_000L) }, action.busy, action.error)
    }.catch { emit(SessionReviewUiState(error = SessionReviewError.SAVE_FAILED)) }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SessionReviewUiState())

    fun checkNow() { now.value = clock() }
    fun later(expectedId: String) {
        val review = uiState.value.review ?: return
        if (operation.value.busy || review.session.id != expectedId) return
        operation.value = Operation(deferredId = review.session.id,
            deferredUntil = clock() + preferences.preferences.value.timerReviewMinutes * 60_000L)
    }
    fun keepAll(expectedId: String) = save(expectedId) { session, timestamp -> SessionTimeReview.keepAll(session, timestamp) }
    fun correctAndPause(expectedId: String, minutes: String) {
        if (operation.value.busy) return
        val value = minutes.trim().toLongOrNull()
        if (value == null || value < 0) { operation.value = Operation(error = SessionReviewError.INVALID_MINUTES); return }
        save(expectedId) { session, timestamp -> SessionTimeReview.correctAndPause(session, value, timestamp) }
    }
    private fun save(expectedId: String, transform: (CraftingSession, Long) -> CraftingSession?) {
        val expected = uiState.value.review?.session ?: return
        if (operation.value.busy) return
        if (expected.id != expectedId) { operation.value = Operation(error = SessionReviewError.TIMER_CHANGED); return }
        operation.value = Operation(busy = true)
        scope.launch {
            try {
                val latest = sessions.observeActiveSession().first()
                if (latest?.id != expected.id || latest.updatedAt != expected.updatedAt || !latest.isRunning) {
                    operation.value = Operation(error = SessionReviewError.TIMER_CHANGED)
                    return@launch
                }
                val timestamp = clock()
                val changed = transform(latest, timestamp)
                if (changed == null) { operation.value = Operation(error = SessionReviewError.INVALID_MINUTES); return@launch }
                sessions.saveSession(changed)
                now.value = timestamp
                operation.value = Operation()
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { operation.value = Operation(error = SessionReviewError.SAVE_FAILED) }
        }
    }
    companion object {
        fun factory(sessions: SessionRepository, projects: ProjectRepository, preferences: UserPreferencesRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { SessionReviewViewModel(sessions, projects, preferences) } }
    }
}
