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
class LibraryDaoTest {

    private lateinit var database: StitchbookDatabase
    private lateinit var dao: LibraryDao

    @Before
    fun createDatabase() {
        val context: Context =
            InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            StitchbookDatabase::class.java
        ).build()
        dao = database.libraryDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndObserveLibraryItem() = runBlocking {
        val item = libraryItemEntity(id = "inserted")
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
    fun updateLibraryItem() = runBlocking {
        val original = libraryItemEntity(id = "updated", title = "Original")
        dao.upsert(original)

        val updated = original.copy(title = "Updated", bookmarked = true, updatedAt = 200)
        dao.upsert(updated)

        assertEquals(updated, dao.observeById(updated.id).first())
    }

    @Test
    fun deleteLibraryItem() = runBlocking {
        val item = libraryItemEntity(id = "deleted")
        dao.upsert(item)

        dao.delete(item)

        assertNull(dao.observeById(item.id).first())
        assertEquals(emptyList<LibraryItemEntity>(), dao.observeAll().first())
    }

    @Test
    fun libraryItemsAreOrderedByMostRecentlyUpdated() = runBlocking {
        val items = listOf(
            libraryItemEntity(id = "old", updatedAt = 100),
            libraryItemEntity(id = "newest", updatedAt = 300),
            libraryItemEntity(id = "middle", updatedAt = 200)
        )
        items.forEach { dao.upsert(it) }

        val orderedIds = dao.observeAll().first().map { it.id }

        assertEquals(listOf("newest", "middle", "old"), orderedIds)
    }
}

private fun libraryItemEntity(
    id: String,
    title: String = "Reference",
    updatedAt: Long = 100
) = LibraryItemEntity(
    id = id,
    title = title,
    craft = "KNITTING",
    author = null,
    sourceUrl = null,
    tags = "",
    notes = null,
    bookmarked = false,
    createdAt = 50,
    updatedAt = updatedAt
)
