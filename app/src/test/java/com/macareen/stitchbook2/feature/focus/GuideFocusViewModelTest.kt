package com.macareen.stitchbook2.feature.focus

import com.macareen.stitchbook2.domain.execution.AncestryFrame
import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.ExecutionAddress
import com.macareen.stitchbook2.domain.execution.ExecutionAddressError
import com.macareen.stitchbook2.domain.execution.ExecutionEngine
import com.macareen.stitchbook2.domain.execution.ExecutionEngineFixtures
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.execution.ExecutionState
import com.macareen.stitchbook2.domain.execution.ExecutionStatus
import com.macareen.stitchbook2.domain.execution.ExecutionTransitionResult
import com.macareen.stitchbook2.domain.execution.GuideDefinition
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.Instruction
import com.macareen.stitchbook2.domain.execution.InvalidExecutionAddressException
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.execution.PersistedExecution
import com.macareen.stitchbook2.domain.execution.PersistedExecutionTransitionResult
import com.macareen.stitchbook2.domain.execution.Range
import com.macareen.stitchbook2.domain.guide.DefinitionRevision
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.guide.GuideDraft
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.ExecutionVersionConflictException
import com.macareen.stitchbook2.domain.repository.GuideRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideFocusViewModelTest {

    private val guideId = ExecutionEngineFixtures.guideId
    private val revisionId = ExecutionEngineFixtures.revisionId

    // Dispatchers.Unconfined runs launched coroutines synchronously up to
    // their first real suspension point. Every fake repository call below
    // is a plain in-memory operation with no real suspension, so every
    // ViewModel action below completes before the calling test line
    // returns -- no test-coroutines artifact or manual idling is needed.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun continuingAnActiveExecutionRendersItsCurrentInstruction() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        val execution = create(executions)
        executions.complete(execution.state.executionId, 0)

        val viewModel = viewModel(guides, executions)

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals("Knit all stitches", state.instructionText)
        assertEquals(
            listOf(StructuralPosition.RangePosition("round", 2, 1, 4)),
            state.positions
        )
    }

    @Test
    fun loadingAnActiveExecutionAlsoLoadsTheProjectsCounters() {
        val guides = FakeGuideRepository().withGuide(projectId = "project-1").withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val counters = FakeCounterRepository(
            listOf(
                counter("row", projectId = "project-1"),
                counter("other-project", projectId = "different-project")
            )
        )

        val viewModel = viewModel(guides, executions, counters)

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(listOf("row"), state.projectCounters.map { it.id })
    }

    @Test
    fun incrementingAProjectCounterUpdatesTheSection() {
        val guides = FakeGuideRepository().withGuide(projectId = "project-1").withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val counters = FakeCounterRepository(listOf(counter("row", projectId = "project-1", currentValue = 5)))
        val viewModel = viewModel(guides, executions, counters)
        val counterBefore = (viewModel.uiState.value as GuideFocusUiState.InProgress).projectCounters.single()

        viewModel.onIncrementCounter(counterBefore)

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(6, state.projectCounters.single().currentValue)
    }

    @Test
    fun decrementingAProjectCounterFloorsAtZero() {
        val guides = FakeGuideRepository().withGuide(projectId = "project-1").withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val counters = FakeCounterRepository(listOf(counter("row", projectId = "project-1", currentValue = 0)))
        val viewModel = viewModel(guides, executions, counters)
        val counterBefore = (viewModel.uiState.value as GuideFocusUiState.InProgress).projectCounters.single()

        viewModel.onDecrementCounter(counterBefore)

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(0, state.projectCounters.single().currentValue)
    }

    @Test
    fun completingAGuideCarriesTheCountersSectionForwardWithoutRefetching() {
        val guides = FakeGuideRepository().withGuide(projectId = "project-1").withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val counters = FakeCounterRepository(listOf(counter("row", projectId = "project-1", currentValue = 5)))
        val viewModel = viewModel(guides, executions, counters)

        viewModel.onComplete()

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(listOf("row"), state.projectCounters.map { it.id })
    }

    @Test
    fun startingAnExecutionInitializesFromPureDomainEngine() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)

        val viewModel = viewModel(guides, executions)
        assertTrue(viewModel.uiState.value is GuideFocusUiState.ReadyToStart)

        viewModel.onStart()

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(0L, state.version)
        assertEquals("Knit all stitches", state.instructionText)
        assertEquals(StructuralPosition.RangePosition("round", 1, 1, 4), state.positions.single())
    }

    @Test
    fun completeDelegatesToTheEngineAndAdvancesTheInstruction() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val viewModel = viewModel(guides, executions)

        viewModel.onComplete()

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(1L, state.version)
        assertEquals(StructuralPosition.RangePosition("round", 2, 1, 4), state.positions.single())
        assertNull(state.feedback)
    }

    @Test
    fun previousDelegatesToTheEngineAndRewindsTheInstruction() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val viewModel = viewModel(guides, executions)
        viewModel.onComplete()

        viewModel.onPrevious()

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(2L, state.version)
        assertEquals(StructuralPosition.RangePosition("round", 1, 1, 4), state.positions.single())
    }

    @Test
    fun jumpToFirstIncompleteDelegatesToTheEngineWithoutTouchingOtherCompletionRecords() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        val execution = create(executions)
        // Complete round 1 (current -> round 2), then jump ahead to round 3
        // without completing round 2, leaving it as the earliest incomplete
        // step while current sits at round 3.
        executions.complete(execution.state.executionId, 0)
        executions.jump(execution.state.executionId, 1, roundAddress(3))

        val viewModel = viewModel(guides, executions)
        val before = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(roundAddress(2), before.jumpToFirstIncompleteTarget)

        viewModel.onJumpToFirstIncomplete()

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(StructuralPosition.RangePosition("round", 2, 1, 4), state.positions.single())
        assertEquals(setOf(1), completedRounds(state.executionId, executions))
    }

    @Test
    fun jumpToFirstIncompleteIsAGuardedNoOpWhenAlreadyThere() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val viewModel = viewModel(guides, executions)
        val before = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertNull(before.jumpToFirstIncompleteTarget)

        viewModel.onJumpToFirstIncomplete()

        assertEquals(before, viewModel.uiState.value)
    }

    @Test
    fun terminalCompletionRendersTheCompletedStateAndClearsActiveExecution() {
        val guides = FakeGuideRepository().withGuide()
            .withRevision(ExecutionEngineFixtures.singleInstructionGuide().definition)
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val viewModel = viewModel(guides, executions)

        viewModel.onComplete()

        assertTrue(viewModel.uiState.value is GuideFocusUiState.Completed)
        assertNull(runBlocking { executions.getActiveExecution(guideId) })
    }

    @Test
    fun nestedRepeatAndRangeContextRendersBreadcrumbsAndBothPositions() {
        val guides = FakeGuideRepository().withGuide()
            .withRevision(ExecutionEngineFixtures.repeatContainingRangeGuide().definition)
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val viewModel = viewModel(guides, executions)

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(listOf("Textured band"), state.breadcrumbs)
        assertEquals(
            listOf(
                StructuralPosition.RepeatPosition(label = null, currentIteration = 1, count = 3),
                StructuralPosition.RangePosition("round", 1, 1, 4)
            ),
            state.positions
        )
    }

    @Test
    fun recreatingTheViewModelRestoresTheSamePersistedState() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val first = viewModel(guides, executions)
        first.onComplete()
        val beforeRecreation = first.uiState.value as GuideFocusUiState.InProgress

        // A brand-new ViewModel instance over the same persistence-backed
        // repositories simulates reopening after process death.
        val recreated = viewModel(guides, executions)

        val state = recreated.uiState.value as GuideFocusUiState.InProgress
        assertEquals(beforeRecreation.instructionText, state.instructionText)
        assertEquals(beforeRecreation.positions, state.positions)
        assertEquals(beforeRecreation.version, state.version)
    }

    @Test
    fun previousAtTheFirstOccurrenceIsAPersistedNoOpWithFeedback() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val viewModel = viewModel(guides, executions)

        viewModel.onPrevious()

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(0L, state.version)
        assertEquals(FocusFeedback.ALREADY_AT_FIRST_OCCURRENCE, state.feedback)
    }

    @Test
    fun staleExecutionStateIsSurfacedAndUiResyncsWithoutSilentOverwrite() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        val execution = create(executions)
        val viewModel = viewModel(guides, executions)

        // Another caller completes the step behind the ViewModel's back.
        executions.complete(execution.state.executionId, 0)

        viewModel.onComplete()

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(FocusFeedback.STALE_EXECUTION_STATE, state.feedback)
        // Resynced to what the other caller actually persisted (round 2);
        // the ViewModel's own stale attempt never applied on top of it.
        assertEquals(StructuralPosition.RangePosition("round", 2, 1, 4), state.positions.single())
    }

    @Test
    fun invalidTransitionIsSurfacedAndUiResyncsToTheCurrentValidState() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val viewModel = viewModel(guides, executions)
        executions.failNextTransitionWith = InvalidExecutionAddressException(
            ExecutionAddressError.UnresolvedAddress(roundAddress(1))
        )

        viewModel.onComplete()

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(FocusFeedback.INVALID_TRANSITION, state.feedback)
        assertEquals(0L, state.version)
    }

    @Test
    fun malformedPersistedStateFailsExplicitlyAsALoadError() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        guides.failNextRevisionLoadWith = IllegalStateException("Stored revision is malformed")

        val viewModel = viewModel(guides, executions)

        assertEquals(GuideFocusUiState.LoadError, viewModel.uiState.value)
    }

    @Test
    fun draftOnlyGuideOffersNoStartAndCannotExecute() {
        // A Guide that has never published a Revision -- Draft-only -- has
        // no Start action to offer at all, per docs/EXECUTION_ENGINE_SPEC.md:
        // drafts are never executable.
        val guides = FakeGuideRepository().withGuide()
        val executions = FakeExecutionRepository(guides)

        val viewModel = viewModel(guides, executions)

        assertEquals(GuideFocusUiState.NoPublishedRevision, viewModel.uiState.value)
    }

    @Test
    fun repeatedStartCallsWhileTheFirstIsInFlightCreateOnlyOneExecution() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        val gate = CompletableDeferred<Unit>()
        executions.pauseNextCreateUntil = gate

        val viewModel = viewModel(guides, executions)
        assertTrue(viewModel.uiState.value is GuideFocusUiState.ReadyToStart)

        // Dispatchers.Unconfined runs eagerly up to the first real
        // suspension point, so this call parks inside createExecution at
        // gate.await() and returns control here with isStarting already true.
        viewModel.onStart()
        assertTrue((viewModel.uiState.value as GuideFocusUiState.ReadyToStart).isStarting)

        // A second tap while the first is still in flight must be a no-op:
        // the state-type guard already moved past ReadyToStart's identity
        // (isStarting = true), so this must not call createExecution again.
        viewModel.onStart()

        gate.complete(Unit)

        assertTrue(viewModel.uiState.value is GuideFocusUiState.InProgress)
        assertEquals(1, executions.createExecutionCallCount)
    }

    @Test
    fun continuingAnActiveExecutionNeverCallsCreateExecution() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val callsBeforeViewModel = executions.createExecutionCallCount

        val viewModel = viewModel(guides, executions)

        assertTrue(viewModel.uiState.value is GuideFocusUiState.InProgress)
        assertEquals(callsBeforeViewModel, executions.createExecutionCallCount)
    }

    @Test
    fun repeatedCompleteCallsWhileTheFirstIsInFlightApplyOnlyOneTransition() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        create(executions)
        val viewModel = viewModel(guides, executions)
        val gate = CompletableDeferred<Unit>()
        executions.pauseNextTransitionUntil = gate

        // Parks inside applyComplete at gate.await(), returning control here
        // with isBusy already true.
        viewModel.onComplete()
        assertTrue((viewModel.uiState.value as GuideFocusUiState.InProgress).isBusy)

        // Further taps while the first write is still in flight must be
        // no-ops: onComplete/onPrevious/onJumpToFirstIncomplete all share
        // the same isBusy guard in applyTransition, so retrying any of them
        // must not reach the repository a second time.
        viewModel.onComplete()
        viewModel.onPrevious()

        gate.complete(Unit)

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(1L, state.version)
        assertEquals(StructuralPosition.RangePosition("round", 2, 1, 4), state.positions.single())
        assertEquals(1, executions.transitionCallCount)
    }

    @Test
    fun recoveringFromAStaleConflictLeavesControlsUsableForTheNextAction() {
        val guides = FakeGuideRepository().withGuide().withRevision(simpleFourRoundDefinition())
        val executions = FakeExecutionRepository(guides)
        val execution = create(executions)
        val viewModel = viewModel(guides, executions)

        // Another caller completes the step behind the ViewModel's back,
        // then the ViewModel's own stale Complete attempt hits the conflict
        // and resyncs (see staleExecutionStateIsSurfacedAndUiResyncsWithoutSilentOverwrite).
        executions.complete(execution.state.executionId, 0)
        viewModel.onComplete()
        val afterResync = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertEquals(FocusFeedback.STALE_EXECUTION_STATE, afterResync.feedback)
        assertFalse(afterResync.isBusy)

        // Controls must be usable again: the next action is not blocked by
        // any leftover in-flight state and succeeds against the resynced version.
        viewModel.onComplete()

        val state = viewModel.uiState.value as GuideFocusUiState.InProgress
        assertNull(state.feedback)
        assertEquals(StructuralPosition.RangePosition("round", 3, 1, 4), state.positions.single())
    }

    private fun create(executions: FakeExecutionRepository): PersistedExecution {
        return runBlocking { executions.createExecution(guideId, revisionId) }
    }

    private fun completedRounds(
        executionId: ExecutionId,
        executions: FakeExecutionRepository
    ): Set<Int> {
        val execution = runBlocking { executions.loadExecution(executionId) }!!
        return execution.state.completedAddresses.map { address ->
            (address.ancestryFrames.single() as AncestryFrame.RangeValue).value
        }.toSet()
    }

    private fun roundAddress(value: Int) = ExecutionAddress(
        definitionRevisionId = revisionId,
        instructionNodeId = NodeId("instruction"),
        ancestryFrames = listOf(AncestryFrame.RangeValue(NodeId("range"), value))
    )

    private fun counter(id: String, projectId: String, currentValue: Int = 0) = Counter(
        id = id,
        projectId = projectId,
        name = id,
        unitLabel = "rows",
        currentValue = currentValue,
        goal = null,
        createdAt = 0,
        updatedAt = 0,
        linkedCounterId = null,
        linkIncrementInterval = null,
        linkIncrementAmount = null
    )

    private fun viewModel(
        guides: FakeGuideRepository,
        executions: FakeExecutionRepository,
        counters: FakeCounterRepository = FakeCounterRepository()
    ) = GuideFocusViewModel(
        guideId = guideId,
        guideRepository = guides,
        executionRepository = executions,
        counterRepository = counters,
        externalScope = scope
    )

    private fun simpleFourRoundDefinition(): GuideDefinition {
        return ExecutionEngineFixtures.definition(
            roots = listOf(NodeId("range")),
            Range(
                id = NodeId("range"),
                unitLabel = "round",
                startInclusive = 1,
                endInclusive = 4,
                children = listOf(NodeId("instruction"))
            ),
            Instruction(NodeId("instruction"), "Knit all stitches"),
            revisionId = revisionId
        )
    }
}

