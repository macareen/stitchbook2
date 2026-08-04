package com.macareen.stitchbook2.feature.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import com.macareen.stitchbook2.domain.guide.GuideDraft
import com.macareen.stitchbook2.domain.repository.DraftValidationException
import com.macareen.stitchbook2.domain.repository.DraftVersionConflictException
import com.macareen.stitchbook2.domain.repository.GuideRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One row of the Draft's node tree flattened into displayable outline order
 * (pre-order, depth-first) -- the ViewModel computes this so the screen
 * never has to walk [GuideDraft.nodes] itself.
 */
data class DraftOutlineRow(
    val node: DraftNode,
    val depth: Int,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean
)

sealed interface DraftEditorUiState {
    data object Loading : DraftEditorUiState
    data object NotFound : DraftEditorUiState
    data object LoadError : DraftEditorUiState
    data class Content(
        val guideName: String,
        val rows: List<DraftOutlineRow>,
        val isSaving: Boolean = false,
        val errorMessage: String? = null
    ) : DraftEditorUiState
}

/**
 * Every structural or content edit persists immediately via
 * [GuideRepository.saveDraft] -- there is no separate longer-lived unsaved
 * buffer that could diverge from what Room holds. [uiState] always reflects
 * either the last successfully persisted Draft or (while a save is in
 * flight) the previously persisted one, never an unconfirmed local copy.
 */
