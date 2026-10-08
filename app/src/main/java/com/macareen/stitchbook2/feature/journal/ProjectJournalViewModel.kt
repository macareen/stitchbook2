package com.macareen.stitchbook2.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.JournalEntry
import com.macareen.stitchbook2.domain.model.Milestone
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.model.PickedDocument
import com.macareen.stitchbook2.domain.model.PhotoRole
import com.macareen.stitchbook2.domain.model.beforeAfterPair
import com.macareen.stitchbook2.domain.model.isValidOptionalLocalDate
import com.macareen.stitchbook2.domain.model.parseLocalDateOrNull
import com.macareen.stitchbook2.domain.model.reorderMilestones
import com.macareen.stitchbook2.domain.model.sortedForDisplay
import com.macareen.stitchbook2.domain.repository.JournalRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import java.time.LocalDate
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

enum class JournalFeedback {
    INVALID_DATE,
    TITLE_REQUIRED,
    BODY_REQUIRED,
    SAVE_FAILED
}

data class ProjectJournalUiState(
    val isLoading: Boolean = true,
    val projectName: String = "",
    val photos: List<Photo> = emptyList(),
    val beforeAfter: Pair<Photo, Photo>? = null,
    val milestones: List<Milestone> = emptyList(),
    val entries: List<JournalEntry> = emptyList(),
    val feedback: JournalFeedback? = null
)

class ProjectJournalViewModel(
    private val projectId: String,
    projectRepository: ProjectRepository,
    private val journalRepository: JournalRepository,
    externalScope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val feedback = MutableStateFlow<JournalFeedback?>(null)

    val uiState: StateFlow<ProjectJournalUiState> = combine(
        projectRepository.observeProject(projectId),
        journalRepository.observePhotosForProject(projectId),
        journalRepository.observeMilestonesForProject(projectId),
        journalRepository.observeEntriesForProject(projectId),
        feedback
    ) { project, photos, milestones, entries, message ->
        ProjectJournalUiState(
            isLoading = false,
            projectName = project?.name.orEmpty(),
            photos = photos,
            beforeAfter = photos.beforeAfterPair(),
            milestones = milestones.sortedWith(compareBy<Milestone> { it.position }.thenBy { it.createdAt }),
            entries = entries.sortedForDisplay(),
            feedback = message
        )
    }
        .catch { emit(ProjectJournalUiState(isLoading = false, feedback = JournalFeedback.SAVE_FAILED)) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ProjectJournalUiState())

    fun addPhotos(documents: List<PickedDocument>) = launchSafely {
        val now = clock()
        documents.forEachIndexed { index, document ->
            journalRepository.savePhoto(
                Photo(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    stashItemId = null,
                    uri = document.uri,
                    displayName = document.displayName,
                    caption = null,
                    takenDate = null,
                    milestoneId = null,
                    role = PhotoRole.NONE,
                    // Distinct timestamps keep a multi-select batch in pick order.
                    createdAt = now + index,
                    updatedAt = now + index
                )
            )
        }
    }

    fun updatePhoto(photo: Photo, caption: String, takenDate: String, milestoneId: String?, role: PhotoRole) {
        if (!isValidOptionalLocalDate(takenDate)) {
            feedback.value = JournalFeedback.INVALID_DATE
            return
        }
        launchSafely {
            val now = clock()
            // Only one photo per project holds each before/after role, so the
            // comparison is never ambiguous.
            if (role != PhotoRole.NONE) {
                journalRepository.observePhotosForProject(projectId).first()
                    .filter { it.id != photo.id && it.role == role }
                    .forEach { journalRepository.savePhoto(it.copy(role = PhotoRole.NONE, updatedAt = now)) }
            }
            journalRepository.savePhoto(
                photo.copy(
                    caption = caption.trim().ifEmpty { null },
                    takenDate = parseLocalDateOrNull(takenDate)?.toString(),
                    milestoneId = milestoneId,
                    role = role,
                    updatedAt = now
                )
            )
        }
    }

    /** Points an existing record at a new file after the original moved or lost permission. */
    fun relinkPhoto(photo: Photo, document: PickedDocument) = launchSafely {
        journalRepository.savePhoto(photo.copy(uri = document.uri, displayName = document.displayName, updatedAt = clock()))
    }

    /** Removes the reference only -- the user's photo file stays where it is. */
    fun removePhoto(photo: Photo) = launchSafely { journalRepository.deletePhoto(photo) }

    fun saveMilestone(original: Milestone?, title: String, reachedDate: String, notes: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) {
            feedback.value = JournalFeedback.TITLE_REQUIRED
            return
        }
        if (!isValidOptionalLocalDate(reachedDate)) {
            feedback.value = JournalFeedback.INVALID_DATE
            return
        }
        launchSafely {
            val now = clock()
            val position = original?.position
                ?: (journalRepository.observeMilestonesForProject(projectId).first().maxOfOrNull { it.position }?.plus(1) ?: 0)
            val milestone = Milestone(
                id = original?.id ?: UUID.randomUUID().toString(),
                projectId = projectId,
                title = normalizedTitle,
                reachedDate = parseLocalDateOrNull(reachedDate)?.toString(),
                notes = notes.trim().ifEmpty { null },
                position = position,
                createdAt = original?.createdAt ?: now,
                updatedAt = now
            )
            journalRepository.saveMilestones(listOf(milestone))
        }
    }

    fun moveMilestone(id: String, direction: Int) = launchSafely {
        val changed = reorderMilestones(
            journalRepository.observeMilestonesForProject(projectId).first(),
            id,
            direction,
            clock()
        )
        if (changed.isNotEmpty()) journalRepository.saveMilestones(changed)
    }

    fun deleteMilestone(milestone: Milestone) = launchSafely { journalRepository.deleteMilestone(milestone) }

    fun saveEntry(original: JournalEntry?, entryDate: String, title: String, body: String) {
        val normalizedBody = body.trim()
        if (normalizedBody.isEmpty()) {
            feedback.value = JournalFeedback.BODY_REQUIRED
            return
        }
        val date = if (entryDate.isBlank()) LocalDate.now() else parseLocalDateOrNull(entryDate)
        if (date == null) {
            feedback.value = JournalFeedback.INVALID_DATE
            return
        }
        launchSafely {
            val now = clock()
            journalRepository.saveEntry(
                JournalEntry(
                    id = original?.id ?: UUID.randomUUID().toString(),
                    projectId = projectId,
                    entryDate = date.toString(),
                    title = title.trim().ifEmpty { null },
                    body = normalizedBody,
                    createdAt = original?.createdAt ?: now,
                    updatedAt = now
                )
            )
        }
    }

    fun deleteEntry(entry: JournalEntry) = launchSafely { journalRepository.deleteEntry(entry) }

    fun dismissFeedback() {
        feedback.value = null
    }

    private fun launchSafely(block: suspend () -> Unit) {
        feedback.value = null
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                feedback.value = JournalFeedback.SAVE_FAILED
            }
        }
    }

    companion object {
        fun factory(
            projectId: String,
            projectRepository: ProjectRepository,
            journalRepository: JournalRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProjectJournalViewModel(projectId, projectRepository, journalRepository)
            }
        }
    }
}
