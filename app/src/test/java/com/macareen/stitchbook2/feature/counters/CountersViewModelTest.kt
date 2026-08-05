package com.macareen.stitchbook2.feature.counters

import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.model.CounterNote
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.repository.CounterNoteRepository
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the filtering, save-normalization, and increment/decrement/reset
 * behavior [CountersViewModel] adds on top of persisted repository state.
 */
class CountersViewModelTest {

    // Dispatchers.Unconfined runs launched coroutines synchronously up to
    // their first real suspension point. The fakes below have no real
    // suspension, so uiState settles before the constructor call returns --
    // no manual idling needed.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun searchQueryMatchesNameOrUnitLabel() {
        val repository = FakeCounterRepository(
            listOf(
                counter("sleeve", name = "Right Sleeve", unitLabel = "rows"),
                counter("cable", name = "Cable Repeat", unitLabel = "repeats")
            )
        )
        val viewModel = viewModel(repository)

        viewModel.updateSearchQuery("row")

        assertEquals(listOf("sleeve"), contentState(viewModel).entries.map { it.counter.id })
    }

    @Test
    fun entriesResolveTheirOwningProjectsName() {
        val counterRepository = FakeCounterRepository(
            listOf(
                counter("owned", projectId = "project-1"),
                counter("standalone", projectId = null)
            )
        )
        val projectRepository = FakeProjectRepository(listOf(project("project-1", "Everyday Cardigan")))
        val viewModel = viewModel(counterRepository, projectRepository)

        val entriesById = contentState(viewModel).entries.associateBy { it.counter.id }

        assertEquals("Everyday Cardigan", entriesById.getValue("owned").projectName)
        assertNull(entriesById.getValue("standalone").projectName)
    }

    @Test
    fun savingWithABlankNameIsANoOp() {
        val repository = FakeCounterRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveCounter(
            null,
            CounterFormInput(name = "   ", unitLabel = "rows", goalText = "", projectId = null)
        )

        assertTrue(repository.counters.value.isEmpty())
    }

    @Test
    fun savingWithABlankUnitLabelIsANoOp() {
        val repository = FakeCounterRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveCounter(
            null,
            CounterFormInput(name = "Right Sleeve", unitLabel = "   ", goalText = "", projectId = null)
        )

        assertTrue(repository.counters.value.isEmpty())
    }

    @Test
    fun savingANewCounterStartsAtZeroAndParsesAPositiveGoal() {
        val repository = FakeCounterRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveCounter(
            null,
            CounterFormInput(name = "Right Sleeve", unitLabel = "rows", goalText = "60", projectId = "project-1")
        )

        val saved = repository.counters.value.single()
        assertEquals(0, saved.currentValue)
        assertEquals(60, saved.goal)
        assertEquals("project-1", saved.projectId)
    }

    @Test
    fun aGoalOfZeroOrBlankIsTreatedAsNoGoal() {
        val repository = FakeCounterRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveCounter(null, CounterFormInput("A", "rows", goalText = "0", projectId = null))
        viewModel.saveCounter(null, CounterFormInput("B", "rows", goalText = "", projectId = null))
        viewModel.saveCounter(null, CounterFormInput("C", "rows", goalText = "not a number", projectId = null))

        assertTrue(repository.counters.value.all { it.goal == null })
    }

    @Test
    fun editingAnExistingCounterPreservesItsCurrentValueAndCreatedAt() {
        val existing = counter("existing", currentValue = 12, createdAt = 100)
        val repository = FakeCounterRepository(listOf(existing))
        val viewModel = viewModel(repository)

        viewModel.saveCounter(
            existing,
            CounterFormInput(name = "Renamed", unitLabel = "rounds", goalText = "", projectId = null)
        )

        val saved = repository.counters.value.single()
        assertEquals("Renamed", saved.name)
        assertEquals("rounds", saved.unitLabel)
        assertEquals(12, saved.currentValue)
        assertEquals(100L, saved.createdAt)
    }

    @Test
    fun incrementIncreasesTheCurrentValueByOne() {
        val existing = counter("counter", currentValue = 5)
        val repository = FakeCounterRepository(listOf(existing))
        val viewModel = viewModel(repository)

        viewModel.increment(existing)

        assertEquals(6, repository.counters.value.single().currentValue)
    }

    @Test
    fun decrementDecreasesTheCurrentValueByOneButNeverBelowZero() {
        val existing = counter("counter", currentValue = 0)
        val repository = FakeCounterRepository(listOf(existing))
        val viewModel = viewModel(repository)

        viewModel.decrement(existing)

        assertEquals(0, repository.counters.value.single().currentValue)
    }

    @Test
    fun resetSetsTheCurrentValueBackToZero() {
        val existing = counter("counter", currentValue = 42)
        val repository = FakeCounterRepository(listOf(existing))
        val viewModel = viewModel(repository)

        viewModel.reset(existing)

        assertEquals(0, repository.counters.value.single().currentValue)
    }

    @Test
    fun deletingACounterRemovesItFromTheRepository() {
        val repository = FakeCounterRepository(listOf(counter("to-delete")))
        val viewModel = viewModel(repository)

        viewModel.deleteCounter(repository.counters.value.single())

        assertTrue(repository.counters.value.isEmpty())
    }

    @Test
    fun savingAValidLinkPersistsItsIntervalAndAmount() {
        val target = counter("target")
        val repository = FakeCounterRepository(listOf(target))
        val viewModel = viewModel(repository)

        viewModel.saveCounter(
            null,
            CounterFormInput(
                name = "Source",
                unitLabel = "rows",
                goalText = "",
                projectId = null,
                linkedCounterId = "target",
                linkIntervalText = "4",
                linkAmountText = "1"
            )
        )

        val saved = repository.counters.value.single { it.name == "Source" }
        assertEquals("target", saved.linkedCounterId)
        assertEquals(4, saved.linkIncrementInterval)
        assertEquals(1, saved.linkIncrementAmount)
    }

    @Test
    fun savingWithANonPositiveIntervalOrAmountDropsTheLink() {
        val target = counter("target")
        val repository = FakeCounterRepository(listOf(target))
        val viewModel = viewModel(repository)

        viewModel.saveCounter(
            null,
            CounterFormInput(
                name = "Bad interval",
                unitLabel = "rows",
                goalText = "",
                projectId = null,
                linkedCounterId = "target",
                linkIntervalText = "0",
                linkAmountText = "1"
            )
        )
        viewModel.saveCounter(
            null,
            CounterFormInput(
                name = "Bad amount",
                unitLabel = "rows",
                goalText = "",
                projectId = null,
                linkedCounterId = "target",
                linkIntervalText = "4",
                linkAmountText = "not a number"
            )
        )

        val saved = repository.counters.value.filter { it.name != "target" }
        assertTrue(saved.all { it.linkedCounterId == null })
    }

    @Test
    fun savingALinkThatWouldCreateACycleDropsTheLink() {
        // b already links to a; saving a with a link to b would complete a cycle.
        val a = counter("a")
        val b = counter("b", linkedCounterId = "a", linkIncrementInterval = 1, linkIncrementAmount = 1)
        val repository = FakeCounterRepository(listOf(a, b))
        val viewModel = viewModel(repository)

        viewModel.saveCounter(
            a,
            CounterFormInput(
                name = "a",
                unitLabel = "rows",
                goalText = "",
                projectId = null,
                linkedCounterId = "b",
                linkIntervalText = "1",
                linkAmountText = "1"
            )
        )

        val savedA = repository.counters.value.first { it.id == "a" }
        assertEquals(null, savedA.linkedCounterId)
    }

    @Test
    fun incrementDoesNotTriggerTheLinkedActionBeforeTheIntervalIsReached() {
        val source = counter("source", currentValue = 2, linkedCounterId = "target", linkIncrementInterval = 4, linkIncrementAmount = 1)
        val target = counter("target", currentValue = 0)
        val repository = FakeCounterRepository(listOf(source, target))
        val viewModel = viewModel(repository)

        viewModel.increment(source)

        assertEquals(3, repository.counters.value.first { it.id == "source" }.currentValue)
        assertEquals(0, repository.counters.value.first { it.id == "target" }.currentValue)
    }

    @Test
    fun incrementTriggersTheLinkedActionOnceTheIntervalIsReached() {
        val source = counter("source", currentValue = 3, linkedCounterId = "target", linkIncrementInterval = 4, linkIncrementAmount = 2)
        val target = counter("target", currentValue = 10)
        val repository = FakeCounterRepository(listOf(source, target))
        val viewModel = viewModel(repository)

        viewModel.increment(source)

        assertEquals(4, repository.counters.value.first { it.id == "source" }.currentValue)
        assertEquals(12, repository.counters.value.first { it.id == "target" }.currentValue)
    }

    @Test
    fun decrementNeverTriggersTheLinkedAction() {
        val source = counter("source", currentValue = 4, linkedCounterId = "target", linkIncrementInterval = 4, linkIncrementAmount = 1)
        val target = counter("target", currentValue = 0)
        val repository = FakeCounterRepository(listOf(source, target))
        val viewModel = viewModel(repository)

        viewModel.decrement(source)

        assertEquals(0, repository.counters.value.first { it.id == "target" }.currentValue)
    }

    @Test
    fun resetNeverTriggersTheLinkedAction() {
        val source = counter("source", currentValue = 8, linkedCounterId = "target", linkIncrementInterval = 4, linkIncrementAmount = 1)
        val target = counter("target", currentValue = 0)
        val repository = FakeCounterRepository(listOf(source, target))
        val viewModel = viewModel(repository)

        viewModel.reset(source)

        assertEquals(0, repository.counters.value.first { it.id == "target" }.currentValue)
    }

    @Test
    fun incrementResetsToZeroWhenGoalIsReachedAndAutoResetIsEnabled() {
        val existing = counter("counter", currentValue = 7, goal = 8, autoResetOnGoal = true)
        val repository = FakeCounterRepository(listOf(existing))
        val viewModel = viewModel(repository)

        viewModel.increment(existing)

        assertEquals(0, repository.counters.value.single().currentValue)
    }

    @Test
    fun incrementDoesNotResetWhenGoalIsReachedButAutoResetIsDisabled() {
        val existing = counter("counter", currentValue = 7, goal = 8, autoResetOnGoal = false)
        val repository = FakeCounterRepository(listOf(existing))
        val viewModel = viewModel(repository)

        viewModel.increment(existing)

        assertEquals(8, repository.counters.value.single().currentValue)
    }

    @Test
    fun incrementDoesNotResetBeforeTheGoalIsReachedEvenWithAutoResetEnabled() {
        val existing = counter("counter", currentValue = 3, goal = 8, autoResetOnGoal = true)
        val repository = FakeCounterRepository(listOf(existing))
        val viewModel = viewModel(repository)

        viewModel.increment(existing)

        assertEquals(4, repository.counters.value.single().currentValue)
    }

    @Test
    fun incrementCanBothTriggerALinkAndAutoResetOnTheSameStep() {
        val source = counter(
            "source",
            currentValue = 3,
            goal = 4,
            autoResetOnGoal = true,
            linkedCounterId = "target",
            linkIncrementInterval = 4,
            linkIncrementAmount = 1
        )
        val target = counter("target", currentValue = 0)
        val repository = FakeCounterRepository(listOf(source, target))
        val viewModel = viewModel(repository)

        viewModel.increment(source)

        assertEquals(0, repository.counters.value.first { it.id == "source" }.currentValue)
        assertEquals(1, repository.counters.value.first { it.id == "target" }.currentValue)
    }

    @Test
    fun savingWithAutoResetButNoGoalDropsAutoReset() {
        val repository = FakeCounterRepository(emptyList())
        val viewModel = viewModel(repository)

        viewModel.saveCounter(
            null,
            CounterFormInput(
                name = "No goal",
                unitLabel = "rows",
                goalText = "",
                projectId = null,
                autoResetOnGoal = true
            )
        )

        assertEquals(false, repository.counters.value.single().autoResetOnGoal)
    }

    @Test
    fun notesUiStateStartsClosed() {
        val viewModel = viewModel(FakeCounterRepository(emptyList()))

        assertEquals(CounterNotesUiState.Closed, viewModel.notesUiState.value)
    }

    @Test
    fun openNotesShowsOnlyThatCountersNotes() {
        val sleeve = counter("sleeve")
        val cable = counter("cable")
        val noteRepository = FakeCounterNoteRepository(
            listOf(
                counterNote("sleeve-note", counterId = "sleeve"),
                counterNote("cable-note", counterId = "cable")
            )
        )
        val viewModel = viewModel(FakeCounterRepository(listOf(sleeve, cable)), noteRepository = noteRepository)

        viewModel.openNotes(sleeve)

        val content = viewModel.notesUiState.value as CounterNotesUiState.Content
        assertEquals(sleeve, content.counter)
        assertEquals(listOf("sleeve-note"), content.notes.map { it.id })
    }

    @Test
    fun closeNotesReturnsToClosed() {
        val existing = counter("counter")
        val viewModel = viewModel(FakeCounterRepository(listOf(existing)))
        viewModel.openNotes(existing)

        viewModel.closeNotes()

        assertEquals(CounterNotesUiState.Closed, viewModel.notesUiState.value)
    }

    @Test
    fun addNoteSavesATrimmedNoteAgainstTheGivenCounterAndValue() {
        val existing = counter("counter")
        val noteRepository = FakeCounterNoteRepository(emptyList())
        val viewModel = viewModel(FakeCounterRepository(listOf(existing)), noteRepository = noteRepository)

        viewModel.addNote(existing, 42, "  Switched needles.  ")

        val saved = noteRepository.notes.value.single()
        assertEquals("counter", saved.counterId)
        assertEquals(42, saved.value)
        assertEquals("Switched needles.", saved.note)
    }

    @Test
    fun addNoteWithBlankTextIsANoOp() {
        val existing = counter("counter")
        val noteRepository = FakeCounterNoteRepository(emptyList())
        val viewModel = viewModel(FakeCounterRepository(listOf(existing)), noteRepository = noteRepository)

        viewModel.addNote(existing, 42, "   ")

        assertTrue(noteRepository.notes.value.isEmpty())
    }

    @Test
    fun deleteNoteRemovesItFromTheRepository() {
        val noteRepository = FakeCounterNoteRepository(listOf(counterNote("note", counterId = "counter")))
        val viewModel = viewModel(FakeCounterRepository(listOf(counter("counter"))), noteRepository = noteRepository)

        viewModel.deleteNote(noteRepository.notes.value.single())

        assertTrue(noteRepository.notes.value.isEmpty())
    }

    private fun counterNote(id: String, counterId: String, value: Int = 0, note: String = "Note") = CounterNote(
        id = id,
        counterId = counterId,
        value = value,
        note = note,
        createdAt = 0
    )

    private fun contentState(viewModel: CountersViewModel): CountersUiState.Content {
        return viewModel.uiState.value as CountersUiState.Content
    }

    private fun counter(
        id: String,
        name: String = id,
        unitLabel: String = "rows",
        projectId: String? = null,
        currentValue: Int = 0,
        goal: Int? = null,
        createdAt: Long = 0,
        linkedCounterId: String? = null,
        linkIncrementInterval: Int? = null,
        linkIncrementAmount: Int? = null,
        autoResetOnGoal: Boolean = false
    ) = Counter(
        id = id,
        projectId = projectId,
        name = name,
        unitLabel = unitLabel,
        currentValue = currentValue,
        goal = goal,
        createdAt = createdAt,
        updatedAt = 0,
        linkedCounterId = linkedCounterId,
        linkIncrementInterval = linkIncrementInterval,
        linkIncrementAmount = linkIncrementAmount,
        autoResetOnGoal = autoResetOnGoal
    )

    private fun project(id: String, name: String) = Project(
        id = id,
        name = name,
        craft = Craft.KNITTING,
        projectType = ProjectType.OTHER,
        status = ProjectStatus.ACTIVE,
        notes = null,
        createdAt = 0,
        updatedAt = 0
    )

    private fun viewModel(
        counterRepository: FakeCounterRepository,
        projectRepository: FakeProjectRepository = FakeProjectRepository(emptyList()),
        noteRepository: FakeCounterNoteRepository = FakeCounterNoteRepository(emptyList())
    ): CountersViewModel {
        val viewModel = CountersViewModel(counterRepository, projectRepository, noteRepository, externalScope = scope)
        // uiState/notesUiState are built with SharingStarted.WhileSubscribed,
        // so each only starts (and its value only advances past its initial
        // state) once it has an active collector. Under Dispatchers.Unconfined
        // this collection runs synchronously through the fakes' non-suspending
        // emissions before this call returns.
        scope.launch { viewModel.uiState.collect {} }
        scope.launch { viewModel.notesUiState.collect {} }
        return viewModel
    }
}