private class FakeGuideRepository : GuideRepository {
    private val guides = mutableMapOf<String, Guide>()
    private val revisions = mutableMapOf<String, MutableList<DefinitionRevision>>()
    var failNextRevisionLoadWith: Exception? = null

    fun withGuide(
        id: GuideId = ExecutionEngineFixtures.guideId,
        projectId: String = "project"
    ): FakeGuideRepository {
        guides[id.value] = Guide(
            id = id,
            projectId = projectId,
            name = "Guide",
            notes = null,
            createdAt = 0,
            updatedAt = 0
        )
        return this
    }

    fun withRevision(
        definition: GuideDefinition,
        id: DefinitionRevisionId = ExecutionEngineFixtures.revisionId,
        revisionNumber: Int = 1
    ): FakeGuideRepository {
        val revision = DefinitionRevision(
            id = id,
            guideId = definition.guideId,
            revisionNumber = revisionNumber,
            createdAt = 0,
            definition = definition
        )
        revisions.getOrPut(definition.guideId.value) { mutableListOf() }.add(revision)
        return this
    }

    override fun observeGuides(projectId: String): Flow<List<Guide>> =
        flowOf(guides.values.filter { it.projectId == projectId })

    override suspend fun getGuide(guideId: GuideId): Guide? = guides[guideId.value]

