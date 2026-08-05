package com.macareen.stitchbook2.domain.usecase

import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.repository.CounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementCounterUseCaseTest {

    @Test
    fun incrementsTheCounterByOne() = runBlocking {
        val repository = FakeCounterRepository(listOf(counter("counter", currentValue = 5)))
        val useCase = IncrementCounterUseCase(repository)

        useCase(repository.counters.value.single())

        assertEquals(6, repository.counters.value.single().currentValue)
    }

    @Test
    fun doesNotTriggerTheLinkedActionBeforeTheIntervalIsReached() = runBlocking {
        val source = counter("source", currentValue = 2, linkedCounterId = "target", linkIncrementInterval = 4, linkIncrementAmount = 1)
        val target = counter("target", currentValue = 0)
        val repository = FakeCounterRepository(listOf(source, target))
        val useCase = IncrementCounterUseCase(repository)

        useCase(source)

        assertEquals(3, repository.counters.value.first { it.id == "source" }.currentValue)
        assertEquals(0, repository.counters.value.first { it.id == "target" }.currentValue)
    }

    @Test
    fun triggersTheLinkedActionOnceTheIntervalIsReached() = runBlocking {
        val source = counter("source", currentValue = 3, linkedCounterId = "target", linkIncrementInterval = 4, linkIncrementAmount = 2)
        val target = counter("target", currentValue = 10)
        val repository = FakeCounterRepository(listOf(source, target))
        val useCase = IncrementCounterUseCase(repository)

        useCase(source)

        assertEquals(4, repository.counters.value.first { it.id == "source" }.currentValue)
        assertEquals(12, repository.counters.value.first { it.id == "target" }.currentValue)
    }

    @Test
    fun resetsToZeroWhenGoalIsReachedAndAutoResetIsEnabled() = runBlocking {
        val existing = counter("counter", currentValue = 7, goal = 8, autoResetOnGoal = true)
        val repository = FakeCounterRepository(listOf(existing))
        val useCase = IncrementCounterUseCase(repository)

        useCase(existing)

        assertEquals(0, repository.counters.value.single().currentValue)
    }

    @Test
    fun persistsEvenWhenAutoResetLandsBackOnTheOriginalValue() = runBlocking {
        // goal=1, autoResetOnGoal=true, starting at 0: newValue=1 reaches
        // the goal, so finalValue resets to 0 -- the same as the original
        // currentValue. This must still be persisted (updatedAt changes,
        // and it's a real user action), not silently skipped as a no-op.
        val existing = counter("counter", currentValue = 0, goal = 1, autoResetOnGoal = true)
        val repository = FakeCounterRepository(listOf(existing))
        val useCase = IncrementCounterUseCase(repository)
        val before = existing.updatedAt

        useCase(existing)

        val saved = repository.counters.value.single()
        assertEquals(0, saved.currentValue)
        assertEquals(true, saved.updatedAt > before)
    }

    @Test
    fun doesNotResetWhenGoalIsReachedButAutoResetIsDisabled() = runBlocking {
        val existing = counter("counter", currentValue = 7, goal = 8, autoResetOnGoal = false)
        val repository = FakeCounterRepository(listOf(existing))
        val useCase = IncrementCounterUseCase(repository)

        useCase(existing)

        assertEquals(8, repository.counters.value.single().currentValue)
    }

    @Test
    fun canBothTriggerALinkAndAutoResetOnTheSameStep() = runBlocking {
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
        val useCase = IncrementCounterUseCase(repository)

        useCase(source)

        assertEquals(0, repository.counters.value.first { it.id == "source" }.currentValue)
        assertEquals(1, repository.counters.value.first { it.id == "target" }.currentValue)
    }

    private fun counter(
        id: String,
        currentValue: Int = 0,
        goal: Int? = null,
        autoResetOnGoal: Boolean = false,
        linkedCounterId: String? = null,
        linkIncrementInterval: Int? = null,
        linkIncrementAmount: Int? = null
    ) = Counter(
        id = id,
        projectId = null,
        name = id,
        unitLabel = "rows",
        currentValue = currentValue,
        goal = goal,
        createdAt = 0,
        updatedAt = 0,
        linkedCounterId = linkedCounterId,
        linkIncrementInterval = linkIncrementInterval,
        linkIncrementAmount = linkIncrementAmount,
        autoResetOnGoal = autoResetOnGoal
    )
}

private class FakeCounterRepository(initial: List<Counter>) : CounterRepository {
    val counters = MutableStateFlow(initial)

    override fun observeCounters(): Flow<List<Counter>> = counters

    override fun observeCountersByProject(projectId: String): Flow<List<Counter>> =
        throw UnsupportedOperationException("Not used by IncrementCounterUseCase")

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
