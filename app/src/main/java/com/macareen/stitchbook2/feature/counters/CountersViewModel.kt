package com.macareen.stitchbook2.feature.counters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.model.CounterNote
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.normalizedCounterName
import com.macareen.stitchbook2.domain.model.wouldCreateCycle
import com.macareen.stitchbook2.domain.repository.CounterNoteRepository
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CounterFilterState(val searchQuery: String = "")

/**
 * A [Counter] paired with its owning Project's name and, if it has a link,
 * the target counter's name -- both resolved once here so the screen never
 * looks them up itself.
 */
data class CounterListEntry(val counter: Counter, val projectName: String?, val linkedCounterName: String?)

sealed interface CountersUiState {
    data object Loading : CountersUiState
    data object Error : CountersUiState
    data class Content(
        val entries: List<CounterListEntry>,
        val filter: CounterFilterState,
        val projects: List<Project>,
        val hasAnyCounters: Boolean
    ) : CountersUiState
}

/** Raw, unvalidated form field values from the add/edit dialog. [CountersViewModel.saveCounter] owns parsing. */
data class CounterFormInput(
    val name: String,
    val unitLabel: String,
    val goalText: String,
    val projectId: String?,
    val linkedCounterId: String? = null,
    val linkIntervalText: String = "",
    val linkAmountText: String = ""
)

/** State for the value-specific-notes dialog opened for one counter at a time; see [CountersViewModel.openNotes]. */
sealed interface CounterNotesUiState {
    data object Closed : CounterNotesUiState
    data class Content(val counter: Counter, val notes: List<CounterNote>) : CounterNotesUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class CountersViewModel(
    private val repository: CounterRepository,
    private val projectRepository: ProjectRepository,
    private val noteRepository: CounterNoteRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val filterState = MutableStateFlow(CounterFilterState())
    private val notesTarget = MutableStateFlow<Counter?>(null)

    val uiState = combine(
        repository.observeCounters(),
        projectRepository.observeProjects(),
        filterState
    ) { counters, projects, filter ->
        val projectNameById = projects.associate { it.id to it.name }
        val counterNameById = counters.associate { it.id to it.name }
        val entries = counters
            .filter { matchesFilter(it, filter) }
            .map { counter ->
                CounterListEntry(
                    counter = counter,
                    projectName = counter.projectId?.let(projectNameById::get),
                    linkedCounterName = counter.linkedCounterId?.let(counterNameById::get)
                )
            }
        CountersUiState.Content(
            entries = entries,
            filter = filter,
            projects = projects,
            hasAnyCounters = counters.isNotEmpty()
        ) as CountersUiState
    }
        .catch { emit(CountersUiState.Error) }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CountersUiState.Loading
        )

