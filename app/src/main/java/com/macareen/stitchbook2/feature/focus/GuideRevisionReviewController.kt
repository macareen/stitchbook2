package com.macareen.stitchbook2.feature.focus

import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.guide.DefinitionRevision
import com.macareen.stitchbook2.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class GuideRevisionContext(val guideName: String, val projectId: String, val executionId: ExecutionId,
    val version: Long, val isBusy: Boolean = false)

data class ReviewMatch(val candidate: ProgressCarryCandidate, val context: String)
data class ReviewUnmatched(val occurrence: UnmatchedProgress, val context: String, val targetText: String?)
data class SavedGuidePlace(val work: PersistedExecution, val revision: DefinitionRevision,
    val context: String, val currentInstruction: String?)
data class GuideRevisionPanel(val current: PersistedExecution, val revision: DefinitionRevision,
    val change: ReviewedRevisionChange?, val matches: List<ReviewMatch>, val unmatched: List<ReviewUnmatched>,
    val saved: List<SavedGuidePlace>)
data class GuideRevisionReviewState(val loading: Boolean = true, val panel: GuideRevisionPanel? = null,
    val selected: Set<ExecutionAddress> = emptySet(), val busy: Boolean = false,
    val failed: Boolean = false, val stale: Boolean = false)

/** Owns review choices only; durable changes are guarded repository transactions. */
class GuideRevisionReviewController(private val guideId: GuideId, private val expectedPieceId: String?,
    private val guides: GuideRepository, private val executions: ExecutionRepository, private val scope: CoroutineScope,
    private val current: () -> GuideRevisionContext?, private val setBusy: (Boolean) -> Unit,
    private val refresh: () -> Unit,
    private val applied: (PersistedExecution) -> Unit = { refresh() }) {
    private val mutable = MutableStateFlow<GuideRevisionReviewState?>(null)
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    fun open() {
        val context = current()?.takeUnless { it.isBusy } ?: return
        if (state.value != null) return
        mutable.value = GuideRevisionReviewState()
        setBusy(true)
        operation = scope.launch {
            try {
                val work = requireNotNull(executions.loadExecution(context.executionId))
                check(work.version == context.version && work.pieceId == expectedPieceId &&
                    work.state.guideId == guideId && work.state.status in setOf(ExecutionStatus.ACTIVE, ExecutionStatus.COMPLETED))
                val revision = requireNotNull(guides.loadRevision(work.state.definitionRevisionId))
                val latest = guides.getLatestRevision(guideId)?.takeUnless { it.id == revision.id }
                val saved = executions.listExecutionsForScope(guideId, context.projectId.ifEmpty { null }, expectedPieceId)
                    .filter { it.state.executionId != work.state.executionId }.map { past ->
                        val definition = requireNotNull(guides.loadRevision(past.state.definitionRevisionId))
                        val address = past.state.currentAddress
                        SavedGuidePlace(past, definition, address?.let { ReviewLabels(definition.definition).at(it) }.orEmpty(),
                            address?.let { a -> definition.definition.nodes.filterIsInstance<Instruction>().firstOrNull { it.id == a.instructionNodeId }?.text })
                    }
                val change = latest?.let { ReviewedRevisionChange(context.projectId.ifEmpty { null }, work,
                    revision.definition, revision.revisionNumber, it.definition, it.revisionNumber) }
                val panel = withContext(Dispatchers.Default) {
                    val comparison = latest?.let { RevisionProgressComparison.preview(revision.definition, it.definition, work.state.completedAddresses) }
                    val targetLabels = latest?.let { ReviewLabels(it.definition) }
                    val sourceLabels = ReviewLabels(revision.definition)
                    GuideRevisionPanel(work, revision, change,
                        comparison?.candidates.orEmpty().map { ReviewMatch(it, requireNotNull(targetLabels).at(it.target)) },
                        comparison?.unmatched.orEmpty().map { ReviewUnmatched(it, sourceLabels.at(it.source),
                            latest?.definition?.nodes?.filterIsInstance<Instruction>()?.firstOrNull { n -> n.id == it.source.instructionNodeId }?.text) }, saved)
                }
                mutable.value = GuideRevisionReviewState(loading = false, panel = panel,
                    selected = panel.matches.map { it.candidate.target }.toSet())
                setBusy(false)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.value = GuideRevisionReviewState(loading = false, failed = true); setBusy(false) }
        }
    }
    fun select(address: ExecutionAddress) {
        mutable.update { value -> value?.takeUnless { it.busy }?.let {
            if (it.panel?.matches?.any { match -> match.candidate.target == address } != true) it
            else it.copy(selected = if (address in it.selected) it.selected - address else it.selected + address)
        } ?: value }
    }
    fun selectAll(all: Boolean) { mutable.update { value -> value?.takeUnless { it.busy }?.copy(
        selected = if (all) value.panel?.matches.orEmpty().map { it.candidate.target }.toSet() else emptySet()) ?: value } }
    fun close() {
        if (state.value?.busy == true) return
        operation?.cancel(); mutable.value = null; setBusy(false); refresh()
    }
    fun apply() {
        val value = state.value?.takeUnless { it.busy || it.stale } ?: return
        val change = value.panel?.change ?: return
        commit { executions.applyReviewedRevision(change, value.selected) }
    }
    fun resume(place: SavedGuidePlace) {
        val value = state.value?.takeUnless { it.busy || it.stale } ?: return
        val panel = value.panel ?: return
        if (place.work.state.status != ExecutionStatus.ACTIVE || place !in panel.saved) return
        val project = current()?.projectId?.ifEmpty { null }
        commit { executions.resumeReviewedPlace(ReviewedSavedPlace(project, panel.current,
            panel.revision.definition, place.work, place.revision.definition)) }
    }
    private fun commit(action: suspend () -> PersistedExecution) {
        val value = state.value ?: return
        mutable.value = value.copy(busy = true, failed = false)
        setBusy(true)
        operation = scope.launch {
            try { val changed = action(); mutable.value = null; applied(changed) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.value = value.copy(failed = true, stale = error is ExecutionVersionConflictException)
                setBusy(false)
            }
        }
    }
}

private class ReviewLabels(private val definition: GuideDefinition) {
    private val fallback by lazy {
        GuideTraversal(GuideDefinitionValidator.validate(definition)).occurrences().take(10_000)
            .mapIndexed { i, occurrence -> occurrence.address to (i + 1) }.toMap()
    }
    private val nodes = definition.nodes.associateBy { it.id }
    private val parents = buildMap { definition.nodes.filterIsInstance<GuideContainer>().forEach { parent -> parent.children.forEach { put(it, parent.id) } } }
    fun at(address: ExecutionAddress): String {
        val path = mutableListOf<GuideNode>()
        var id = address.instructionNodeId
        while (parents[id] != null) { id = requireNotNull(parents[id]); path += requireNotNull(nodes[id]) }
        val frames = address.ancestryFrames.associateBy { it.containerNodeId }
        if (path.isEmpty()) return fallback[address]?.let { "Step $it" } ?: "Instruction"
        return path.asReversed().mapNotNull { node -> when (node) {
            is Section -> node.title
            is Range -> (frames[node.id] as? AncestryFrame.RangeValue)?.let { "${node.unitLabel} ${it.value}" }
            is Repeat -> (frames[node.id] as? AncestryFrame.RepeatIteration)?.let { "${node.label ?: "Repeat"} ${it.iteration}/${node.count}" }
            is Instruction -> null
        } }.joinToString(" · ")
    }
}
