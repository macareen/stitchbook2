package com.macareen.stitchbook2.feature.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.cards.CardContent
import com.macareen.stitchbook2.domain.cards.CardField
import com.macareen.stitchbook2.domain.cards.CardSource
import com.macareen.stitchbook2.domain.cards.CardTemplate
import com.macareen.stitchbook2.domain.cards.buildCardContent
import com.macareen.stitchbook2.domain.cards.defaultPhotoId
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.repository.JournalRepository
import com.macareen.stitchbook2.domain.repository.MaterialsRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.SessionRepository
import com.macareen.stitchbook2.domain.repository.StashRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

private data class CardSelection(
    val template: CardTemplate,
    val fields: Set<CardField>,
    /** Null until the user picks; the template's default photo is used meanwhile. */
    val photoId: String?
)

data class ShareCardUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val templates: List<CardTemplate> = emptyList(),
    val template: CardTemplate = CardTemplate.PROGRESS,
    val availableFields: List<CardField> = emptyList(),
    val fields: Set<CardField> = emptySet(),
    val photos: List<Photo> = emptyList(),
    val selectedPhotoId: String? = null,
    val content: CardContent? = null
)

/**
 * [projectId] null means a crafting summary (weekly/annual) rather than a
 * project card. Building the content is pure; nothing here touches files.
 */
class ShareCardViewModel(
    private val projectId: String?,
    projectRepository: ProjectRepository,
    journalRepository: JournalRepository,
    sessionRepository: SessionRepository,
    materialsRepository: MaterialsRepository,
    stashRepository: StashRepository,
    externalScope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val templates = CardTemplate.entries.filter { it.isProjectCard == (projectId != null) }
    private val selection = MutableStateFlow(
        CardSelection(templates.first(), templates.first().defaultFields, photoId = null)
    )

    private val sources: Flow<CardSource> = combine(
        combine(
            projectRepository.observeProjects(),
            projectId?.let { journalRepository.observePhotosForProject(it) } ?: flowOf(emptyList()),
            projectId?.let { journalRepository.observeMilestonesForProject(it) } ?: flowOf(emptyList())
        ) { projects, photos, milestones -> Triple(projects, photos, milestones) },
        sessionRepository.observeSessions(),
        materialsRepository.observeAllocations(),
        stashRepository.observeStashItems()
    ) { (projects, photos, milestones), sessions, allocations, stash ->
        CardSource(
            project = projects.firstOrNull { it.id == projectId },
            projects = projects,
            photos = photos,
            milestones = milestones,
            sessions = sessions,
            allocations = allocations,
            stashItems = stash,
            today = LocalDate.now(),
            now = clock()
        )
    }

    val uiState: StateFlow<ShareCardUiState> = combine(sources, selection) { source, chosen ->
        if (projectId != null && source.project == null) {
            ShareCardUiState(isLoading = false, loadFailed = true)
        } else {
            val photoId = chosen.photoId ?: defaultPhotoId(chosen.template, source)
            ShareCardUiState(
                isLoading = false,
                templates = templates,
                template = chosen.template,
                availableFields = chosen.template.availableFields,
                fields = chosen.fields,
                photos = source.photos,
                selectedPhotoId = photoId,
                content = buildCardContent(chosen.template, chosen.fields, photoId, source)
            )
        }
    }
        .catch { emit(ShareCardUiState(isLoading = false, loadFailed = true)) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ShareCardUiState())

    fun selectTemplate(template: CardTemplate) {
        selection.value = CardSelection(template, template.defaultFields, photoId = null)
    }

    fun toggleField(field: CardField) {
        val current = selection.value
        selection.value = current.copy(
            fields = if (field in current.fields) current.fields - field else current.fields + field
        )
    }

    fun selectPhoto(photoId: String) {
        selection.value = selection.value.copy(photoId = photoId, fields = selection.value.fields + CardField.PHOTO)
    }

    companion object {
        fun factory(
            projectId: String?,
            projectRepository: ProjectRepository,
            journalRepository: JournalRepository,
            sessionRepository: SessionRepository,
            materialsRepository: MaterialsRepository,
            stashRepository: StashRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ShareCardViewModel(
                    projectId,
                    projectRepository,
                    journalRepository,
                    sessionRepository,
                    materialsRepository,
                    stashRepository
                )
            }
        }
    }
}