    override suspend fun createGuide(
        projectId: String,
        name: String,
        notes: String?
    ): Guide = throw UnsupportedOperationException("Not used by Focus Mode")

    override suspend fun updateGuideMetadata(
        guideId: GuideId,
        name: String,
        notes: String?
    ): Guide? = throw UnsupportedOperationException("Not used by Focus Mode")

    override suspend fun deleteGuide(guideId: GuideId) {
        guides.remove(guideId.value)
    }

    override suspend fun loadDraft(guideId: GuideId): GuideDraft? =
        throw UnsupportedOperationException("Not used by Focus Mode")

    override suspend fun saveDraft(draft: GuideDraft): GuideDraft =
        throw UnsupportedOperationException("Not used by Focus Mode")

    override suspend fun createDraftFromLatestRevision(guideId: GuideId): GuideDraft =
        throw UnsupportedOperationException("Not used by Focus Mode")

    override suspend fun listRevisions(guideId: GuideId): List<DefinitionRevision> =
        revisions[guideId.value].orEmpty().sortedBy { it.revisionNumber }

    override suspend fun loadRevision(revisionId: DefinitionRevisionId): DefinitionRevision? {
        failNextRevisionLoadWith?.let {
            failNextRevisionLoadWith = null
            throw it
        }
        return revisions.values.flatten().firstOrNull { it.id == revisionId }
    }

