package com.macareen.stitchbook2.feature.projects

import com.macareen.stitchbook2.domain.execution.*
import com.macareen.stitchbook2.domain.guide.*
import com.macareen.stitchbook2.domain.model.ProjectPiece
import com.macareen.stitchbook2.domain.repository.*
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class ProjectPiecesViewModelTest {
    @Test fun quantityCreatesOneReviewedBatchAndCanceledCallbacksCannotSaveAnotherForm() = Fixture().use { f ->
        f.model.add(); f.model.name("Motif"); f.model.quantity("3")
        val canceled = f.editor
        f.model.dismiss(); f.model.add(); f.model.name("Panel")
        f.model.save(canceled)
        assertEquals(0, f.repo.batchCalls)
        f.model.quantity("2"); f.model.save(f.editor)
        assertEquals(listOf("Panel 1", "Panel 2"), f.repo.names)
        assertEquals(null, f.model.state.value.editor)
        assertEquals(2, f.model.state.value.entries.size)
    }
    @Test fun batchFailureKeepsQuantityAndNameAndBusySaveBlocksDuplicateOrReplacement() = Fixture().use { f ->
        f.model.add(); f.model.name("Motif"); f.model.quantity("5")
        f.repo.fail = true; f.model.save(f.editor)
        assertTrue(f.editor.error)
        assertEquals("5", f.editor.quantity)
        f.repo.fail = false; f.repo.gate = CompletableDeferred()
        val retry = f.editor
        f.model.save(retry); f.model.save(retry); f.model.dismiss(); f.model.add(); f.model.name("Overwrite")
        assertTrue(f.editor.busy)
        assertEquals("Motif", f.editor.name)
        assertEquals(2, f.repo.batchCalls)
        f.repo.gate!!.complete(Unit)
        assertEquals(5, f.model.state.value.entries.size)
    }
    @Test fun freshCopyLocksGuideAndRequiresNameButDoesNotReuseSourceArchiveState() = Fixture().use { f ->
        val source = ProjectPiece("source", "p", "g", "Left panel", true, 0, 0)
        f.repo.pieces.value = listOf(source)
        f.model.copy(source)
        assertFalse(f.editor.canSave)
        f.model.guide("another"); f.model.quantity("8"); f.model.name("Right panel")
        assertEquals("g", f.editor.guideId)
        assertEquals(listOf("Right panel"), f.editor.names)
        f.model.save(f.editor)
        assertEquals(source to "Right panel", f.repo.copied)
        assertEquals(0, f.repo.batchCalls)
        assertFalse(f.repo.pieces.value.last().isArchived)
    }
    @Test fun invalidQuantityAndForeignSourceCannotStartAWritingOperation() = Fixture().use { f ->
        f.model.copy(ProjectPiece("foreign", "other", "g", "Panel", false, 0, 0))
        assertEquals(null, f.model.state.value.editor)
        f.model.add(); f.model.name("Motif"); f.model.quantity("101"); f.model.save(f.editor)
        assertEquals(0, f.repo.batchCalls)
        f.model.quantity("NaN"); assertFalse(f.editor.canSave)
    }

    private class Fixture : AutoCloseable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Pieces()
        private val guide = Guide(GuideId("g"), "p", "Panel guide", null, 0, 0)
        private val guides = object : GuideRepository by unused<GuideRepository>() {
            override fun observeGuides(projectId: String): Flow<List<Guide>> = flowOf(listOf(guide))
            override suspend fun getGuide(guideId: GuideId): Guide = guide
            override suspend fun getLatestRevision(guideId: GuideId): DefinitionRevision = DefinitionRevision(
                DefinitionRevisionId("r"), guideId, 1, 0, GuideDefinition(guideId, DefinitionRevisionId("r"),
                    listOf(NodeId("step")), listOf(Instruction(NodeId("step"), "Work a pass."))))
        }
        val model = ProjectPiecesViewModel("p", guides, repo, scope)
        val editor get() = model.state.value.editor!!
        override fun close() { scope.cancel() }
    }
    private class Pieces : ExecutionRepository by unused<ExecutionRepository>() {
        val pieces = MutableStateFlow<List<ProjectPiece>>(emptyList())
        var names: List<String> = emptyList()
        var batchCalls = 0
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        var copied: Pair<ProjectPiece, String>? = null
        override fun observePieces(projectId: String): Flow<List<ProjectPiece>> = pieces
        override fun observeWorkChanges(): Flow<Unit> = flowOf(Unit)
        override suspend fun getActivePieceExecution(pieceId: String): PersistedExecution? = null
        override suspend fun getLatestPieceExecution(pieceId: String): PersistedExecution? = null
        override suspend fun createPieces(projectId: String, guideId: GuideId, names: List<String>): List<ProjectPiece> {
            batchCalls++
            gate?.await()
            if (fail) error("Test failure")
            this.names = names
            return names.mapIndexed { index, name -> ProjectPiece("piece-$index", projectId, guideId.value, name, false, 0, 0) }
                .also { pieces.value += it }
        }
        override suspend fun copyPiece(expected: ProjectPiece, name: String): ProjectPiece {
            copied = expected to name
            return expected.copy(id = "copy", name = name, isArchived = false).also { pieces.value += it }
        }
    }
    companion object {
        private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader,
            arrayOf(T::class.java)) { _, method, _ -> error("Unexpected repository call: ${method.name}") } as T
    }
}
