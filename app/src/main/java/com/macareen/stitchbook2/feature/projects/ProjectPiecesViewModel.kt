package com.macareen.stitchbook2.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.model.ProjectPiece
import com.macareen.stitchbook2.domain.model.normalizedPieceName
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.GuideRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PieceGuideChoice(val id: String, val name: String)
data class PieceEntry(val piece: ProjectPiece, val guideName: String, val completedSteps: Int,
    val isStarted: Boolean, val isComplete: Boolean, val revisionNumber: Int?)
data class PieceEditor(val expected: ProjectPiece? = null, val name: String = "", val guideId: String = "",
    val busy: Boolean = false, val error: Boolean = false) {
    val canSave: Boolean get() = !busy && normalizedPieceName(name) != null && guideId.isNotBlank()
}
data class ProjectPiecesState(val loading: Boolean = true, val entries: List<PieceEntry> = emptyList(),
    val choices: List<PieceGuideChoice> = emptyList(), val editor: PieceEditor? = null,
    val busyPieceId: String? = null, val failed: Boolean = false)

class ProjectPiecesViewModel(private val projectId: String, private val guides: GuideRepository,
    private val executions: ExecutionRepository, externalScope: CoroutineScope? = null) : ViewModel() {
    private val scope = externalScope ?: viewModelScope
    private val mutableState = MutableStateFlow(ProjectPiecesState())
    val state = mutableState.asStateFlow()
    private var observer: Job? = null
    init { refresh() }
    fun refresh() {
        observer?.cancel()
        observer = scope.launch {
            try {
                combine(guides.observeGuides(projectId), executions.observePieces(projectId), executions.observeWorkChanges()) { choices, pieces, _ -> choices to pieces }
                    .collectLatest { (guidesInProject, pieces) ->
                        val choices = guidesInProject.filter { !it.isArchived && guides.getLatestRevision(it.id) != null }
                            .map { PieceGuideChoice(it.id.value, it.name) }
                        val entries = pieces.map { piece ->
                            val progress = executions.getActivePieceExecution(piece.id) ?: executions.getLatestPieceExecution(piece.id)
                            val revision = progress?.let { guides.loadRevision(it.state.definitionRevisionId) }
                            PieceEntry(piece, guides.getGuide(GuideId(piece.guideId))?.name.orEmpty(),
                                progress?.state?.completedAddresses?.size ?: 0, progress != null,
                                progress?.state?.status == ExecutionStatus.COMPLETED, revision?.revisionNumber)
                        }
                        mutableState.update { it.copy(loading = false, entries = entries, choices = choices, failed = false) }
                    }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, failed = true) } }
        }
    }
    fun add() { mutableState.update { it.copy(editor = PieceEditor(guideId = it.choices.firstOrNull()?.id.orEmpty())) } }
    fun edit(piece: ProjectPiece) { mutableState.update { it.copy(editor = PieceEditor(piece, piece.name, piece.guideId)) } }
    fun name(value: String) { mutableState.update { it.copy(editor = it.editor?.takeUnless { e -> e.busy }?.copy(name = value, error = false) ?: it.editor) } }
    fun guide(id: String) { mutableState.update { it.copy(editor = it.editor?.takeUnless { e -> e.busy || e.expected != null }?.copy(guideId = id, error = false) ?: it.editor) } }
    fun dismiss() { mutableState.update { if (it.editor?.busy == true) it else it.copy(editor = null) } }
    fun save() {
        val editor = state.value.editor ?: return
        if (editor.busy || normalizedPieceName(editor.name) == null || editor.guideId.isBlank()) return
        mutableState.update { it.copy(editor = editor.copy(busy = true, error = false)) }
        scope.launch {
            try {
                val expected = editor.expected
                if (expected == null) executions.createPiece(projectId, GuideId(editor.guideId), editor.name)
                else check(executions.updatePiece(expected, editor.name, expected.isArchived))
                mutableState.update { it.copy(editor = null) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(editor = editor.copy(busy = false, error = true)) } }
        }
    }
    fun archive(piece: ProjectPiece) {
        if (state.value.busyPieceId != null) return
        mutableState.update { it.copy(busyPieceId = piece.id, failed = false) }
        scope.launch {
            try { check(executions.updatePiece(piece, piece.name, !piece.isArchived)) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
            finally { mutableState.update { it.copy(busyPieceId = null) } }
        }
    }
    companion object {
        fun factory(projectId: String, guides: GuideRepository, executions: ExecutionRepository) = viewModelFactory {
            initializer { ProjectPiecesViewModel(projectId, guides, executions) }
        }
    }
}
