package com.macareen.stitchbook2.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ResumeGuide(
    val guideId: String,
    val guideName: String,
    val projectName: String
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Error : HomeUiState
    data class Content(
        val activeProjectCount: Int,
        val totalProjectCount: Int,
        val craftCount: Int,
        val activeProjects: List<Project>,
        val resumeGuide: ResumeGuide?
    ) : HomeUiState
}

/**
 * Aggregates state across Projects, Guides, and Executions for the Home
 * dashboard. This cross-repository derivation belongs here, not in the
 * composable: [resumeGuide] in particular mirrors the one-shot suspend-call
 * pattern [com.macareen.stitchbook2.feature.projects.ProjectDetailViewModel]
 * uses to resolve a Guide's entry action, rather than composing a dynamic
 * per-guide Flow graph.
 */
class HomeViewModel(
    private val projectRepository: ProjectRepository,
    private val guideRepository: GuideRepository,
    private val executionRepository: ExecutionRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    val uiState = projectRepository.observeProjects()
        .map<List<Project>, HomeUiState> { projects -> buildContent(projects) }
        .catch { emit(HomeUiState.Error) }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading
        )

    private suspend fun buildContent(projects: List<Project>): HomeUiState.Content {
        val activeProjects = projects.filter { it.status == ProjectStatus.ACTIVE }

        var resumeGuide: ResumeGuide? = null
        var resumeUpdatedAt = Long.MIN_VALUE
        for (project in activeProjects) {
            val guides = try {
                guideRepository.observeGuides(project.id).first()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            for (guide in guides) {
                val activeExecution = try {
                    executionRepository.getActiveExecution(GuideId(guide.id.value))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (activeExecution != null && activeExecution.updatedAt > resumeUpdatedAt) {
                    resumeUpdatedAt = activeExecution.updatedAt
                    resumeGuide = ResumeGuide(
                        guideId = guide.id.value,
                        guideName = guide.name,
                        projectName = project.name
                    )
                }
            }
        }

        return HomeUiState.Content(
            activeProjectCount = activeProjects.size,
            totalProjectCount = projects.size,
            craftCount = projects.map { it.craft }.toSet().size,
            activeProjects = activeProjects,
            resumeGuide = resumeGuide
        )
    }

    companion object {
        fun factory(
            projectRepository: ProjectRepository,
            guideRepository: GuideRepository,
            executionRepository: ExecutionRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(projectRepository, guideRepository, executionRepository)
            }
        }
    }
}
