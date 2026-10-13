package com.macareen.stitchbook2.data.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.repository.LocalProjectRepository
import com.macareen.stitchbook2.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProjectCompletionTimerTest {
    private val project = Project("p", "Cardigan", Craft.KNITTING, ProjectType.CARDIGAN, ProjectStatus.ACTIVE, "Keep", 1, 2)
    private val now = 10_000L

    @Test fun completingProjectFinalizesAllItsOpenSessionsAndPreservesOtherScopes() = runBlocking {
        val db = database()
        try {
            val projects = LocalProjectRepository(db.projectDao(), clock = { now })
            projects.saveProject(project); projects.saveProject(project.copy(id = "other"))
            val running = SessionTimer.start("running", project.id, 1000, "UTC").copy(rowsCompleted = 9, stitchesPerRow = 20, notes = "Keep record")
            val paused = SessionTimer.pause(SessionTimer.start("paused", project.id, 1000, "UTC"), 3000).copy(pausedTotalMillis = 400)
            val finished = SessionTimer.stop(SessionTimer.start("finished", project.id, 1000, "UTC"), 2000)
            val other = SessionTimer.start("other-session", "other", 1000, "UTC")
            val standalone = SessionTimer.start("standalone", null, 1000, "UTC")
            listOf(running, paused, finished, other, standalone).forEach { db.craftingSessionDao().upsert(it.toEntity()) }
            projects.saveProject(project.copy(status = ProjectStatus.COMPLETED, completedDate = "2026-10-13"))
            val recorded = db.craftingSessionDao().observeAll().first().associate { it.id to it.toDomain() }
            assertEquals(SessionTimer.stop(running, now), recorded[running.id])
            assertEquals(SessionTimer.stop(paused, now), recorded[paused.id])
            assertEquals(finished, recorded[finished.id])
            assertEquals(other, recorded[other.id]); assertEquals(standalone, recorded[standalone.id])
            assertEquals(1600L, recorded[paused.id]!!.workedMillis(now + 60_000))
            assertEquals(ProjectStatus.COMPLETED, projects.observeProject(project.id).first()!!.status)
            projects.saveProject(project.copy(status = ProjectStatus.COMPLETED, notes = "Edited later"))
            assertEquals(recorded, db.craftingSessionDao().observeAll().first().associate { it.id to it.toDomain() })
        } finally { db.close() }
    }

    @Test fun failedFinalizationRollsBackProjectStatusAndTimerTogether() = runBlocking {
        val db = database()
        try {
            val projects = LocalProjectRepository(db.projectDao(), clock = { now })
            projects.saveProject(project)
            val session = SessionTimer.start("s", project.id, 1000, "UTC")
            db.craftingSessionDao().upsert(session.toEntity())
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_test_finish BEFORE UPDATE ON crafting_sessions WHEN NEW.ended_at IS NOT NULL BEGIN SELECT RAISE(ABORT, 'test rejection'); END")
            var rejected = false
            try { projects.saveProject(project.copy(status = ProjectStatus.COMPLETED)) } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
            assertEquals(project, projects.observeProject(project.id).first())
            assertEquals(session, db.craftingSessionDao().observeAll().first().single().toDomain())
        } finally { db.close() }
    }

    @Test fun otherStatusEditsDoNotChangeTimers() = runBlocking {
        val db = database()
        try {
            val projects = LocalProjectRepository(db.projectDao(), clock = { now })
            projects.saveProject(project)
            val session = SessionTimer.start("s", project.id, 1000, "UTC")
            db.craftingSessionDao().upsert(session.toEntity())
            listOf(ProjectStatus.ACTIVE, ProjectStatus.PLANNED, ProjectStatus.PAUSED, ProjectStatus.ABANDONED).forEach {
                projects.saveProject(project.copy(status = it))
                assertEquals(session, db.craftingSessionDao().observeAll().first().single().toDomain())
            }
        } finally { db.close() }
    }
    @Test fun editingCompletedMetadataDoesNotStopANewSessionButCompletingAgainDoes() = runBlocking {
        val db = database()
        try {
            val projects = LocalProjectRepository(db.projectDao(), clock = { now })
            projects.saveProject(project.copy(status = ProjectStatus.COMPLETED))
            val later = SessionTimer.start("later", project.id, 2000, "UTC")
            db.craftingSessionDao().upsert(later.toEntity())
            projects.saveProject(project.copy(status = ProjectStatus.COMPLETED, notes = "New photo notes"))
            assertEquals(later, db.craftingSessionDao().observeAll().first().single().toDomain())
            projects.saveProject(project.copy(status = ProjectStatus.ACTIVE))
            projects.saveProject(project.copy(status = ProjectStatus.COMPLETED))
            assertEquals(SessionTimer.stop(later, now), db.craftingSessionDao().observeAll().first().single().toDomain())
        } finally { db.close() }
    }

    private fun database() = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
        StitchbookDatabase::class.java).build()
}
