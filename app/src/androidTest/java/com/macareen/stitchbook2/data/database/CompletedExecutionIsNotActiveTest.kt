package com.macareen.stitchbook2.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.repository.LocalExecutionRepository
import com.macareen.stitchbook2.data.repository.LocalGuideRepository
import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.ExecutionStatus
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.execution.PersistedExecutionTransitionResult
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated from [ExecutionRepositoryTest] on purpose: that class currently
 * has unrelated pre-existing methods that fail JUnit4's "should be void"
 * validation, which fails the whole class before any of its tests run.
 * Fixing that is out of scope here (see the separately filed follow-up), so
 * this one required invariant -- a completed Execution is no longer
 * returned as ACTIVE -- gets its own small, valid, passing test class
 * instead of depending on that class's currently-broken coverage.
 */
@RunWith(AndroidJUnit4::class)
class CompletedExecutionIsNotActiveTest {

    private lateinit var database: StitchbookDatabase
    private lateinit var guideRepository: LocalGuideRepository
    private lateinit var executionRepository: LocalExecutionRepository
    private lateinit var ids: ArrayDeque<String>
    private var now = 100L

    @Before
    fun createDatabase() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StitchbookDatabase::class.java).build()
        ids = ArrayDeque()
        guideRepository = LocalGuideRepository(
            guideDao = database.guideDao(),
            newId = { ids.removeFirst() },
            currentTimeMillis = { now++ }
        )
        executionRepository = LocalExecutionRepository(
            executionDao = database.executionDao(),
            newId = { ids.removeFirst() },
            currentTimeMillis = { now++ }
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun completingAnExecutionThroughExistingRepositoryTransitionsRemovesItFromActive(): Unit = runBlocking {
        database.projectDao().upsert(projectEntity("project"))
        ids.addLast("guide")
        ids.addLast("draft")
        val guide = guideRepository.createGuide("project", "Guide")
        val draft = checkNotNull(guideRepository.loadDraft(guide.id))
        guideRepository.saveDraft(
            draft.copy(
                rootNodeIds = listOf(NodeId("instruction")),
                nodes = listOf(
                    DraftNode(
                        id = NodeId("instruction"),
                        type = DraftNodeType.INSTRUCTION,
                        instructionText = "Cast on 40 stitches"
                    )
                )
            )
        )
        ids.addLast("revision")
        guideRepository.publishDraft(guide.id)

        ids.addLast("execution")
        val created = executionRepository.createExecution(guide.id, DefinitionRevisionId("revision"))
        assertEquals(ExecutionStatus.ACTIVE, created.state.status)

        // Complete through the existing repository transition -- never a
        // hand-rolled status flip -- and let the single-Instruction guide's
        // own terminal completion (per docs/EXECUTION_ENGINE_SPEC.md) decide
        // the outcome.
        val result = executionRepository.applyComplete(created.state.executionId, created.version)
        val completed = (result as PersistedExecutionTransitionResult.Changed).execution
        assertEquals(ExecutionStatus.COMPLETED, completed.state.status)

        assertNull(executionRepository.getActiveExecution(guide.id))

        val reloaded = checkNotNull(executionRepository.loadExecution(created.state.executionId))
        assertEquals(ExecutionStatus.COMPLETED, reloaded.state.status)
    }

    private fun projectEntity(id: String) = ProjectEntity(
        id = id,
        name = "Project",
        craft = "KNITTING",
        projectType = "OTHER",
        status = "PLANNED",
        notes = null,
        createdAt = 1,
        updatedAt = 1
    )
}
