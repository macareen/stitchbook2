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
class ProjectDaoTest {

    private lateinit var database: StitchbookDatabase
    private lateinit var dao: ProjectDao

    @Before
    fun createDatabase() {
        val context: Context =
            InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            StitchbookDatabase::class.java
        ).build()
        dao = database.projectDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndObserveProject() = runBlocking {
        val project = projectEntity(id = "inserted")
        val initialEmissionReceived = CompletableDeferred<Unit>()
        val observedValues = async {
            dao.observeById(project.id)
                .onEach { initialEmissionReceived.complete(Unit) }
                .take(2)
                .toList()
        }

        initialEmissionReceived.await()
        dao.upsert(project)

        assertEquals(listOf(null, project), observedValues.await())
        assertEquals(listOf(project), dao.observeAll().first())
    }

    @Test
    fun updateProject() = runBlocking {
        val original = projectEntity(id = "updated", name = "Original")
        dao.upsert(original)

        val updated = original.copy(
            name = "Updated",
            status = "ACTIVE",
            updatedAt = 200
        )
        dao.upsert(updated)

        assertEquals(updated, dao.observeById(updated.id).first())
    }

    @Test
    fun deleteProject() = runBlocking {
        val project = projectEntity(id = "deleted")
        dao.upsert(project)

        dao.delete(project)

        assertNull(dao.observeById(project.id).first())
        assertEquals(emptyList<ProjectEntity>(), dao.observeAll().first())
    }

    @Test
    fun projectsAreOrderedByStatusThenMostRecentlyUpdated() = runBlocking {
        val projects = listOf(
            projectEntity(id = "abandoned", status = "ABANDONED", updatedAt = 600),
            projectEntity(id = "active-old", status = "ACTIVE", updatedAt = 100),
            projectEntity(id = "completed", status = "COMPLETED", updatedAt = 500),
            projectEntity(id = "planned", status = "PLANNED", updatedAt = 400),
            projectEntity(id = "active-new", status = "ACTIVE", updatedAt = 200),
            projectEntity(id = "paused", status = "PAUSED", updatedAt = 300)
        )
        projects.forEach { dao.upsert(it) }

        val orderedIds = dao.observeAll().first().map { it.id }

        assertEquals(
            listOf(
                "active-new",
                "active-old",
                "planned",
                "paused",
                "completed",
                "abandoned"
            ),
            orderedIds
        )
    }
}

private fun projectEntity(
    id: String,
    name: String = "Project",
    status: String = "PLANNED",
    updatedAt: Long = 100
) = ProjectEntity(
    id = id,
    name = name,
    craft = "KNITTING",
    projectType = "OTHER",
    status = status,
    notes = null,
    createdAt = 50,
    updatedAt = updatedAt
)