    val notesUiState = notesTarget
        .flatMapLatest { counter ->
            if (counter == null) {
                flowOf(CounterNotesUiState.Closed)
            } else {
                noteRepository.observeNotesByCounter(counter.id).map { notes ->
                    CounterNotesUiState.Content(counter, notes) as CounterNotesUiState
                }
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CounterNotesUiState.Closed
        )

    fun updateSearchQuery(value: String) {
        filterState.value = filterState.value.copy(searchQuery = value)
    }

    fun openNotes(counter: Counter) {
        notesTarget.value = counter
    }

    fun closeNotes() {
        notesTarget.value = null
    }

    fun addNote(counter: Counter, value: Int, noteText: String) {
        val trimmed = noteText.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            try {
                noteRepository.saveNote(
                    CounterNote(
                        id = UUID.randomUUID().toString(),
                        counterId = counter.id,
                        value = value,
                        note = trimmed,
                        createdAt = System.currentTimeMillis()
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Best-effort, same as saveCounter/persist below: the notes
                // list re-renders from persisted state on the next emission.
            }
        }
    }

    fun deleteNote(note: CounterNote) {
        scope.launch {
            try {
                noteRepository.deleteNote(note)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Nothing to reconcile locally, same rationale as deleteCounter.
            }
        }
    }

    fun saveCounter(original: Counter?, form: CounterFormInput) {
        val normalizedName = normalizedCounterName(form.name) ?: return
        val normalizedUnitLabel = form.unitLabel.trim().ifEmpty { return }
        val currentCounters = (uiState.value as? CountersUiState.Content)?.entries?.map { it.counter }.orEmpty()
        val link = validatedLink(original?.id, form, currentCounters)
        scope.launch {
            val now = System.currentTimeMillis()
            val counter = Counter(
                id = original?.id ?: UUID.randomUUID().toString(),
                projectId = form.projectId,
                name = normalizedName,
                unitLabel = normalizedUnitLabel,
                currentValue = original?.currentValue ?: 0,
                goal = form.goalText.toIntOrNull()?.takeIf { it > 0 },
                createdAt = original?.createdAt ?: now,
                updatedAt = now,
                linkedCounterId = link?.targetId,
                linkIncrementInterval = link?.interval,
                linkIncrementAmount = link?.amount
            )
            persist(counter)
        }
    }

    /**
     * Parses and validates [form]'s link fields into a [Link], or null if
     * there's no link, the interval/amount don't parse to positive
     * integers, or [wouldCreateCycle] rejects the target. This is a
     * defensive backstop -- [CountersScreen]'s dialog is expected to reject
     * an invalid/cyclic link before ever calling [saveCounter], the same
     * division of labor as the existing blank-name/blank-unit-label checks.
     */
    private fun validatedLink(
        editingCounterId: String?,
        form: CounterFormInput,
        currentCounters: List<Counter>
    ): Link? {
        val targetId = form.linkedCounterId ?: return null
        val interval = form.linkIntervalText.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val amount = form.linkAmountText.toIntOrNull()?.takeIf { it > 0 } ?: return null
        if (wouldCreateCycle(currentCounters, editingCounterId, targetId)) return null
        return Link(targetId, interval, amount)
    }

    private data class Link(val targetId: String, val interval: Int, val amount: Int)

    fun increment(counter: Counter) {
        val newValue = counter.currentValue + 1
        persistValue(counter, newValue)
        triggerLinkIfDue(counter, newValue)
    }

    /**
     * Every [Counter.linkIncrementInterval] increments of [counter], bumps
     * [Counter.linkedCounterId] by [Counter.linkIncrementAmount] (PRODUCT_SPEC.md
     * 6.3, "Linked behavior between counters"). Only forward increments
     * trigger this -- decrementing or resetting [counter] never does.
     */
    private fun triggerLinkIfDue(counter: Counter, newValue: Int) {
        val targetId = counter.linkedCounterId ?: return
        val interval = counter.linkIncrementInterval ?: return
        val amount = counter.linkIncrementAmount ?: return
        if (interval <= 0 || newValue % interval != 0) return
        scope.launch {
            try {
                // An atomic SQL increment, not a read-then-write: two
                // counters linked to the same target (or two rapid-fire
                // triggers) can never clobber each other's update.
                repository.incrementCounterValue(targetId, amount, System.currentTimeMillis())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Best-effort, same rationale as persist(): the list
                // re-renders from persisted state on the next emission.
            }
        }
    }

    fun decrement(counter: Counter) {
        persistValue(counter, (counter.currentValue - 1).coerceAtLeast(0))
    }

    fun reset(counter: Counter) {
        persistValue(counter, 0)
    }

    fun deleteCounter(counter: Counter) {
        scope.launch {
            try {
                repository.deleteCounter(counter)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Nothing to reconcile locally: the list reflects whatever is
                // actually persisted on the next emission.
            }
        }
    }

    private fun persistValue(counter: Counter, newValue: Int) {
        if (newValue == counter.currentValue) return
        scope.launch {
            persist(counter.copy(currentValue = newValue, updatedAt = System.currentTimeMillis()))
        }
    }

    private suspend fun persist(counter: Counter) {
        try {
            repository.saveCounter(counter)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Best-effort: the list re-renders from persisted state on the
            // next emission either way, so there is nothing else to do here.
        }
    }

    private fun matchesFilter(counter: Counter, filter: CounterFilterState): Boolean {
        val query = filter.searchQuery.trim()
        if (query.isEmpty()) return true
        return counter.name.contains(query, ignoreCase = true) ||
            counter.unitLabel.contains(query, ignoreCase = true)
    }

    companion object {
        fun factory(
            repository: CounterRepository,
            projectRepository: ProjectRepository,
            noteRepository: CounterNoteRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CountersViewModel(repository, projectRepository, noteRepository)
            }
        }
    }
}
