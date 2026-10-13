package com.macareen.stitchbook2.data.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.repository.LocalSessionRepository
import com.macareen.stitchbook2.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ManualTimePersistenceTest {
    @Test fun manualOriginSurvivesReopenWithoutChangingRunningTimer() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "manual-time-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, StitchbookDatabase::class.java, name).build()
        var db = open()
        val project = Project("p", "Scarf", Craft.CROCHET, ProjectType.OTHER, ProjectStatus.ACTIVE, null, 1, 1)
        val now = java.time.Instant.parse("2026-10-13T20:00:00Z").toEpochMilli()
        val running = SessionTimer.start("running", project.id, now - 60_000, "UTC")
        val manual = (ManualCraftingTime.create(ManualTimeInput("2026-10-13", "19:00", "10", "Forgot timer"), project.id, "manual", java.time.ZoneId.of("UTC"), now) as ManualTimeResult.Ready).session
        try {
            db.projectDao().upsert(project.toEntity())
            val sessions = LocalSessionRepository(db.craftingSessionDao())
            sessions.saveSession(running); sessions.saveSession(manual)
            db.close(); db = open()
            val restored = LocalSessionRepository(db.craftingSessionDao())
            assertEquals(running, restored.observeActiveSession().first())
            assertEquals(manual, restored.observeSessionsForProject(project.id).first().single { it.id == "manual" })
            assertEquals(600_000L, manual.workedMillis(now))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
