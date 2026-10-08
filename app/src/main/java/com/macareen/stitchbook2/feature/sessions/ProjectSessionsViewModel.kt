package com.macareen.stitchbook2.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.model.SessionTimer
import com.macareen.stitchbook2.domain.model.parseLocalDateOrNull
import com.macareen.stitchbook2.domain.model.safeZone
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.SessionRepository
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SessionFeedback {
    INVALID_DATE,
    INVALID_TIME,
    INVALID_DURATION,
    INVALID_NUMBER,
    STOPPED_OTHER_PROJECT,
    SAVE_FAILED
}

data class ProjectSessionsUiState(
    val isLoading: Boolean = true,
    val projectName: String = "",
    /** The one active session app-wide, if any. */
    val activeSession: CraftingSession? = null,
    val activeProjectName: String? = null,
    val sessions: List<CraftingSession> = emptyList(),
    val feedback: SessionFeedback? = null
) {
    val activeIsThisProject: Boolean get() = activeSession != null && sessions.any { it.id == activeSession.id }
}

/** Raw text from the correction dialog; parsed and validated by the ViewModel. */
data class SessionCorrectionInput(
    val date: String,
    val startTime: String,
    val workedMinutes: String,
    val rowsCompleted: String,
    val stitchesPerRow: String,
    val notes: String
)

class ProjectSessionsViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    externalScope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val feedback = MutableStateFlow<SessionFeedback?>(null)

    val uiState: StateFlow<ProjectSessionsUiState> = combine(
        projectRepository.observeProjects(),
        sessionRepository.observeSessionsForProject(projectId),
        sessionRepository.observeActiveSession(),
        feedback
    ) { projects, sessions, active, message ->
        val names = projects.associate { it.id to it.name }
        ProjectSessionsUiState(
            isLoading = false,
            projectName = names[projectId].orEmpty(),
            activeSession = active,
            activeProjectName = active?.projectId?.let { names[it] },
            sessions = sessions,
            feedback = message
        )
    }
        .catch { emit(ProjectSessionsUiState(isLoading = false, feedback = SessionFeedback.SAVE_FAILED)) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ProjectSessionsUiState())

    /** Only one session runs at a time; starting here stops any other one first. */
    fun start() = launchSafely {
        val now = clock()
        sessionRepository.observeActiveSession().first()?.let { other ->
            sessionRepository.saveSession(SessionTimer.stop(other, now))
            if (other.projectId != projectId) feedback.value = SessionFeedback.STOPPED_OTHER_PROJECT
        }
        sessionRepository.saveSession(SessionTimer.start(UUID.randomUUID().toString(), projectId, now, zone().id))
    }

    fun pause() = updateActive { SessionTimer.pause(it, clock()) }

    fun resume() = updateActive { SessionTimer.resume(it, clock()) }

    fun stop() = updateActive { SessionTimer.stop(it, clock()) }

    fun saveCorrection(session: CraftingSession, input: SessionCorrectionInput) {
        val date = parseLocalDateOrNull(input.date) ?: return fail(SessionFeedback.INVALID_DATE)
        val time = try {
            LocalTime.parse(input.startTime.trim())
        } catch (_: DateTimeParseException) {
            return fail(SessionFeedback.INVALID_TIME)
        }
        val minutes = input.workedMinutes.trim().toLongOrNull()
        if (minutes == null || minutes < 0 || minutes > MAX_SESSION_MINUTES) return fail(SessionFeedback.INVALID_DURATION)
        val rows = optionalCount(input.rowsCompleted) ?: return fail(SessionFeedback.INVALID_NUMBER)
        val stitches = optionalCount(input.stitchesPerRow) ?: return fail(SessionFeedback.INVALID_NUMBER)

        launchSafely {
            val now = clock()
            val startedAt = LocalDateTime.of(date, time).atZone(safeZone(session.zoneId)).toInstant().toEpochMilli()
            // An active session keeps running; only its start moves and its details change.
            val corrected = if (session.isActive) {
                session.copy(startedAt = startedAt, updatedAt = now)
            } else {
                SessionTimer.correct(session, startedAt, minutes * 60_000L, now)
            }
            sessionRepository.saveSession(
                corrected.copy(
                    rowsCompleted = rows.value,
                    stitchesPerRow = stitches.value,
                    notes = input.notes.trim().ifEmpty { null }
                )
            )
        }
    }

    fun delete(session: CraftingSession) = launchSafely { sessionRepository.deleteSession(session) }

    fun dismissFeedback() {
        feedback.value = null
    }

    private fun updateActive(transform: (CraftingSession) -> CraftingSession) = launchSafely {
        val active = sessionRepository.observeActiveSession().first() ?: return@launchSafely
        sessionRepository.saveSession(transform(active))
    }

    private fun fail(message: SessionFeedback) {
        feedback.value = message
    }

    /** Wraps so "blank" (null value) and "invalid" (null wrapper) stay distinguishable. */
    private data class OptionalCount(val value: Int?)

    private fun optionalCount(text: String): OptionalCount? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return OptionalCount(null)
        val value = trimmed.toIntOrNull() ?: return null
        return if (value < 0) null else OptionalCount(value)
    }

    private fun launchSafely(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                feedback.value = SessionFeedback.SAVE_FAILED
            }
        }
    }

    companion object {
        private const val MAX_SESSION_MINUTES = 24L * 60L

        fun factory(
            projectId: String,
            projectRepository: ProjectRepository,
            sessionRepository: SessionRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProjectSessionsViewModel(projectId, projectRepository, sessionRepository)
            }
        }
    }
}
