package com.macareen.stitchbook2.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.repository.LocalExecutionRepository
import com.macareen.stitchbook2.data.repository.LocalGuideRepository
import com.macareen.stitchbook2.domain.execution.AncestryFrame
import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.ExecutionAddress
import com.macareen.stitchbook2.domain.execution.ExecutionEngine
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.execution.ExecutionState
import com.macareen.stitchbook2.domain.execution.ExecutionStatus
import com.macareen.stitchbook2.domain.execution.ExecutionTransitionResult
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.InvalidExecutionAddressException
import com.macareen.stitchbook2.domain.execution.NoChangeReason
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.execution.PersistedExecutionTransitionResult
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import com.macareen.stitchbook2.domain.guide.GuideDraft
import com.macareen.stitchbook2.domain.repository.ExecutionVersionConflictException
import java.util.ArrayDeque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExecutionRepositoryTest {

    private lateinit var database: StitchbookDatabase
    private lateinit var guideRepository: LocalGuideRepository
    private lateinit var executionRepository: LocalExecutionRepository
    private lateinit var ids: ArrayDeque<String>
    private var now = 100L

    @Before
    fun createDatabase() {
        val context: Context =
            InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            StitchbookDatabase::class.java
        ).build()
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

    // ---- Creation ----

    @Test
    fun creatingExecutionInitializesPointerFromPureDomainEngine() = runBlocking {
        val guideId = createGuideWithSimpleRevision(
            guideId = "guide",
            draftId = "draft",
            revisionId = "revision"
        )

        enqueueIds("exec")
        val created = executionRepository.createExecution(
            guideId,
            DefinitionRevisionId("revision")
        )

        val definition = checkNotNull(guideRepository.loadRevision(DefinitionRevisionId("revision")))
            .definition
        val expected = ExecutionEngine.forDefinition(definition)
            .newExecution(ExecutionId("exec"))

        assertEquals(expected, created.state)
        assertEquals(ExecutionStatus.ACTIVE, created.state.status)
        assertEquals(emptySet<ExecutionAddress>(), created.state.completedAddresses)
        assertEquals(0L, created.version)
        assertNull(created.completedAt)
    }

    @Test
    fun revisionMustBelongToSpecifiedGuide() = runBlocking {
        val guideOne = createGuideWithSimpleRevision("guide-one", "draft-one", "revision-one")
        createGuideWithSimpleRevision("guide-two", "draft-two", "revision-two")

        enqueueIds("exec")
        assertSuspendThrows<RevisionGuideMismatchException> {
            executionRepository.createExecution(guideOne, DefinitionRevisionId("revision-two"))
        }
    }

    @Test
    fun creatingExecutionForMissingGuideOrRevisionFailsExplicitly() = runBlocking {
        createGuideWithSimpleRevision("guide", "draft", "revision")

        enqueueIds("exec-1")
        assertSuspendThrows<GuideNotFoundException> {
            executionRepository.createExecution(GuideId("missing"), DefinitionRevisionId("revision"))
        }

        enqueueIds("exec-2")
        assertSuspendThrows<RevisionNotFoundException> {
            executionRepository.createExecution(GuideId("guide"), DefinitionRevisionId("missing"))
        }
    }

    @Test
    fun onlyOneActiveExecutionMayExistPerGuide() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision")
        enqueueIds("exec-1")
        executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))

        enqueueIds("exec-2")
        assertSuspendThrows<ActiveExecutionAlreadyExistsException> {
            executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))
        }
    }

    @Test
    fun multipleCompletedHistoricalExecutionsBelongToOneGuide() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision", occurrences = 1)

        enqueueIds("exec-1")
        val first = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))
        executionRepository.applyComplete(first.state.executionId, first.version)

        enqueueIds("exec-2")
        val second = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))
        executionRepository.applyComplete(second.state.executionId, second.version)

        enqueueIds("exec-3")
        executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))

        val all = executionRepository.listExecutions(guideId)
        assertEquals(3, all.size)
        assertEquals(2, all.count { it.state.status == ExecutionStatus.COMPLETED })
        assertEquals(1, all.count { it.state.status == ExecutionStatus.ACTIVE })
    }

    @Test
    fun differentGuidesEachHaveAnActiveExecution() = runBlocking {
        val guideOne = createGuideWithSimpleRevision("guide-one", "draft-one", "revision-one")
        val guideTwo = createGuideWithSimpleRevision("guide-two", "draft-two", "revision-two")

        enqueueIds("exec-one")
        executionRepository.createExecution(guideOne, DefinitionRevisionId("revision-one"))
        enqueueIds("exec-two")
        executionRepository.createExecution(guideTwo, DefinitionRevisionId("revision-two"))

        val activeOne = checkNotNull(executionRepository.getActiveExecution(guideOne))
        val activeTwo = checkNotNull(executionRepository.getActiveExecution(guideTwo))
        assertEquals(ExecutionId("exec-one"), activeOne.state.executionId)
        assertEquals(ExecutionId("exec-two"), activeTwo.state.executionId)
    }

    @Test
    fun executionStateRoundTripsCorrectly() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision")
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))

        val loaded = checkNotNull(executionRepository.loadExecution(created.state.executionId))
        assertEquals(created, loaded)
    }

    // ---- Complete ----

    @Test
    fun completePersistsExactPureDomainResult() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision")
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))
        val definition = checkNotNull(guideRepository.loadRevision(DefinitionRevisionId("revision")))
            .definition
        val engine = ExecutionEngine.forDefinition(definition)
        val expected = engine.complete(created.state) as ExecutionTransitionResult.Changed

        val result = executionRepository.applyComplete(created.state.executionId, created.version)
            as PersistedExecutionTransitionResult.Changed

        assertEquals(expected.state, result.execution.state)
        assertEquals(1L, result.execution.version)
    }

    @Test
    fun completeSkipsWrapsAndTerminatesFollowingSpecExample() = runBlocking {
        // docs/EXECUTION_ENGINE_SPEC.md section 14.5, four occurrences.
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision", occurrences = 4)
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))
        val executionId = created.state.executionId

        val afterFirstComplete =
            executionRepository.applyComplete(executionId, 0) as PersistedExecutionTransitionResult.Changed
        assertEquals(2, currentRoundValue(afterFirstComplete.execution.state))

        val fourthAddress = addressForRound(created.state, round = 4)
        val afterJump = executionRepository.applyJump(executionId, 1, fourthAddress)
            as PersistedExecutionTransitionResult.Changed
        assertEquals(4, currentRoundValue(afterJump.execution.state))

        // Completing occurrence 4 skips the already-completed occurrence 1
        // and wraps to the earliest incomplete occurrence, round 2.
        val afterFourthComplete =
            executionRepository.applyComplete(executionId, 2) as PersistedExecutionTransitionResult.Changed
        assertEquals(2, currentRoundValue(afterFourthComplete.execution.state))
        assertEquals(
            setOf(1, 4),
            afterFourthComplete.execution.state.completedAddresses.map { roundValue(it) }.toSet()
        )
        assertEquals(ExecutionStatus.ACTIVE, afterFourthComplete.execution.state.status)

        val afterSecondComplete =
            executionRepository.applyComplete(executionId, 3) as PersistedExecutionTransitionResult.Changed
        assertEquals(3, currentRoundValue(afterSecondComplete.execution.state))

        val afterThirdComplete =
            executionRepository.applyComplete(executionId, 4) as PersistedExecutionTransitionResult.Changed
        assertEquals(ExecutionStatus.COMPLETED, afterThirdComplete.execution.state.status)
        assertNull(afterThirdComplete.execution.state.currentAddress)
        assertEquals(4, afterThirdComplete.execution.state.completedAddresses.size)
        assertNotNull(afterThirdComplete.execution.completedAt)
        assertNull(executionRepository.getActiveExecution(guideId))
    }

    @Test
    fun completeAfterTerminalCompletionIsPersistedNoOp() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision", occurrences = 1)
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))
        val completed = executionRepository.applyComplete(created.state.executionId, 0)
            as PersistedExecutionTransitionResult.Changed
        assertEquals(ExecutionStatus.COMPLETED, completed.execution.state.status)

        val noOp = executionRepository.applyComplete(created.state.executionId, 1)
            as PersistedExecutionTransitionResult.NoChange
        assertEquals(NoChangeReason.ALREADY_COMPLETE, noOp.reason)
        assertEquals(1L, noOp.execution.version)

        val reloaded = checkNotNull(executionRepository.loadExecution(created.state.executionId))
        assertEquals(1L, reloaded.version)
        assertEquals(completed.execution.state, reloaded.state)
    }

    // ---- Previous ----

    @Test
    fun previousPersistsExactPureDomainResultAndMarksOnlyThatOccurrenceIncomplete() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision")
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))
        val afterComplete = executionRepository.applyComplete(created.state.executionId, 0)
            as PersistedExecutionTransitionResult.Changed

        val definition = checkNotNull(guideRepository.loadRevision(DefinitionRevisionId("revision")))
            .definition
        val engine = ExecutionEngine.forDefinition(definition)
        val expected = engine.previous(afterComplete.execution.state) as ExecutionTransitionResult.Changed

        val result = executionRepository.applyPrevious(created.state.executionId, 1)
            as PersistedExecutionTransitionResult.Changed

        assertEquals(expected.state, result.execution.state)
        assertEquals(emptySet<ExecutionAddress>(), result.execution.state.completedAddresses)
        assertEquals(created.state.currentAddress, result.execution.state.currentAddress)
    }

    @Test
    fun previousReopensACompletedExecution() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision", occurrences = 1)
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))
        val completed = executionRepository.applyComplete(created.state.executionId, 0)
            as PersistedExecutionTransitionResult.Changed
        assertEquals(ExecutionStatus.COMPLETED, completed.execution.state.status)
        assertNull(executionRepository.getActiveExecution(guideId))

        val reopened = executionRepository.applyPrevious(created.state.executionId, 1)
            as PersistedExecutionTransitionResult.Changed

        assertEquals(ExecutionStatus.ACTIVE, reopened.execution.state.status)
        assertEquals(created.state.currentAddress, reopened.execution.state.currentAddress)
        val active = checkNotNull(executionRepository.getActiveExecution(guideId))
        assertEquals(created.state.executionId, active.state.executionId)
    }

    @Test
    fun previousAtFirstOccurrenceIsNoOp() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision")
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))

        val result = executionRepository.applyPrevious(created.state.executionId, 0)
            as PersistedExecutionTransitionResult.NoChange

        assertEquals(NoChangeReason.ALREADY_AT_FIRST_OCCURRENCE, result.reason)
        assertEquals(0L, result.execution.version)
    }

    // ---- Jump ----

    @Test
    fun jumpChangesOnlyPointerAndPreservesCompletionRecords() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision", occurrences = 4)
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))
        val afterComplete = executionRepository.applyComplete(created.state.executionId, 0)
            as PersistedExecutionTransitionResult.Changed

        val targetAddress = addressForRound(created.state, round = 4)
        val result = executionRepository.applyJump(created.state.executionId, 1, targetAddress)
            as PersistedExecutionTransitionResult.Changed

        assertEquals(targetAddress, result.execution.state.currentAddress)
        assertEquals(
            afterComplete.execution.state.completedAddresses,
            result.execution.state.completedAddresses
        )
    }

    @Test
    fun jumpToCurrentAddressIsNoOp() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision")
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))

        val result = executionRepository.applyJump(
            created.state.executionId,
            0,
            checkNotNull(created.state.currentAddress)
        ) as PersistedExecutionTransitionResult.NoChange

        assertEquals(NoChangeReason.ALREADY_AT_TARGET, result.reason)
        assertEquals(0L, result.execution.version)
    }

    // ---- Nested Range/Repeat addresses ----

    @Test
    fun nestedRangeAndRepeatAddressesPersistCorrectly() = runBlocking {
        // docs/EXECUTION_ENGINE_SPEC.md section 14.3: repeat of 3, each
        // containing rounds 1-4 -> 12 occurrences.
        val guideId = createGuideWithNestedRevision("guide", "draft", "revision")
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))
        var executionId = created.state.executionId
        var version = created.version

        repeat(8) {
            val result = executionRepository.applyComplete(executionId, version)
                as PersistedExecutionTransitionResult.Changed
            version = result.execution.version
        }

        val loaded = checkNotNull(executionRepository.loadExecution(executionId))
        val current = checkNotNull(loaded.state.currentAddress)
        assertEquals(
            listOf(
                AncestryFrame.RepeatIteration(NodeId("repeat"), 3),
                AncestryFrame.RangeValue(NodeId("range"), 1)
            ),
            current.ancestryFrames
        )
        assertEquals(8, loaded.state.completedAddresses.size)
    }

    @Test
    fun nonContiguousCompletionStatePersistsCorrectly() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision", occurrences = 4)
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))
        executionRepository.applyComplete(created.state.executionId, 0)
        val fourthAddress = addressForRound(created.state, round = 4)
        executionRepository.applyJump(created.state.executionId, 1, fourthAddress)
        executionRepository.applyComplete(created.state.executionId, 2)

        val reloaded = checkNotNull(executionRepository.loadExecution(created.state.executionId))
        assertEquals(
            setOf(1, 4),
            reloaded.state.completedAddresses.map { roundValue(it) }.toSet()
        )
        assertEquals(2, currentRoundValue(reloaded.state))
    }

    // ---- Explicit failure, not silent repair ----

    @Test
    fun invalidOrRevisionMismatchedAddressesAreRejected() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision")
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))

        val wrongRevision = ExecutionAddress(
            definitionRevisionId = DefinitionRevisionId("other-revision"),
            instructionNodeId = NodeId("instruction"),
            ancestryFrames = listOf(AncestryFrame.RangeValue(NodeId("range"), 1))
        )

        assertSuspendThrows<InvalidExecutionAddressException> {
            executionRepository.applyJump(created.state.executionId, 0, wrongRevision)
        }
    }

    @Test
    fun malformedPersistedStateFailsExplicitly() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision")
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))

        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO execution_completed_occurrences
                (execution_id, address_signature, instruction_node_id)
            VALUES ('${created.state.executionId.value}', 'bogus', 'missing-node')
            """.trimIndent()
        )

        assertSuspendThrows<MalformedPersistedExecutionStateException> {
            executionRepository.loadExecution(created.state.executionId)
        }
    }

    // ---- Atomicity and concurrency ----

    @Test
    fun transactionRollbackPreventsPartialTransitionWrites() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision")
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))

        assertSuspendThrows<ExecutionVersionConflictException> {
            executionRepository.applyComplete(created.state.executionId, expectedVersion = 99)
        }

        val reloaded = checkNotNull(executionRepository.loadExecution(created.state.executionId))
        assertEquals(created, reloaded)
    }

    @Test
    fun concurrentStaleUpdatesDoNotSilentlyOverwriteNewerState() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision")
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))

        val firstCaller = created
        val secondCaller = created

        val firstResult = executionRepository.applyComplete(
            firstCaller.state.executionId,
            firstCaller.version
        ) as PersistedExecutionTransitionResult.Changed

        assertSuspendThrows<ExecutionVersionConflictException> {
            executionRepository.applyComplete(secondCaller.state.executionId, secondCaller.version)
        }

        val reloaded = checkNotNull(executionRepository.loadExecution(created.state.executionId))
        assertEquals(firstResult.execution.state, reloaded.state)
        assertEquals(1L, reloaded.version)
    }

    // ---- Cascade and revision integrity ----

    @Test
    fun deletingGuideRemovesExecutionsWithoutOrphanRows() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision")
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))
        executionRepository.applyComplete(created.state.executionId, 0)

        guideRepository.deleteGuide(guideId)

        assertNull(executionRepository.loadExecution(created.state.executionId))
        assertEquals(0, countRows("executions", "guide_id = '${guideId.value}'"))
        assertEquals(0, countRows("active_executions", "guide_id = '${guideId.value}'"))
        assertEquals(
            0,
            countRows(
                "execution_current_address_frames",
                "execution_id = '${created.state.executionId.value}'"
            )
        )
        assertEquals(
            0,
            countRows(
                "execution_completed_occurrences",
                "execution_id = '${created.state.executionId.value}'"
            )
        )
    }

    @Test
    fun publishingNewerRevisionDoesNotMoveAnExistingExecution() = runBlocking {
        val guideId = createGuideWithSimpleRevision("guide", "draft", "revision")
        enqueueIds("exec")
        val created = executionRepository.createExecution(guideId, DefinitionRevisionId("revision"))

        val draft = checkNotNull(guideRepository.loadDraft(guideId))
        guideRepository.saveDraft(draft.withInstruction("Purl"))
        enqueueIds("revision-2")
        guideRepository.publishDraft(guideId)

        val reloaded = checkNotNull(executionRepository.loadExecution(created.state.executionId))
        assertEquals(DefinitionRevisionId("revision"), reloaded.state.definitionRevisionId)
        assertEquals(created.state, reloaded.state)
    }

    // ---- Fixtures ----

    private suspend fun createGuideWithSimpleRevision(
        guideId: String,
        draftId: String,
        revisionId: String,
        occurrences: Int = 4
    ): GuideId {
        insertProject("project-$guideId")
        enqueueIds(guideId, draftId)
        val guide = guideRepository.createGuide("project-$guideId", "Guide")
        val draft = checkNotNull(guideRepository.loadDraft(guide.id))
        guideRepository.saveDraft(
            draft.copy(
                rootNodeIds = listOf(nodeId("section")),
                nodes = listOf(
                    DraftNode(
                        id = nodeId("section"),
                        type = DraftNodeType.SECTION,
                        title = "Body",
                        children = listOf(nodeId("range"))
                    ),
                    DraftNode(
                        id = nodeId("range"),
                        type = DraftNodeType.RANGE,
                        rangeUnitLabel = "round",
                        rangeStartInclusive = 1,
                        rangeEndInclusive = occurrences,
                        children = listOf(nodeId("instruction"))
                    ),
                    DraftNode(
                        id = nodeId("instruction"),
                        type = DraftNodeType.INSTRUCTION,
                        instructionText = "Knit"
                    )
                )
            )
        )
        enqueueIds(revisionId)
        guideRepository.publishDraft(guide.id)
        return guide.id
    }

    private suspend fun createGuideWithNestedRevision(
        guideId: String,
        draftId: String,
        revisionId: String
    ): GuideId {
        insertProject("project-$guideId")
        enqueueIds(guideId, draftId)
        val guide = guideRepository.createGuide("project-$guideId", "Guide")
        val draft = checkNotNull(guideRepository.loadDraft(guide.id))
        guideRepository.saveDraft(
            draft.copy(
                rootNodeIds = listOf(nodeId("section")),
                nodes = listOf(
                    DraftNode(
                        id = nodeId("section"),
                        type = DraftNodeType.SECTION,
                        title = "Textured band",
                        children = listOf(nodeId("repeat"))
                    ),
                    DraftNode(
                        id = nodeId("repeat"),
                        type = DraftNodeType.REPEAT,
                        repeatCount = 3,
                        children = listOf(nodeId("range"))
                    ),
                    DraftNode(
                        id = nodeId("range"),
                        type = DraftNodeType.RANGE,
                        rangeUnitLabel = "round",
                        rangeStartInclusive = 1,
                        rangeEndInclusive = 4,
                        children = listOf(nodeId("instruction"))
                    ),
                    DraftNode(
                        id = nodeId("instruction"),
                        type = DraftNodeType.INSTRUCTION,
                        instructionText = "Work texture round"
                    )
                )
            )
        )
        enqueueIds(revisionId)
        guideRepository.publishDraft(guide.id)
        return guide.id
    }

    private fun GuideDraft.withInstruction(
        instruction: String
    ) = copy(
        nodes = nodes.map { node ->
            if (node.type == DraftNodeType.INSTRUCTION) {
                node.copy(instructionText = instruction)
            } else {
                node
            }
        }
    )

    private fun addressForRound(
        state: ExecutionState,
        round: Int
    ): ExecutionAddress {
        val current = checkNotNull(state.currentAddress)
        return current.copy(
            ancestryFrames = listOf(AncestryFrame.RangeValue(NodeId("range"), round))
        )
    }

    private fun roundValue(address: ExecutionAddress): Int {
        return (address.ancestryFrames.single() as AncestryFrame.RangeValue).value
    }

    private fun currentRoundValue(
        state: ExecutionState
    ): Int {
        return roundValue(checkNotNull(state.currentAddress))
    }

    private suspend fun insertProject(id: String) {
        database.projectDao().upsert(projectEntity(id))
    }

    private suspend fun countRows(table: String, whereClause: String): Int {
        return database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM `$table` WHERE $whereClause")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
    }

    private fun enqueueIds(vararg values: String) {
        values.forEach(ids::addLast)
    }

    private fun nodeId(value: String) = NodeId(value)

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        crossinline block: suspend () -> Unit
    ): T {
        return try {
            block()
            throw AssertionError("Expected ${T::class.java.simpleName}")
        } catch (throwable: Throwable) {
            if (throwable is T) throwable else throw throwable
        }
    }
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
