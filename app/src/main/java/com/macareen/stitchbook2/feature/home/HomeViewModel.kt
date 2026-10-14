package com.macareen.stitchbook2.feature.home

import com.macareen.stitchbook2.domain.execution.PersistedExecution
import com.macareen.stitchbook2.domain.execution.GuideProgressSummary
import com.macareen.stitchbook2.domain.execution.GuideDefinitionValidator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.repository.JournalRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val projectName: String,
    /** The project whose place to resume; one guide can be in progress in several projects. */
    val projectId: String? = null,
    /** How far through the guide, weighted by stitches as in Focus Mode; null if it can't be worked out. */
    val percentDone: Int? = null,
    val pieceId: String? = null
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Error : HomeUiState
    data class Content(
        val activeProjectCount: Int,
        val totalProjectCount: Int,
        val craftCount: Int,
        val activeProjects: List<Project>,
        val resumeGuide: ResumeGuide?,
        val currentProject: Project? = null,
        val currentPhoto: Photo? = null
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
    externalScope: CoroutineScope? = null,
    private val journalRepository: JournalRepository? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    val uiState = combine<List<Project>, List<Photo>, Unit, HomeUiState>(
        projectRepository.observeProjects(),
        journalRepository?.observePhotos()?.map { photos -> photos.filter { it.projectId != null } }?.distinctUntilChanged()
            ?: flowOf(emptyList()),
        executionRepository.observeWorkChanges()
    ) { projects, photos, _ -> buildContent(projects, photos) }
        .catch { emit(HomeUiState.Error) }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading
        )

    private suspend fun percentDone(execution: PersistedExecution): Int? = try {
        guideRepository.loadRevision(execution.state.definitionRevisionId)?.let { revision ->
            GuideProgressSummary.of(GuideDefinitionValidator.validate(revision.definition), execution.state.completedAddresses).percent
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun buildContent(projects: List<Project>, photos: List<Photo>): HomeUiState.Content {
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
                    executionRepository.getActiveProjectWork(GuideId(guide.id.value), project.id)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (activeExecution != null && activeExecution.updatedAt > resumeUpdatedAt) {
                    resumeUpdatedAt = activeExecution.updatedAt
                    resumeGuide = ResumeGuide(
                        guideId = guide.id.value,
                        projectId = project.id,
                        guideName = activeExecution.pieceId?.let { executionRepository.loadPiece(it)?.name }?.let { "${guide.name} · $it" } ?: guide.name,
                        projectName = project.name,
                        percentDone = percentDone(activeExecution),
                        pieceId = activeExecution.pieceId
                    )
                }
            }
        }

        val current = dashboardProject(activeProjects, resumeGuide?.projectId)
        return HomeUiState.Content(
            activeProjectCount = activeProjects.size,
            totalProjectCount = projects.size,
            craftCount = projects.map { it.craft }.toSet().size,
            activeProjects = activeProjects,
            resumeGuide = resumeGuide,
            currentProject = current,
            currentPhoto = latestDashboardPhoto(current?.id, photos, current?.coverPhotoId)
        )
    }

    companion object {
        fun factory(
            projectRepository: ProjectRepository,
            guideRepository: GuideRepository,
            executionRepository: ExecutionRepository,
            journalRepository: JournalRepository? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(projectRepository, guideRepository, executionRepository, journalRepository = journalRepository)
            }
        }
    }
}

/** Resume wins; otherwise show the most recently updated active project. */
internal fun dashboardProject(activeProjects: List<Project>, resumeProjectId: String?): Project? =
    activeProjects.firstOrNull { it.id == resumeProjectId }
        ?: activeProjects.maxWithOrNull(compareBy<Project> { it.updatedAt }.thenBy { it.id })

internal fun latestDashboardPhoto(projectId: String?, photos: List<Photo>, coverPhotoId: String? = null): Photo? =
    photos.firstOrNull { projectId != null && it.projectId == projectId && it.id == coverPhotoId } ?:
    if (projectId == null) null else photos.filter { it.projectId == projectId }
        .maxWithOrNull(compareBy<Photo> { it.createdAt }.thenBy { it.id })
