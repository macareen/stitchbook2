package com.macareen.stitchbook2.feature.counters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.normalizedCounterName
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CounterFilterState(val searchQuery: String = "")

/** A [Counter] paired with its owning Project's name, resolved once here so the screen never looks it up itself. */
data class CounterListEntry(val counter: Counter, val projectName: String?)

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
    val projectId: String?
)

class CountersViewModel(
    private val repository: CounterRepository,
    private val projectRepository: ProjectRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val filterState = MutableStateFlow(CounterFilterState())

    val uiState = combine(
        repository.observeCounters(),
        projectRepository.observeProjects(),
        filterState
    ) { counters, projects, filter ->
        val projectNameById = projects.associate { it.id to it.name }
        val entries = counters
            .filter { matchesFilter(it, filter) }
            .map { CounterListEntry(it, it.projectId?.let(projectNameById::get)) }
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

    fun updateSearchQuery(value: String) {
        filterState.value = filterState.value.copy(searchQuery = value)
    }

    fun saveCounter(original: Counter?, form: CounterFormInput) {
        val normalizedName = normalizedCounterName(form.name) ?: return
        val normalizedUnitLabel = form.unitLabel.trim().ifEmpty { return }
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
                updatedAt = now
            )
            persist(counter)
        }
    }

    fun increment(counter: Counter) {
        persistValue(counter, counter.currentValue + 1)
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
            projectRepository: ProjectRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CountersViewModel(repository, projectRepository)
            }
        }
    }
}
