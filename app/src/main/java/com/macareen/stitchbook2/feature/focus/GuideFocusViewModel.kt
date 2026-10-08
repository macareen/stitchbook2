package com.macareen.stitchbook2.feature.focus

import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.time.ZoneId
import com.macareen.stitchbook2.domain.repository.SessionRepository
import com.macareen.stitchbook2.domain.model.SessionTimer
import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.repository.MaterialsRepository
import com.macareen.stitchbook2.domain.execution.OverviewEntry
import com.macareen.stitchbook2.domain.execution.GuideProgressSummary
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.execution.AncestryFrame
import com.macareen.stitchbook2.domain.execution.ExecutionAddress
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.execution.ExecutionStatus
import com.macareen.stitchbook2.domain.execution.GuideDefinition
import com.macareen.stitchbook2.domain.execution.GuideDefinitionValidator
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.GuideTraversal
import com.macareen.stitchbook2.domain.execution.Instruction
import com.macareen.stitchbook2.domain.execution.InvalidExecutionAddressException
import com.macareen.stitchbook2.domain.execution.InvalidExecutionStateException
import com.macareen.stitchbook2.domain.execution.NoChangeReason
import com.macareen.stitchbook2.domain.execution.PersistedExecution
import com.macareen.stitchbook2.domain.execution.PersistedExecutionTransitionResult
import com.macareen.stitchbook2.domain.execution.Range
import com.macareen.stitchbook2.domain.execution.Repeat
import com.macareen.stitchbook2.domain.execution.Section
import com.macareen.stitchbook2.domain.execution.ValidatedGuideDefinition
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.ExecutionVersionConflictException
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.usecase.IncrementCounterUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One line of structural context derived from an [ExecutionAddress]'s
 * ancestry frames plus the (already loaded) Guide Definition — never
 * stored, never a new form of execution state.
 */
sealed interface StructuralPosition {
    data class RangePosition(
        val unitLabel: String,
        val currentValue: Int,
        val startInclusive: Int,
        val endInclusive: Int
    ) : StructuralPosition

    data class RepeatPosition(
        val label: String?,
        val currentIteration: Int,
        val count: Int
    ) : StructuralPosition
}

/**
 * Transient, non-fatal feedback from the engine about the last transition:
 * either one of its own [NoChangeReason]s, or a persistence-layer failure
 * the UI should surface rather than silently swallow.
 */
enum class FocusFeedback {
    ALREADY_AT_FIRST_OCCURRENCE,
    ALREADY_AT_TARGET,
    ALREADY_COMPLETE,
    STALE_EXECUTION_STATE,
    INVALID_TRANSITION,
    UNKNOWN_ERROR
}

sealed interface GuideFocusUiState {
    data object Loading : GuideFocusUiState
    data object GuideNotFound : GuideFocusUiState
    data object NoPublishedRevision : GuideFocusUiState
    data object LoadError : GuideFocusUiState

    data class ReadyToStart(
        val guideName: String,
        val projectId: String,
        val isStarting: Boolean = false,
        val startFailed: Boolean = false
    ) : GuideFocusUiState

    data class InProgress(
        val guideName: String,
        val projectId: String,
        val executionId: ExecutionId,
        val version: Long,
        val instructionText: String,
        val breadcrumbs: List<String>,
        val positions: List<StructuralPosition>,
        /**
         * The owning Project's counters (PRODUCT_SPEC.md 6.3's "active
         * crafting screen": tracking counters without leaving Focus Mode).
         * Fetched once per (re)load/refresh rather than observed live, the
         * same non-reactive-snapshot style this ViewModel already uses for
         * the guide/execution state above it.
         */
        val projectCounters: List<Counter> = emptyList(),
        val jumpToFirstIncompleteTarget: ExecutionAddress? = null,
        val isBusy: Boolean = false,
        val feedback: FocusFeedback? = null,
        /** How far through the whole guide, weighted by stitches where the guide states them. */
        val progress: GuideProgressSummary? = null,
        /** The whole guide as a scannable list, for the overview. */
        val overview: List<OverviewEntry> = emptyList(),
        /** The pattern this guide follows, at the current step's page when the step names one. */
        val pattern: PatternPage? = null,
        /** The project's crafting sessions, for total time and time on this step; empty without a project. */
        val timeSessions: List<CraftingSession> = emptyList(),
        /** When the current step became current; null when time isn't tracked (no project). */
        val stepStartedAt: Long? = null,
        val timer: FocusTimer = FocusTimer.NOT_RUNNING
    ) : GuideFocusUiState

