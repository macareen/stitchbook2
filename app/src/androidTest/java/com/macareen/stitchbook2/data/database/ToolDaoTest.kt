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
class ToolDaoTest {

    private lateinit var database: StitchbookDatabase
    private lateinit var dao: ToolDao

    @Before
    fun createDatabase() {
        val context: Context =
            InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            StitchbookDatabase::class.java
        ).build()
        dao = database.toolDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndObserveToolItem() = runBlocking {
        val item = toolItemEntity(id = "inserted")
        val initialEmissionReceived = CompletableDeferred<Unit>()
        val observedValues = async {
            dao.observeItemById(item.id)
                .onEach { initialEmissionReceived.complete(Unit) }
                .take(2)
                .toList()
        }

        initialEmissionReceived.await()
        dao.upsertItem(item)

        assertEquals(listOf(null, item), observedValues.await())
        assertEquals(listOf(item), dao.observeAllItems().first())
    }

    @Test
    fun updateToolItem() = runBlocking {
        val original = toolItemEntity(id = "updated", name = "Original")
        dao.upsertItem(original)

        val updated = original.copy(name = "Updated", quantity = 3, updatedAt = 200)
        dao.upsertItem(updated)

        assertEquals(updated, dao.observeItemById(updated.id).first())
    }

    @Test
    fun deleteToolItem() = runBlocking {
        val item = toolItemEntity(id = "deleted")
        dao.upsertItem(item)

        dao.deleteItem(item)

        assertNull(dao.observeItemById(item.id).first())
        assertEquals(emptyList<ToolItemEntity>(), dao.observeAllItems().first())
    }

    @Test
    fun toolItemsAreOrderedByMostRecentlyUpdated() = runBlocking {
        val items = listOf(
            toolItemEntity(id = "old", updatedAt = 100),
            toolItemEntity(id = "newest", updatedAt = 300),
            toolItemEntity(id = "middle", updatedAt = 200)
        )
        items.forEach { dao.upsertItem(it) }

        val orderedIds = dao.observeAllItems().first().map { it.id }

        assertEquals(listOf("newest", "middle", "old"), orderedIds)
    }

    @Test
    fun insertUpdateAndDeleteToolSet() = runBlocking {
        val set = toolSetEntity(id = "set-1", name = "Original set")
        dao.upsertSet(set)
        assertEquals(set, dao.observeSetById(set.id).first())

        val updated = set.copy(name = "Renamed set", updatedAt = 200)
        dao.upsertSet(updated)
        assertEquals(updated, dao.observeSetById(set.id).first())

        dao.deleteSet(updated)
        assertNull(dao.observeSetById(set.id).first())
    }

    @Test
    fun observeItemsBySetReturnsOnlyMembersOfThatSet() = runBlocking {
        val set = toolSetEntity(id = "set-1")
        dao.upsertSet(set)
        val member = toolItemEntity(id = "member", setId = set.id)
        val nonMember = toolItemEntity(id = "non-member", setId = null)
        dao.upsertItem(member)
        dao.upsertItem(nonMember)

        val membersOfSet = dao.observeItemsBySet(set.id).first()

        assertEquals(listOf(member), membersOfSet)
    }

    @Test
    fun deletingAToolSetOrphansItsComponentsInsteadOfDeletingThem() = runBlocking {
        val set = toolSetEntity(id = "set-1")
        dao.upsertSet(set)
        val member = toolItemEntity(id = "member", setId = set.id)
        dao.upsertItem(member)

        dao.deleteSet(set)

        val survivingItem = dao.observeItemById(member.id).first()
        assertEquals(null, survivingItem?.setId)
    }

    private fun toolItemEntity(
        id: String,
        name: String = "Item",
        setId: String? = null,
        updatedAt: Long = 100
    ) = ToolItemEntity(
        id = id,
        name = name,
        category = "CROCHET_HOOK",
        brand = null,
        material = null,
        sizeMetricMm = null,
        sizeLabel = null,
        lengthMm = null,
        statedCableLengthMm = null,
        cableLengthDefinition = null,
        approximateAssembledLengthMm = null,
        connectorFamily = null,
        compatibilityNotes = null,
        quantity = 1,
        storageLocation = null,
        notes = null,
        setId = setId,
        createdAt = 50,
        updatedAt = updatedAt
    )

    private fun toolSetEntity(
        id: String,
        name: String = "Set",
        updatedAt: Long = 100
    ) = ToolSetEntity(
        id = id,
        name = name,
        brand = null,
        notes = null,
        createdAt = 50,
        updatedAt = updatedAt
    )
}