class DraftEditorViewModel(
    private val guideId: GuideId,
    private val guideRepository: GuideRepository,
    externalScope: CoroutineScope? = null,
    private val newNodeId: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow<DraftEditorUiState>(DraftEditorUiState.Loading)
    val uiState: StateFlow<DraftEditorUiState> = _uiState.asStateFlow()

    private var draft: GuideDraft? = null
    private var guideName: String = ""

    init {
        load()
    }

    private fun load() {
        scope.launch {
            try {
                val guide = guideRepository.getGuide(guideId)
                val loadedDraft = guideRepository.loadDraft(guideId)
                if (guide == null || loadedDraft == null) {
                    _uiState.value = DraftEditorUiState.NotFound
                    return@launch
                }
                guideName = guide.name
                draft = loadedDraft
                render()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = DraftEditorUiState.LoadError
            }
        }
    }

    /**
     * Adds a new node either as a root node ([parentId] null) or as the last
     * child of an existing container node.
     */
    fun addNode(
        type: DraftNodeType,
        parentId: NodeId?,
        title: String? = null,
        instructionText: String? = null,
        rangeUnitLabel: String? = null,
        rangeStartInclusive: Int? = null,
        rangeEndInclusive: Int? = null,
        repeatCount: Int? = null,
        repeatLabel: String? = null
    ) {
        val current = draft ?: return
        if (isSaving()) return

        val newId = NodeId(newNodeId())
        val newNode = DraftNode(
            id = newId,
            type = type,
            title = title,
            instructionText = instructionText,
            rangeUnitLabel = rangeUnitLabel,
            rangeStartInclusive = rangeStartInclusive,
            rangeEndInclusive = rangeEndInclusive,
            repeatCount = repeatCount,
            repeatLabel = repeatLabel
        )

        val updatedNodes = if (parentId == null) {
            current.nodes + newNode
        } else {
            current.nodes.map { existing ->
                if (existing.id == parentId) {
                    existing.copy(children = existing.children + newId)
                } else {
                    existing
                }
            } + newNode
        }
        val updatedRootIds = if (parentId == null) {
            current.rootNodeIds + newId
        } else {
            current.rootNodeIds
        }

        persist(current.copy(rootNodeIds = updatedRootIds, nodes = updatedNodes))
    }

    /** Updates an existing node's own content fields; never its children or position. */
    fun updateNode(
        nodeId: NodeId,
        title: String? = null,
        instructionText: String? = null,
        rangeUnitLabel: String? = null,
        rangeStartInclusive: Int? = null,
        rangeEndInclusive: Int? = null,
        repeatCount: Int? = null,
        repeatLabel: String? = null
    ) {
        val current = draft ?: return
        if (isSaving()) return

        val updatedNodes = current.nodes.map { existing ->
            if (existing.id != nodeId) {
                existing
            } else {
                existing.copy(
                    title = title,
                    instructionText = instructionText,
                    rangeUnitLabel = rangeUnitLabel,
                    rangeStartInclusive = rangeStartInclusive,
                    rangeEndInclusive = rangeEndInclusive,
                    repeatCount = repeatCount,
                    repeatLabel = repeatLabel
                )
            }
        }
        persist(current.copy(nodes = updatedNodes))
    }

    /** Deletes [nodeId] and every one of its descendants. */
    fun deleteNode(nodeId: NodeId) {
        val current = draft ?: return
        if (isSaving()) return

        val toRemove = descendantsIncludingSelf(current, nodeId)
        val updatedNodes = current.nodes
            .filterNot { it.id in toRemove }
            .map { it.copy(children = it.children.filterNot { childId -> childId in toRemove }) }
        val updatedRootIds = current.rootNodeIds.filterNot { it in toRemove }

        persist(current.copy(rootNodeIds = updatedRootIds, nodes = updatedNodes))
    }

    /** Swaps [nodeId] with its previous sibling, whether a root node or a container's child. */
    fun moveUp(nodeId: NodeId) = move(nodeId, -1)

    /** Swaps [nodeId] with its next sibling, whether a root node or a container's child. */
    fun moveDown(nodeId: NodeId) = move(nodeId, 1)

    fun dismissError() {
        val content = _uiState.value as? DraftEditorUiState.Content ?: return
        _uiState.value = content.copy(errorMessage = null)
    }

    private fun move(nodeId: NodeId, delta: Int) {
        val current = draft ?: return
        if (isSaving()) return

        val parent = current.nodes.firstOrNull { nodeId in it.children }
        if (parent != null) {
            val siblings = parent.children.toMutableList()
            val index = siblings.indexOf(nodeId)
            val target = index + delta
            if (target !in siblings.indices) return
            siblings[index] = siblings[target].also { siblings[target] = siblings[index] }
            val updatedNodes = current.nodes.map {
                if (it.id == parent.id) it.copy(children = siblings) else it
            }
            persist(current.copy(nodes = updatedNodes))
        } else {
            val roots = current.rootNodeIds.toMutableList()
            val index = roots.indexOf(nodeId)
            if (index < 0) return
            val target = index + delta
            if (target !in roots.indices) return
            roots[index] = roots[target].also { roots[target] = roots[index] }
            persist(current.copy(rootNodeIds = roots))
        }
    }

    private fun descendantsIncludingSelf(draft: GuideDraft, nodeId: NodeId): Set<NodeId> {
        val byId = draft.nodes.associateBy { it.id }
        val result = mutableSetOf<NodeId>()
        fun visit(id: NodeId) {
            if (!result.add(id)) return
            byId[id]?.children?.forEach(::visit)
        }
        visit(nodeId)
        return result
    }

    private fun isSaving(): Boolean {
        return (_uiState.value as? DraftEditorUiState.Content)?.isSaving == true
    }

    private fun persist(updated: GuideDraft) {
        val content = _uiState.value as? DraftEditorUiState.Content
        _uiState.value = content?.copy(isSaving = true, errorMessage = null)
            ?: DraftEditorUiState.Content(
                guideName = guideName,
                rows = outlineRows(updated),
                isSaving = true
            )

        scope.launch {
            try {
                val saved = guideRepository.saveDraft(updated)
                draft = saved
                render()
            } catch (error: CancellationException) {
                throw error
            } catch (error: DraftValidationException) {
                showError(error.message ?: "This draft isn't structured correctly.")
            } catch (error: DraftVersionConflictException) {
                reloadAfterConflict()
            } catch (_: Exception) {
                showError("This step could not be saved. Try again.")
            }
        }
    }

    private fun showError(message: String) {
        val unchanged = draft ?: return
        _uiState.value = DraftEditorUiState.Content(
            guideName = guideName,
            rows = outlineRows(unchanged),
            isSaving = false,
            errorMessage = message
        )
    }

    private suspend fun reloadAfterConflict() {
        // loadDraft returning null here means the Guide/Draft itself is gone
        // (not merely changed) -- still must not leave isSaving stuck true,
        // so this falls through to NotFound rather than returning silently.
        val reloaded = guideRepository.loadDraft(guideId)
        if (reloaded == null) {
            _uiState.value = DraftEditorUiState.NotFound
            return
        }
        draft = reloaded
        _uiState.value = DraftEditorUiState.Content(
            guideName = guideName,
            rows = outlineRows(reloaded),
            isSaving = false,
            errorMessage = "This changed elsewhere. Showing the current draft."
        )
    }

    private fun render() {
        val current = draft ?: return
        _uiState.value = DraftEditorUiState.Content(
            guideName = guideName,
            rows = outlineRows(current)
        )
    }

    private fun outlineRows(draft: GuideDraft): List<DraftOutlineRow> {
        val byId = draft.nodes.associateBy { it.id }
        val rows = mutableListOf<DraftOutlineRow>()

        fun visit(nodeId: NodeId, depth: Int, siblings: List<NodeId>) {
            val node = byId[nodeId] ?: return
            val index = siblings.indexOf(nodeId)
            rows += DraftOutlineRow(
                node = node,
                depth = depth,
                canMoveUp = index > 0,
                canMoveDown = index < siblings.lastIndex
            )
            node.children.forEach { childId -> visit(childId, depth + 1, node.children) }
        }

        draft.rootNodeIds.forEach { rootId -> visit(rootId, 0, draft.rootNodeIds) }
        return rows
    }

    companion object {
        fun factory(
            guideId: GuideId,
            guideRepository: GuideRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DraftEditorViewModel(guideId, guideRepository)
            }
        }
    }
}