private class FakeCounterRepository(initial: List<Counter>) : CounterRepository {
    val counters = MutableStateFlow(initial)

    override fun observeCounters(): Flow<List<Counter>> = counters

    override fun observeCountersByProject(projectId: String): Flow<List<Counter>> =
        throw UnsupportedOperationException("Not used by CountersViewModel")

    override fun observeCounter(id: String): Flow<Counter?> {
        return counters.map { all -> all.firstOrNull { it.id == id } }
    }

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

private class FakeProjectRepository(initial: List<Project>) : ProjectRepository {
    private val projects = MutableStateFlow(initial)

    override fun observeProjects(): Flow<List<Project>> = projects

    override fun observeProject(id: String): Flow<Project?> =
        throw UnsupportedOperationException("Not used by CountersViewModel")

    override suspend fun saveProject(project: Project): Unit =
        throw UnsupportedOperationException("Not used by CountersViewModel")

    override suspend fun deleteProject(project: Project): Unit =
        throw UnsupportedOperationException("Not used by CountersViewModel")
}

private class FakeCounterNoteRepository(initial: List<CounterNote>) : CounterNoteRepository {
    val notes = MutableStateFlow(initial)

    override fun observeNotes(): Flow<List<CounterNote>> = notes

    override fun observeNotesByCounter(counterId: String): Flow<List<CounterNote>> {
        return notes.map { all -> all.filter { it.counterId == counterId } }
    }

    override suspend fun saveNote(note: CounterNote) {
        notes.value = notes.value.filterNot { it.id == note.id } + note
    }

    override suspend fun deleteNote(note: CounterNote) {
        notes.value = notes.value.filterNot { it.id == note.id }
    }
}
