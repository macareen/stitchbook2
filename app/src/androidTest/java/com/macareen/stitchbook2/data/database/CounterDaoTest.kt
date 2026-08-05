package com.macareen.stitchbook2.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
}

private fun counterEntity(
    id: String,
    projectId: String? = null,
    name: String = "Counter",
    unitLabel: String = "rows",
    currentValue: Int = 0,
    goal: Int? = null,
    updatedAt: Long = 100
) = CounterEntity(
    id = id,
    projectId = projectId,
    name = name,
    unitLabel = unitLabel,
    currentValue = currentValue,
    goal = goal,
    createdAt = 50,
    updatedAt = updatedAt
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