    data class Completed(
        val guideName: String,
        val projectId: String,
        val isStartingNext: Boolean = false,
        val startNextFailed: Boolean = false
    ) : GuideFocusUiState
}

/**
 * Drives one Guide's Focus Mode session.
 *
 * This ViewModel only loads persisted state, asks [GuideRepository] and
 * [ExecutionRepository] to act on it, and renders whatever they return. It
 * never applies Complete/Previous/Jump semantics itself, never decides an
 * Execution is complete except by reading [ExecutionStatus] as persisted,
 * and never computes container progress beyond formatting the ancestry
 * frames and container bounds/counts the guide definition already has.
 */
/** The project's session timer as Focus Mode sees it. Only one session runs app-wide. */
enum class FocusTimer { NOT_RUNNING, RUNNING, PAUSED, OTHER_PROJECT }

/** A pattern's PDF in the Library, and the 1-based page to open it at. */
data class PatternPage(val libraryItemId: String, val page: Int?)

class GuideFocusViewModel(
    private val guideId: GuideId,
    private val guideRepository: GuideRepository,
    private val executionRepository: ExecutionRepository,
    private val counterRepository: CounterRepository,
    externalScope: CoroutineScope? = null,
    /** The project this knitting belongs to; falls back to the guide's own project. */
    private val projectId: String? = null,
    /** Finds the project's pattern when the guide doesn't name one itself. */
    private val materialsRepository: MaterialsRepository? = null,
    /** The project's crafting sessions, which time the knitting. */
    private val sessionRepository: SessionRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** Moves a planned project to active once its knitting starts. */
    private val projectRepository: ProjectRepository? = null
) : ViewModel() {

    /** The Library pattern to open from Focus, decided once per load. */
    private var patternLibraryItemId: String? = null

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val incrementCounter = IncrementCounterUseCase(counterRepository)

    private val _uiState = MutableStateFlow<GuideFocusUiState>(GuideFocusUiState.Loading)
    val uiState: StateFlow<GuideFocusUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Re-derives UI state from whatever is currently persisted. */
    fun refresh() {
        scope.launch { loadFromRepositories() }
    }

    fun onStart() {
        val current = _uiState.value as? GuideFocusUiState.ReadyToStart ?: return
        if (current.isStarting) return
        _uiState.value = current.copy(isStarting = true, startFailed = false)
        startNewExecution(
            guideName = current.guideName,
            projectId = current.projectId,
            onFailure = {
                _uiState.value = GuideFocusUiState.ReadyToStart(
                    guideName = current.guideName,
                    projectId = current.projectId,
                    startFailed = true
                )
            }
        )
    }

    fun onStartNext() {
        val current = _uiState.value as? GuideFocusUiState.Completed ?: return
        if (current.isStartingNext) return
        _uiState.value = current.copy(isStartingNext = true, startNextFailed = false)
        startNewExecution(
            guideName = current.guideName,
            projectId = current.projectId,
            onFailure = {
                _uiState.value = GuideFocusUiState.Completed(
                    guideName = current.guideName,
                    projectId = current.projectId,
                    startNextFailed = true
                )
            }
        )
    }

    fun onIncrementCounter(counter: Counter) {
        scope.launch {
            incrementCounter(counter)
            refreshProjectCounters()
        }
    }

    fun onDecrementCounter(counter: Counter) {
        val newValue = (counter.currentValue - 1).coerceAtLeast(0)
        if (newValue == counter.currentValue) return
        scope.launch {
            try {
                counterRepository.saveCounter(counter.copy(currentValue = newValue, updatedAt = System.currentTimeMillis()))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Best-effort, same rationale as CountersViewModel's own
                // persist(): the section re-renders from persisted state
                // on the next refresh either way.
            }
            refreshProjectCounters()
        }
    }

    private suspend fun refreshProjectCounters() {
        val projectId = (_uiState.value as? GuideFocusUiState.InProgress)?.projectId ?: return
        val counters = counterRepository.observeCountersByProject(projectId).first()
        // Re-read (not the snapshot from before the suspend above): a
        // transition (Complete/Previous) could have changed other InProgress
        // fields, or moved this ViewModel out of InProgress entirely, while
        // the counters fetch was in flight.
        val latest = _uiState.value as? GuideFocusUiState.InProgress ?: return
        _uiState.value = latest.copy(projectCounters = counters)
    }

    /** Completing a step means knitting is happening: an idle timer starts and a planned project becomes active. */
    fun onComplete() {
        val projectId = (_uiState.value as? GuideFocusUiState.InProgress)?.projectId.orEmpty()
        applyTransition { executionId, version ->
            startTimerIfIdle(projectId)
            markProjectActive(projectId)
            executionRepository.applyComplete(executionId, version)
        }
    }

    /** Starts, pauses or resumes this project's timer; a timer running for another project is left alone. */
    fun onToggleTimer() {
        val current = _uiState.value as? GuideFocusUiState.InProgress ?: return
        val sessions = sessionRepository ?: return
        if (current.projectId.isEmpty()) return
        scope.launch {
            try {
                val now = clock()
                val active = sessions.observeActiveSession().first()
                when {
                    active == null -> sessions.saveSession(
                        SessionTimer.start(UUID.randomUUID().toString(), current.projectId, now, zone().id)
                    )
                    active.projectId != current.projectId -> Unit
                    active.isPaused -> sessions.saveSession(SessionTimer.resume(active, now))
                    else -> sessions.saveSession(SessionTimer.pause(active, now))
                }
                refreshTiming()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The timer is a convenience; the step and its place are unaffected.
            }
        }
    }

    private suspend fun markProjectActive(projectId: String) {
        val projects = projectRepository ?: return
        if (projectId.isEmpty()) return
        try {
            val project = projects.observeProject(projectId).first() ?: return
            if (project.status == ProjectStatus.PLANNED) {
                projects.saveProject(project.copy(status = ProjectStatus.ACTIVE, updatedAt = clock()))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The status is a convenience; starting the guide matters more.
        }
    }

    private suspend fun startTimerIfIdle(projectId: String) {
        val sessions = sessionRepository ?: return
        if (projectId.isEmpty()) return
        try {
            if (sessions.observeActiveSession().first() == null) {
                sessions.saveSession(SessionTimer.start(UUID.randomUUID().toString(), projectId, clock(), zone().id))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Never let the timer stop a step from being completed.
        }
    }

    private suspend fun refreshTiming() {
        val current = _uiState.value as? GuideFocusUiState.InProgress ?: return
        val (sessions, timer) = loadTiming(current.projectId) ?: return
        _uiState.update { state ->
            (state as? GuideFocusUiState.InProgress)?.copy(timeSessions = sessions, timer = timer) ?: state
        }
    }

    /** The project's sessions and its timer state, or null when time isn't tracked here. */
    private suspend fun loadTiming(projectId: String): Pair<List<CraftingSession>, FocusTimer>? {
        val sessions = sessionRepository ?: return null
        if (projectId.isEmpty()) return null
        val active = sessions.observeActiveSession().first()
        val timer = when {
            active == null -> FocusTimer.NOT_RUNNING
            active.projectId != projectId -> FocusTimer.OTHER_PROJECT
            active.isPaused -> FocusTimer.PAUSED
            else -> FocusTimer.RUNNING
        }
        return sessions.observeSessionsForProject(projectId).first() to timer
    }

    fun onPrevious() = applyTransition { executionId, version ->
        executionRepository.applyPrevious(executionId, version)
    }

    /** Moves to [target], a step picked from the overview. */
    fun onJumpTo(target: ExecutionAddress) {
        if (_uiState.value !is GuideFocusUiState.InProgress) return
        applyTransition { executionId, version ->
            executionRepository.applyJump(executionId, version, target)
        }
    }

    fun onJumpToFirstIncomplete() {
        val current = _uiState.value as? GuideFocusUiState.InProgress ?: return
        val target = current.jumpToFirstIncompleteTarget ?: return
        applyTransition { executionId, version ->
            executionRepository.applyJump(executionId, version, target)
        }
    }

    private fun startNewExecution(guideName: String, projectId: String, onFailure: () -> Unit) {
        scope.launch {
            try {
                val revisionId = guideRepository.getLatestRevision(guideId)?.id
                if (revisionId == null) {
                    _uiState.value = GuideFocusUiState.NoPublishedRevision
                    return@launch
                }
                val execution = executionRepository.createExecution(guideId, revisionId, projectId.ifEmpty { null })
                markProjectActive(projectId)
                val projectCounters = counterRepository.observeCountersByProject(projectId).first()
                applyExecutionResult(guideName, projectId, execution, projectCounters)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                onFailure()
            }
        }
    }

    private fun applyTransition(
        transition: suspend (ExecutionId, Long) -> PersistedExecutionTransitionResult
    ) {
        val current = _uiState.value as? GuideFocusUiState.InProgress ?: return
        if (current.isBusy) return
        _uiState.value = current.copy(isBusy = true, feedback = null)

        scope.launch {
            try {
                // A guide-execution transition never changes counters on its
                // own, so the currently displayed list carries forward
                // unchanged rather than being re-fetched on every tap.
                when (val result = transition(current.executionId, current.version)) {
                    is PersistedExecutionTransitionResult.Changed ->
                        applyExecutionResult(
                            current.guideName,
                            current.projectId,
                            result.execution,
                            current.projectCounters
                        )

                    is PersistedExecutionTransitionResult.NoChange ->
                        applyExecutionResult(
                            guideName = current.guideName,
                            projectId = current.projectId,
                            execution = result.execution,
                            projectCounters = current.projectCounters,
                            feedback = result.reason.toFocusFeedback()
                        )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: ExecutionVersionConflictException) {
                // Another transition committed first. Re-derive from what is
                // actually persisted now rather than patching stale local
                // state, so the displayed instruction/position stay accurate.
                loadFromRepositories(FocusFeedback.STALE_EXECUTION_STATE)
            } catch (_: InvalidExecutionAddressException) {
                loadFromRepositories(FocusFeedback.INVALID_TRANSITION)
            } catch (_: InvalidExecutionStateException) {
                loadFromRepositories(FocusFeedback.INVALID_TRANSITION)
            } catch (_: Exception) {
                loadFromRepositories(FocusFeedback.UNKNOWN_ERROR)
            }
        }
    }

    private suspend fun loadFromRepositories(feedback: FocusFeedback? = null) {
        try {
            val guide = guideRepository.getGuide(guideId)
            if (guide == null) {
                _uiState.value = GuideFocusUiState.GuideNotFound
                return
            }

            // A pattern guide opened on its own has no project, so no project counters.
            val contextProjectId = this.projectId ?: guide.projectId
            val projectId = contextProjectId.orEmpty()
            patternLibraryItemId = guide.libraryItemId ?: contextProjectId?.let { id ->
                // Only a project with exactly one pattern file says which pattern to open.
                materialsRepository?.observePatternsForProject(id)?.first()?.filter { it.pdfUri != null }?.singleOrNull()?.id
            }
            val active = executionRepository.getActiveExecution(guideId, contextProjectId)
            if (active != null) {
                val projectCounters = contextProjectId?.let { counterRepository.observeCountersByProject(it).first() }.orEmpty()
                applyExecutionResult(guide.name, projectId, active, projectCounters, feedback)
                return
            }

            val hasPublishedRevision = guideRepository.getLatestRevision(guideId) != null
            _uiState.value = if (hasPublishedRevision) {
                GuideFocusUiState.ReadyToStart(guideName = guide.name, projectId = projectId)
            } else {
                GuideFocusUiState.NoPublishedRevision
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _uiState.value = GuideFocusUiState.LoadError
        }
    }

    private suspend fun applyExecutionResult(
        guideName: String,
        projectId: String,
        execution: PersistedExecution,
        projectCounters: List<Counter>,
        feedback: FocusFeedback? = null
    ) {
        if (execution.state.status == ExecutionStatus.COMPLETED) {
            _uiState.value = GuideFocusUiState.Completed(guideName = guideName, projectId = projectId)
            return
        }

        val revision = guideRepository.loadRevision(execution.state.definitionRevisionId)
        if (revision == null) {
            _uiState.value = GuideFocusUiState.LoadError
            return
        }

        val timing = try {
            loadTiming(projectId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        _uiState.value = buildInProgress(guideName, projectId, projectCounters, revision.definition, execution, feedback)
            .copy(
                timeSessions = timing?.first.orEmpty(),
                stepStartedAt = timing?.let { execution.updatedAt },
                timer = timing?.second ?: FocusTimer.NOT_RUNNING
            )
    }

    private fun buildInProgress(
        guideName: String,
        projectId: String,
        projectCounters: List<Counter>,
        definition: GuideDefinition,
        execution: PersistedExecution,
        feedback: FocusFeedback?
    ): GuideFocusUiState.InProgress {
        val validated = GuideDefinitionValidator.validate(definition)
        val traversal = GuideTraversal(validated)
        val currentAddress = checkNotNull(execution.state.currentAddress) {
            "An ACTIVE execution must have a current address."
        }
        val instruction = validated.node(currentAddress.instructionNodeId) as Instruction

        val breadcrumbs = traversal.ancestryNodePath(currentAddress)
            .mapNotNull { nodeId -> (validated.node(nodeId) as? Section)?.title }

        val positions = currentAddress.ancestryFrames.mapNotNull { frame ->
            structuralPositionFor(validated, frame)
        }

        val firstIncompleteAddress = traversal.occurrences()
            .map { it.address }
            .firstOrNull { it !in execution.state.completedAddresses }
        val jumpTarget = firstIncompleteAddress?.takeIf { it != currentAddress }

        return GuideFocusUiState.InProgress(
            guideName = guideName,
            projectId = projectId,
            executionId = execution.state.executionId,
            version = execution.version,
            // The page shows on the Pattern button, so steps read without their "(p.N)".
            instructionText = withoutSourcePage(instruction.text),
            breadcrumbs = breadcrumbs,
            positions = positions,
            projectCounters = projectCounters,
            jumpToFirstIncompleteTarget = jumpTarget,
            feedback = feedback,
            progress = GuideProgressSummary.of(validated, execution.state.completedAddresses),
            overview = OverviewEntry.overviewOf(validated, execution.state.completedAddresses, currentAddress)
                .map { it.copy(text = withoutSourcePage(it.text)) },
            pattern = patternLibraryItemId?.let { id ->
                // Imported steps name their page only where it changes, so look back to the last one that does.
                val page = traversal.occurrences()
                    .takeWhile { it.address != currentAddress }
                    .plus(traversal.resolve(currentAddress))
                    .mapNotNull { occurrence ->
                        (validated.node(occurrence.address.instructionNodeId) as? Instruction)?.text
                            ?.let { SOURCE_PAGE.find(it)?.groupValues?.get(1)?.toIntOrNull() }
                    }
                    .lastOrNull()
                PatternPage(id, page)
            }
        )
    }

    private fun withoutSourcePage(text: String): String = SOURCE_PAGE.replace(text, "").trimEnd()

    private fun structuralPositionFor(
        guide: ValidatedGuideDefinition,
        frame: AncestryFrame
    ): StructuralPosition? = when (frame) {
        is AncestryFrame.RangeValue -> {
            (guide.node(frame.containerNodeId) as? Range)?.let { range ->
                StructuralPosition.RangePosition(
                    unitLabel = range.unitLabel,
                    currentValue = frame.value,
                    startInclusive = range.startInclusive,
                    endInclusive = range.endInclusive
                )
            }
        }

        is AncestryFrame.RepeatIteration -> {
            (guide.node(frame.containerNodeId) as? Repeat)?.let { repeat ->
                StructuralPosition.RepeatPosition(
                    label = repeat.label,
                    currentIteration = frame.iteration,
                    count = repeat.count
                )
            }
        }
    }

    private fun NoChangeReason.toFocusFeedback(): FocusFeedback = when (this) {
        NoChangeReason.ALREADY_COMPLETE -> FocusFeedback.ALREADY_COMPLETE
        NoChangeReason.ALREADY_AT_FIRST_OCCURRENCE -> FocusFeedback.ALREADY_AT_FIRST_OCCURRENCE
        NoChangeReason.ALREADY_AT_TARGET -> FocusFeedback.ALREADY_AT_TARGET
    }

    companion object {
        /** The "(p.N)" an imported step ends with, naming its page in the original PDF. */
        private val SOURCE_PAGE = Regex("""\(p\.(\d+)\)\s*$""")

        fun factory(
            guideId: GuideId,
            guideRepository: GuideRepository,
            executionRepository: ExecutionRepository,
            counterRepository: CounterRepository,
            projectId: String? = null,
            materialsRepository: MaterialsRepository? = null,
            sessionRepository: SessionRepository? = null,
            projectRepository: ProjectRepository? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GuideFocusViewModel(
                    guideId,
                    guideRepository,
                    executionRepository,
                    counterRepository,
                    projectId = projectId,
                    materialsRepository = materialsRepository,
                    sessionRepository = sessionRepository,
                    projectRepository = projectRepository
                )
            }
        }
    }
}
