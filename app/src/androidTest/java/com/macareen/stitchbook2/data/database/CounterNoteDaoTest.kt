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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CounterNoteDaoTest {

    private lateinit var database: StitchbookDatabase
    private lateinit var dao: CounterNoteDao

    @Before
    fun createDatabase() = runBlocking {
        val context: Context =
            InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            StitchbookDatabase::class.java
        ).build()
        dao = database.counterNoteDao()
        database.counterDao().upsert(counterEntity(id = "counter-1"))
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndObserveNote() = runBlocking {
        val note = counterNoteEntity(id = "inserted")
        val initialEmissionReceived = CompletableDeferred<Unit>()
        val observedValues = async {
            dao.observeByCounterId(note.counterId)
                .onEach { initialEmissionReceived.complete(Unit) }
                .take(2)
                .toList()
        }

        initialEmissionReceived.await()
        dao.upsert(note)

        assertEquals(listOf(emptyList(), listOf(note)), observedValues.await())
        assertEquals(listOf(note), dao.observeAll().first())
    }

    @Test
    fun updateNote() = runBlocking {
        val original = counterNoteEntity(id = "updated", note = "Original note")
        dao.upsert(original)

        val updated = original.copy(note = "Revised note")
        dao.upsert(updated)

        assertEquals(listOf(updated), dao.observeByCounterId(original.counterId).first())
    }

    @Test
    fun deleteNote() = runBlocking {
        val note = counterNoteEntity(id = "deleted")
        dao.upsert(note)

        dao.delete(note)

        assertEquals(emptyList<CounterNoteEntity>(), dao.observeAll().first())
    }

    @Test
    fun observeByCounterIdReturnsOnlyThatCountersNotes() = runBlocking {
        database.counterDao().upsert(counterEntity(id = "counter-2"))
        val ownedByOne = counterNoteEntity(id = "owned-by-one", counterId = "counter-1")
        val ownedByTwo = counterNoteEntity(id = "owned-by-two", counterId = "counter-2")
        dao.upsert(ownedByOne)
        dao.upsert(ownedByTwo)

        val notesForCounterOne = dao.observeByCounterId("counter-1").first()

        assertEquals(listOf(ownedByOne), notesForCounterOne)
    }

    @Test
    fun notesAreOrderedByMostRecentlyCreated() = runBlocking {
        val notes = listOf(
            counterNoteEntity(id = "old", createdAt = 100),
            counterNoteEntity(id = "newest", createdAt = 300),
            counterNoteEntity(id = "middle", createdAt = 200)
        )
        notes.forEach { dao.upsert(it) }

        val orderedIds = dao.observeByCounterId("counter-1").first().map { it.id }

        assertEquals(listOf("newest", "middle", "old"), orderedIds)
    }

    @Test
    fun deletingACounterCascadesToItsNotes() = runBlocking {
        val note = counterNoteEntity(id = "note", counterId = "counter-1")
        dao.upsert(note)

        database.counterDao().delete(counterEntity(id = "counter-1"))

        assertEquals(emptyList<CounterNoteEntity>(), dao.observeAll().first())
    }
}

private fun counterNoteEntity(
    id: String,
    counterId: String = "counter-1",
    value: Int = 0,
    note: String = "Note",
    createdAt: Long = 100
) = CounterNoteEntity(
    id = id,
    counterId = counterId,
    value = value,
    note = note,
    createdAt = createdAt
)

private fun counterEntity(id: String) = CounterEntity(
    id = id,
    projectId = null,
    name = "Counter",
    unitLabel = "rows",
    currentValue = 0,
    goal = null,
    createdAt = 50,
    updatedAt = 100,
    linkedCounterId = null,
    linkIncrementInterval = null,
    linkIncrementAmount = null,
    autoResetOnGoal = false
)