    override suspend fun getLatestRevision(guideId: GuideId): DefinitionRevision? {
        return revisions[guideId.value]?.maxByOrNull { it.revisionNumber }
    }

    override suspend fun publishDraft(guideId: GuideId): DefinitionRevision =
        throw UnsupportedOperationException("Not used by Focus Mode")
}

private class FakeExecutionRepository(
    private val guides: FakeGuideRepository
) : ExecutionRepository {

    private val executions = mutableMapOf<String, PersistedExecution>()
    private val activeByGuide = mutableMapOf<String, String>()
    private var nextId = 0
    var failNextTransitionWith: Exception? = null
    var createExecutionCallCount = 0
        private set
    var transitionCallCount = 0
        private set

    /**
     * When set, the next [createExecution] call suspends here before doing
     * anything else, then clears itself. Lets a test hold a Start call
     * in-flight (past its first real suspension point, under
     * [Dispatchers.Unconfined]) to exercise the ViewModel's own
     * re-entrancy guard against a second concurrent Start.
     */
    var pauseNextCreateUntil: CompletableDeferred<Unit>? = null

    /** Same idea as [pauseNextCreateUntil], but for Complete/Previous/Jump. */
    var pauseNextTransitionUntil: CompletableDeferred<Unit>? = null

    /** Test-only synchronous conveniences for arranging fixtures directly. */
    fun complete(executionId: ExecutionId, expectedVersion: Long) =
        runBlocking { applyComplete(executionId, expectedVersion) }

    fun jump(executionId: ExecutionId, expectedVersion: Long, target: ExecutionAddress) =
        runBlocking { applyJump(executionId, expectedVersion, target) }

    override suspend fun createExecution(
        guideId: GuideId,
        revisionId: DefinitionRevisionId
    ): PersistedExecution {
        pauseNextCreateUntil?.let {
            pauseNextCreateUntil = null
            it.await()
        }
        createExecutionCallCount++
        check(!activeByGuide.containsKey(guideId.value)) { "Guide already has an active execution" }
        val revision = checkNotNull(guides.loadRevision(revisionId))
        val engine = ExecutionEngine.forDefinition(revision.definition)
        val executionId = ExecutionId("exec-${nextId++}")
        val state = engine.newExecution(executionId)
        val persisted = PersistedExecution(
            state = state,
            version = 0,
            createdAt = 0,
            updatedAt = 0,
            completedAt = null
        )
        executions[executionId.value] = persisted
        activeByGuide[guideId.value] = executionId.value
        return persisted
    }

    override suspend fun loadExecution(executionId: ExecutionId): PersistedExecution? =
        executions[executionId.value]

    override suspend fun getActiveExecution(guideId: GuideId): PersistedExecution? =
        activeByGuide[guideId.value]?.let { executions[it] }

    override suspend fun listExecutions(guideId: GuideId): List<PersistedExecution> =
        executions.values.filter { it.state.guideId == guideId }

    override suspend fun applyComplete(
        executionId: ExecutionId,
        expectedVersion: Long
    ): PersistedExecutionTransitionResult = applyTransition(executionId, expectedVersion) { engine, state ->
        engine.complete(state)
    }

    override suspend fun applyPrevious(
        executionId: ExecutionId,
        expectedVersion: Long
    ): PersistedExecutionTransitionResult = applyTransition(executionId, expectedVersion) { engine, state ->
        engine.previous(state)
    }

    override suspend fun applyJump(
        executionId: ExecutionId,
        expectedVersion: Long,
        targetAddress: ExecutionAddress
    ): PersistedExecutionTransitionResult = applyTransition(executionId, expectedVersion) { engine, state ->
        engine.jump(state, targetAddress)
    }

    private suspend fun applyTransition(
        executionId: ExecutionId,
        expectedVersion: Long,
        transition: (ExecutionEngine, ExecutionState) -> ExecutionTransitionResult
    ): PersistedExecutionTransitionResult {
        pauseNextTransitionUntil?.let {
            pauseNextTransitionUntil = null
            it.await()
        }
        transitionCallCount++
        failNextTransitionWith?.let {
            failNextTransitionWith = null
            throw it
        }
        val current = checkNotNull(executions[executionId.value])
        if (current.version != expectedVersion) throw ExecutionVersionConflictException(executionId)

        val revision = checkNotNull(guides.loadRevision(current.state.definitionRevisionId))
        val engine = ExecutionEngine.forDefinition(revision.definition)
        return when (val result = transition(engine, current.state)) {
            is ExecutionTransitionResult.NoChange ->
                PersistedExecutionTransitionResult.NoChange(current, result.reason)

            is ExecutionTransitionResult.Changed -> {
                val updated = current.copy(
                    state = result.state,
                    version = current.version + 1,
                    completedAt = if (result.state.status == ExecutionStatus.COMPLETED) 0L else null
                )
                executions[executionId.value] = updated
                if (result.state.status == ExecutionStatus.COMPLETED) {
                    activeByGuide.remove(current.state.guideId.value)
                }
                PersistedExecutionTransitionResult.Changed(updated)
            }
        }
    }
}

private class FakeCounterRepository(initial: List<Counter> = emptyList()) : CounterRepository {
    val counters = MutableStateFlow(initial)

    override fun observeCounters(): Flow<List<Counter>> = counters

    override fun observeCountersByProject(projectId: String): Flow<List<Counter>> =
        counters.map { all -> all.filter { it.projectId == projectId } }

    override fun observeCounter(id: String): Flow<Counter?> =
        counters.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun saveCounter(counter: Counter) {
        counters.value = counters.value.filterNot { it.id == counter.id } + counter
    }

    override suspend fun incrementCounterValue(id: String, amount: Int, updatedAt: Long) {
        counters.value = counters.value.map {
            if (it.id == id) it.copy(currentValue = it.currentValue + amount, updatedAt = updatedAt) else it
        }
    }

    override suspend fun deleteCounter(counter: Counter) {
        counters.value = counters.value.filterNot { it.id == counter.id }
    }
}
