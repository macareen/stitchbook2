package com.macareen.stitchbook2.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.macareen.stitchbook2.data.repository.LocalCounterRepository
import com.macareen.stitchbook2.domain.usecase.IncrementCounterUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CounterDaoTest {

    private lateinit var database: StitchbookDatabase
    private lateinit var dao: CounterDao

    @Before
    fun createDatabase() {
        val context: Context =
            InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            StitchbookDatabase::class.java
        ).build()
        dao = database.counterDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test fun rapidStaleTapsUseStoredRulesAndPreserveMetadata() = runBlocking {
        val original = counterEntity("source")
        dao.upsert(counterEntity("target"))
        dao.upsert(original.copy(name = "Edited", goal = 4, autoResetOnGoal = true,
            linkedCounterId = "target", linkIncrementInterval = 4, linkIncrementAmount = 2))
        val useCase = IncrementCounterUseCase(LocalCounterRepository(dao))
        (1..100).map { async { useCase(original.toDomain()) } }.awaitAll()
        assertEquals(0, dao.getById("source")?.currentValue)
        assertEquals("Edited", dao.getById("source")?.name)
        assertEquals(50, dao.getById("target")?.currentValue)
    }

    @Test fun sharedLinkTargetsRetainBothSourcesIncrements() = runBlocking {
        dao.upsert(counterEntity("target"))
        listOf("a", "b").forEach { dao.upsert(counterEntity(it, linkedCounterId = "target",
            linkIncrementInterval = 1, linkIncrementAmount = 1)) }
        (1..100).map { async { dao.incrementWithRules(if (it % 2 == 0) "a" else "b", 300) } }.awaitAll()
        assertEquals(50, dao.getById("a")?.currentValue)
        assertEquals(50, dao.getById("b")?.currentValue)
        assertEquals(100, dao.getById("target")?.currentValue)
    }

    @Test fun failureAfterTargetWriteRollsBackBothCounters() = runBlocking {
        dao.upsert(counterEntity("target"))
        dao.upsert(counterEntity("source", linkedCounterId = "target", linkIncrementInterval = 1, linkIncrementAmount = 1))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_source BEFORE UPDATE ON counters WHEN NEW.id = 'source' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        var failed = false
        try { dao.incrementWithRules("source", 300) } catch (_: Exception) { failed = true }
        assertEquals(true, failed)
        assertEquals(0, dao.getById("source")?.currentValue)
        assertEquals(0, dao.getById("target")?.currentValue)
    }

    @Test fun targetOverflowLeavesTheSourceUnchanged() = runBlocking {
        dao.upsert(counterEntity("target", currentValue = Int.MAX_VALUE))
        dao.upsert(counterEntity("source", linkedCounterId = "target", linkIncrementInterval = 1, linkIncrementAmount = 1))
        var failed = false
        try { dao.incrementWithRules("source", 300) } catch (_: ArithmeticException) { failed = true }
        assertEquals(true, failed)
        assertEquals(0, dao.getById("source")?.currentValue)
        assertEquals(Int.MAX_VALUE, dao.getById("target")?.currentValue)
    }

    @Test fun missingSourceIsNotRecreated() = runBlocking {
        dao.incrementWithRules("missing", 300)
        assertNull(dao.getById("missing"))
    }

    @Test
    fun insertAndObserveCounter() = runBlocking {
        val counter = counterEntity(id = "inserted")
        val initialEmissionReceived = CompletableDeferred<Unit>()
        val observedValues = async {
            dao.observeById(counter.id)
                .onEach { initialEmissionReceived.complete(Unit) }
                .take(2)
                .toList()
        }

        initialEmissionReceived.await()
        dao.upsert(counter)

        assertEquals(listOf(null, counter), observedValues.await())
        assertEquals(listOf(counter), dao.observeAll().first())
    }

    @Test
    fun updateCounter() = runBlocking {
        val original = counterEntity(id = "updated", currentValue = 0)
        dao.upsert(original)

        val updated = original.copy(currentValue = 5, updatedAt = 200)
        dao.upsert(updated)

        assertEquals(updated, dao.observeById(updated.id).first())
    }

    @Test
    fun deleteCounter() = runBlocking {
        val counter = counterEntity(id = "deleted")
        dao.upsert(counter)

        dao.delete(counter)

        assertNull(dao.observeById(counter.id).first())
        assertEquals(emptyList<CounterEntity>(), dao.observeAll().first())
    }

    @Test
    fun countersAreOrderedByMostRecentlyUpdated() = runBlocking {
        val counters = listOf(
            counterEntity(id = "old", updatedAt = 100),
            counterEntity(id = "newest", updatedAt = 300),
            counterEntity(id = "middle", updatedAt = 200)
        )
        counters.forEach { dao.upsert(it) }

        val orderedIds = dao.observeAll().first().map { it.id }

        assertEquals(listOf("newest", "middle", "old"), orderedIds)
    }

    @Test
    fun observeByProjectIdReturnsOnlyThatProjectsCountersNotStandaloneOnes() = runBlocking {
        database.projectDao().upsert(projectEntity(id = "project-1"))
        database.projectDao().upsert(projectEntity(id = "project-2"))

        val ownedByOne = counterEntity(id = "owned-by-one", projectId = "project-1")
        val ownedByTwo = counterEntity(id = "owned-by-two", projectId = "project-2")
        val standalone = counterEntity(id = "standalone", projectId = null)
        dao.upsert(ownedByOne)
        dao.upsert(ownedByTwo)
        dao.upsert(standalone)

        val countersForProjectOne = dao.observeByProjectId("project-1").first()

        assertEquals(listOf(ownedByOne), countersForProjectOne)
    }

    @Test
    fun deletingAProjectCascadesToItsCountersButNotStandaloneOnes() = runBlocking {
        database.projectDao().upsert(projectEntity(id = "project-1"))
        val owned = counterEntity(id = "owned", projectId = "project-1")
        val standalone = counterEntity(id = "standalone", projectId = null)
        dao.upsert(owned)
        dao.upsert(standalone)

        database.projectDao().delete(projectEntity(id = "project-1"))

        assertEquals(listOf(standalone), dao.observeAll().first())
    }

    @Test
    fun incrementValueAddsToTheCurrentValueWithoutARead() = runBlocking {
        val counter = counterEntity(id = "target", currentValue = 10)
        dao.upsert(counter)

        dao.incrementValue(id = "target", amount = 3, updatedAt = 999)

        val updated = dao.observeById("target").first()
        assertEquals(13, updated?.currentValue)
        assertEquals(999L, updated?.updatedAt)
    }

    @Test
    fun incrementValueOnAMissingCounterIsANoOp() = runBlocking {
        dao.incrementValue(id = "does-not-exist", amount = 5, updatedAt = 100)

        assertEquals(emptyList<CounterEntity>(), dao.observeAll().first())
    }

    @Test
    fun deletingTheLinkedCounterClearsTheLinkButNotTheLinkingCounter() = runBlocking {
        val target = counterEntity(id = "target")
        val source = counterEntity(
            id = "source",
            linkedCounterId = "target",
            linkIncrementInterval = 4,
            linkIncrementAmount = 1
        )
        dao.upsert(target)
        dao.upsert(source)

        dao.delete(target)

        val survivingSource = dao.observeById("source").first()
        assertEquals("source", survivingSource?.id)
        assertNull(survivingSource?.linkedCounterId)
        assertNull(survivingSource?.linkIncrementInterval)
        assertNull(survivingSource?.linkIncrementAmount)
    }
}

