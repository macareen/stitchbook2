package com.macareen.stitchbook2.feature.focus

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
        val feedback: FocusFeedback? = null
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
class GuideFocusViewModel(
    private val guideId: GuideId,
    private val guideRepository: GuideRepository,
    private val executionRepository: ExecutionRepository,
    private val counterRepository: CounterRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

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

    fun onComplete() = applyTransition { executionId, version ->
        executionRepository.applyComplete(executionId, version)
    }

    fun onPrevious() = applyTransition { executionId, version ->
        executionRepository.applyPrevious(executionId, version)
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
                val execution = executionRepository.createExecution(guideId, revisionId)
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

            val active = executionRepository.getActiveExecution(guideId)
            if (active != null) {
                val projectCounters = counterRepository.observeCountersByProject(guide.projectId).first()
                applyExecutionResult(guide.name, guide.projectId, active, projectCounters, feedback)
                return
            }

            val hasPublishedRevision = guideRepository.getLatestRevision(guideId) != null
            _uiState.value = if (hasPublishedRevision) {
                GuideFocusUiState.ReadyToStart(guideName = guide.name, projectId = guide.projectId)
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

        _uiState.value = buildInProgress(guideName, projectId, projectCounters, revision.definition, execution, feedback)
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
            instructionText = instruction.text,
            breadcrumbs = breadcrumbs,
            positions = positions,
            projectCounters = projectCounters,
            jumpToFirstIncompleteTarget = jumpTarget,
            feedback = feedback
        )
    }

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
        fun factory(
            guideId: GuideId,
            guideRepository: GuideRepository,
            executionRepository: ExecutionRepository,
            counterRepository: CounterRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GuideFocusViewModel(guideId, guideRepository, executionRepository, counterRepository)
            }
        }
    }
}
