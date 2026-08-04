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
class StashDaoTest {

    private lateinit var database: StitchbookDatabase
    private lateinit var dao: StashDao

    @Before
    fun createDatabase() {
        val context: Context =
            InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            StitchbookDatabase::class.java
        ).build()
        dao = database.stashDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndObserveStashItem() = runBlocking {
        val item = stashItemEntity(id = "inserted")
        val initialEmissionReceived = CompletableDeferred<Unit>()
        val observedValues = async {
            dao.observeById(item.id)
                .onEach { initialEmissionReceived.complete(Unit) }
                .take(2)
                .toList()
        }

        initialEmissionReceived.await()
        dao.upsert(item)

        assertEquals(listOf(null, item), observedValues.await())
        assertEquals(listOf(item), dao.observeAll().first())
    }

    @Test
    fun updateStashItem() = runBlocking {
        val original = stashItemEntity(id = "updated", name = "Original")
        dao.upsert(original)

        val updated = original.copy(name = "Updated", quantity = 3.0, updatedAt = 200)
        dao.upsert(updated)

        assertEquals(updated, dao.observeById(updated.id).first())
    }

    @Test
    fun deleteStashItem() = runBlocking {
        val item = stashItemEntity(id = "deleted")
        dao.upsert(item)

        dao.delete(item)

        assertNull(dao.observeById(item.id).first())
        assertEquals(emptyList<StashItemEntity>(), dao.observeAll().first())
    }

    @Test
    fun stashItemsAreOrderedByMostRecentlyUpdated() = runBlocking {
        val items = listOf(
            stashItemEntity(id = "old", updatedAt = 100),
            stashItemEntity(id = "newest", updatedAt = 300),
            stashItemEntity(id = "middle", updatedAt = 200)
        )
        items.forEach { dao.upsert(it) }

        val orderedIds = dao.observeAll().first().map { it.id }

        assertEquals(listOf("newest", "middle", "old"), orderedIds)
    }
}

private fun stashItemEntity(
    id: String,
    name: String = "Item",
    updatedAt: Long = 100
) = StashItemEntity(
    id = id,
    name = name,
    category = "YARN",
    brand = null,
    colorway = null,
    dyeLot = null,
    weightCategory = null,
    fiberContent = null,
    quantity = 1.0,
    unitLabel = "skeins",
    yardagePerUnit = null,
    notes = null,
    createdAt = 50,
    updatedAt = updatedAt
)