private fun counterEntity(
    id: String,
    projectId: String? = null,
    name: String = "Counter",
    unitLabel: String = "rows",
    currentValue: Int = 0,
    goal: Int? = null,
    updatedAt: Long = 100,
    linkedCounterId: String? = null,
    linkIncrementInterval: Int? = null,
    linkIncrementAmount: Int? = null,
    autoResetOnGoal: Boolean = false,
    repeatIntervalDays: Int? = null,
    lastRepeatResetAt: Long? = null
) = CounterEntity(
    id = id,
    projectId = projectId,
    name = name,
    unitLabel = unitLabel,
    currentValue = currentValue,
    goal = goal,
    createdAt = 50,
    updatedAt = updatedAt,
    linkedCounterId = linkedCounterId,
    linkIncrementInterval = linkIncrementInterval,
    linkIncrementAmount = linkIncrementAmount,
    autoResetOnGoal = autoResetOnGoal,
    repeatIntervalDays = repeatIntervalDays,
    lastRepeatResetAt = lastRepeatResetAt
)

private fun projectEntity(id: String) = ProjectEntity(
    id = id,
    name = "Project",
    craft = "KNITTING",
    projectType = "OTHER",
    status = "ACTIVE",
    notes = null,
    createdAt = 50,
    updatedAt = 100
)
