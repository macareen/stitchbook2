package com.macareen.stitchbook2.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.repository.MaterialsRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.SessionRepository
import com.macareen.stitchbook2.domain.repository.StashRepository
import com.macareen.stitchbook2.domain.statistics.CraftingStatistics
import com.macareen.stitchbook2.domain.statistics.StatisticsRange
import com.macareen.stitchbook2.domain.statistics.computeStatistics
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface StatisticsUiState {
    data object Loading : StatisticsUiState
    data object Error : StatisticsUiState
    data class Content(
        val range: StatisticsRange,
        val statistics: CraftingStatistics,
        val projectNames: Map<String, String>
    ) : StatisticsUiState
}

class StatisticsViewModel(
    projectRepository: ProjectRepository,
    sessionRepository: SessionRepository,
    materialsRepository: MaterialsRepository,
    stashRepository: StashRepository,
    externalScope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val range = MutableStateFlow(StatisticsRange.LAST_30_DAYS)

    val uiState: StateFlow<StatisticsUiState> = combine(
        sessionRepository.observeSessions(),
        projectRepository.observeProjects(),
        materialsRepository.observeAllocations(),
        stashRepository.observeStashItems(),
        range
    ) { sessions, projects, allocations, stash, selectedRange ->
        val now = clock()
        StatisticsUiState.Content(
            range = selectedRange,
            statistics = computeStatistics(
                sessions = sessions,
                projects = projects,
                allocations = allocations,
                stashItems = stash,
                range = selectedRange,
                today = LocalDate.now(zone()),
                now = now
            ),
            projectNames = projects.associate { it.id to it.name }
        ) as StatisticsUiState
    }
        .catch { emit(StatisticsUiState.Error) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState.Loading)

    fun selectRange(value: StatisticsRange) {
        range.value = value
    }

    companion object {
        fun factory(
            projectRepository: ProjectRepository,
            sessionRepository: SessionRepository,
            materialsRepository: MaterialsRepository,
            stashRepository: StashRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StatisticsViewModel(projectRepository, sessionRepository, materialsRepository, stashRepository)
            }
        }
    }
}
