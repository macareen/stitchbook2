package com.macareen.stitchbook2.data.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.repository.LocalSessionRepository
import com.macareen.stitchbook2.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class TimerConsistencyTest {
    private fun database() = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
        StitchbookDatabase::class.java).build()
    private val now = 900 * 60_000L
    @Test fun concurrentIdleStartsCreateOnlyOneTimer() = runBlocking<Unit> {
        val db = database()
        try {
            val repository = LocalSessionRepository(db.craftingSessionDao())
            val results = listOf("a", "b").map { id -> async(Dispatchers.Default) {
                repository.startSession(SessionTimer.start(id, null, now, "UTC"))
            } }.awaitAll()
            assertEquals(1, results.count { it.started })
            assertEquals(1, repository.observeSessions().first().size)
            assertEquals(1, repository.observeSessions().first().count { it.isActive })
        } finally { db.close() }
    }
    @Test fun explicitSwitchClosesAllRestoredTimersAndPreservesPauseAccountingAndFinishedRecords() = runBlocking<Unit> {
        val db = database()
        try {
            val repository = LocalSessionRepository(db.craftingSessionDao())
            listOf("first", "second").forEach { id -> db.projectDao().upsert(Project(id, "Project", Craft.KNITTING, ProjectType.OTHER, ProjectStatus.ACTIVE, null, 1, 1).toEntity()) }
            val running = SessionTimer.start("running", "first", 0, "UTC").copy(notes = "Keep", rowsCompleted = 9)
            val paused = SessionTimer.pause(SessionTimer.start("paused", "second", 0, "UTC"), 600_000).copy(pausedTotalMillis = 60_000)
            val finished = SessionTimer.stop(SessionTimer.start("finished", null, 0, "UTC"), 300_000)
            val manual = finished.copy(id = "manual", source = CraftingSessionSource.MANUAL)
            val standalone = SessionTimer.start("standalone", null, 100, "UTC")
            listOf(running, paused, standalone, finished, manual).forEach { repository.saveSession(it) }
            val started = SessionTimer.start("new", null, now, "UTC")
            val result = repository.startSession(started, replaceActive = true)
            assertTrue(result.started); assertEquals(setOf("first", "second", null), result.stoppedProjectIds)
            val rows = repository.observeSessions().first().associateBy { it.id }
            assertEquals(SessionTimer.stop(running, now), rows[running.id])
            assertEquals(SessionTimer.stop(paused, now), rows[paused.id])
            assertEquals(SessionTimer.stop(standalone, now), rows[standalone.id])
            assertEquals(540_000L, rows[paused.id]!!.workedMillis(now))
            assertEquals(finished, rows[finished.id]); assertEquals(manual, rows[manual.id])
            assertEquals(listOf(started), rows.values.filter { it.isActive })
        } finally { db.close() }
    }
    @Test fun failedNewTimerInsertRollsBackEveryClosedTimer() = runBlocking<Unit> {
        val db = database()
        try {
            val repository = LocalSessionRepository(db.craftingSessionDao())
            val original = listOf(SessionTimer.start("old", null, 0, "UTC"), SessionTimer.start("other", null, 100, "UTC"))
            original.forEach { repository.saveSession(it) }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_test_start BEFORE INSERT ON crafting_sessions WHEN NEW.id = 'new' BEGIN SELECT RAISE(ABORT, 'test rejection'); END")
            var failed = false
            try { repository.startSession(SessionTimer.start("new", null, now, "UTC"), replaceActive = true) } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(original.toSet(), repository.observeSessions().first().toSet())
        } finally { db.close() }
    }
    @Test fun staleReviewCannotOverwritePauseAndMatchingReviewCanSave() = runBlocking<Unit> {
        val db = database()
        try {
            val repository = LocalSessionRepository(db.craftingSessionDao())
            val original = SessionTimer.start("s", null, 0, "UTC")
            repository.saveSession(original)
            val paused = SessionTimer.pause(original, 600_000).copy(notes = "New note")
            repository.saveSession(paused)
            assertFalse(repository.saveIfUnchanged(original, SessionTimeReview.keepAll(original, now)))
            assertEquals(paused, repository.observeSessions().first().single())
            val stopped = SessionTimer.stop(paused, now)
            assertTrue(repository.saveIfUnchanged(paused, stopped))
            assertEquals(stopped, repository.observeSessions().first().single())
        } finally { db.close() }
    }
    @Test fun newTimerCannotOverwriteExistingRecordWithSameId() = runBlocking<Unit> {
        val db = database()
        try {
            val repository = LocalSessionRepository(db.craftingSessionDao())
            val recorded = SessionTimer.stop(SessionTimer.start("same", null, 0, "UTC"), 600_000)
            repository.saveSession(recorded)
            var rejected = false
            try { repository.startSession(SessionTimer.start("same", null, now, "UTC")) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(recorded, repository.observeSessions().first().single())
        } finally { db.close() }
    }
}
