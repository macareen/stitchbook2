package com.macareen.stitchbook2.feature.assist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.parsing.StructuredGuide
import com.macareen.stitchbook2.domain.parsing.StructuredGuideDecodeResult
import com.macareen.stitchbook2.domain.parsing.StructuredGuideDecoder
import com.macareen.stitchbook2.domain.parsing.StructuredGuideProblem
import com.macareen.stitchbook2.domain.parsing.StructuredGuidePrompt
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromStructuredGuideUseCase
import java.io.ByteArrayInputStream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PatternTextError { NO_TEXT, UNREADABLE }

/** What a checked reply will become, shown before anything is saved. */
sealed interface ReplyCheck {
    data class Ready(val guide: StructuredGuide) : ReplyCheck
    data class Problems(val problems: List<StructuredGuideProblem>) : ReplyCheck
}

data class AssistedImportUiState(
    val craft: Craft = Craft.OTHER,
    val guideName: String = "",
    val patternText: String = "",
    val isReadingPdf: Boolean = false,
    val patternTextError: PatternTextError? = null,
    val reply: String = "",
    val check: ReplyCheck? = null,
    val isCreating: Boolean = false,
    val createFailed: Boolean = false
) {
    val hasPatternText: Boolean get() = patternText.isNotBlank()
    val prompt: String
        get() = StructuredGuidePrompt.build(craft, guideName.ifBlank { DEFAULT_GUIDE_NAME }, patternText)

    companion object {
        const val DEFAULT_GUIDE_NAME = "Imported guide"
    }
}

/**
 * Drives the "ask an assistant" import: the person brings pattern text (from
 * a PDF or pasted), takes a prompt to an assistant of their choice, pastes
 * the reply back, sees what it will create, and only then creates a draft.
 * The app makes no network call at any point.
 */
class AssistedImportViewModel(
    private val projectId: String,
    projectRepository: ProjectRepository,
    private val textExtractor: PdfTextExtractor,
    private val decoder: StructuredGuideDecoder,
    private val createGuide: CreateGuideFromStructuredGuideUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private val state = MutableStateFlow(AssistedImportUiState())
    val uiState: StateFlow<AssistedImportUiState> = state.asStateFlow()

    private val created = MutableStateFlow<String?>(null)

    /** The new guide's id once its draft exists; the screen opens the Draft editor. */
    val createdGuideId: StateFlow<String?> = created.asStateFlow()

    init {
        scope.launch {
            val project = projectRepository.observeProject(projectId).filterNotNull().first()
            state.update { it.copy(craft = project.craft) }
        }
    }

    fun updateGuideName(name: String) = state.update { it.copy(guideName = name) }

    fun updatePatternText(text: String) = state.update { it.copy(patternText = text, patternTextError = null) }

    fun readPdf(bytes: ByteArray) {
        if (state.value.isReadingPdf) return
        state.update { it.copy(isReadingPdf = true, patternTextError = null) }
        scope.launch {
            val document = try {
                withContext(ioDispatcher) { textExtractor.extract(ByteArrayInputStream(bytes)) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            state.update { current ->
                when {
                    document == null -> current.copy(isReadingPdf = false, patternTextError = PatternTextError.UNREADABLE)
                    document.hasNoExtractableText ->
                        current.copy(isReadingPdf = false, patternTextError = PatternTextError.NO_TEXT)

                    else -> current.copy(isReadingPdf = false, patternText = StructuredGuidePrompt.patternText(document))
                }
            }
        }
    }

    fun updateReply(reply: String) = state.update { it.copy(reply = reply, check = null, createFailed = false) }

    fun checkReply() {
        val check = when (val result = decoder.decode(state.value.reply)) {
            is StructuredGuideDecodeResult.Valid -> ReplyCheck.Ready(result.guide)
            is StructuredGuideDecodeResult.Invalid -> ReplyCheck.Problems(result.problems)
        }
        state.update { current ->
            // Fill an empty name from the reply so the person doesn't have to type it twice.
            val name = if (check is ReplyCheck.Ready && current.guideName.isBlank()) {
                check.guide.name.orEmpty()
            } else {
                current.guideName
            }
            current.copy(check = check, guideName = name)
        }
    }

    fun createDraft() {
        val current = state.value
        val guide = (current.check as? ReplyCheck.Ready)?.guide ?: return
        if (current.isCreating) return
        state.update { it.copy(isCreating = true, createFailed = false) }
        scope.launch {
            val name = current.guideName.trim().ifEmpty { guide.name ?: AssistedImportUiState.DEFAULT_GUIDE_NAME }
            try {
                created.value = createGuide(projectId, name, guide).value
                state.update { it.copy(isCreating = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update { it.copy(isCreating = false, createFailed = true) }
            }
        }
    }

    companion object {
        fun factory(
            projectId: String,
            projectRepository: ProjectRepository,
            textExtractor: PdfTextExtractor,
            decoder: StructuredGuideDecoder,
            createGuide: CreateGuideFromStructuredGuideUseCase
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AssistedImportViewModel(projectId, projectRepository, textExtractor, decoder, createGuide)
            }
        }
    }
}
