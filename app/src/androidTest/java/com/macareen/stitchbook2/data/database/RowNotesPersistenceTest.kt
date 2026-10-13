package com.macareen.stitchbook2.data.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.macareen.stitchbook2.data.repository.LocalJournalRepository
import com.macareen.stitchbook2.data.repository.LocalProjectRepository
import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.feature.journal.ProjectJournalViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class RowNotesPersistenceTest {
    @Test fun recordedTagsAndContextSurviveRoomAndJournalEdits() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            StitchbookDatabase::class.java).build()
        val editorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val project = Project("p", "Crochet shawl", Craft.CROCHET, ProjectType.SHAWL, ProjectStatus.ACTIVE, null, 1, 2)
            val projects = LocalProjectRepository(database.projectDao())
            projects.saveProject(project)
            val journal = LocalJournalRepository(database.journalDao())
            val note = JournalEntry("n", project.id, "2026-10-13", null, "Missed increase", 1, 2,
                JournalNoteTag.MISTAKE, "Body \u00b7 round 4")
            journal.saveEntry(note)
            assertEquals(note, journal.observeEntriesForProject(project.id).first().single())
            val editor = ProjectJournalViewModel(project.id, projects, journal, externalScope = editorScope, clock = { 3 })
            editor.saveEntry(note, note.entryDate, "Fixed now", "Corrected next round")
            val edited = withTimeout(5000) { journal.observeEntriesForProject(project.id).first { list -> list.singleOrNull()?.body == "Corrected next round" }.single() }
            assertEquals(note.tag, edited.tag)
            assertEquals(note.rowContext, edited.rowContext)
            assertEquals(note.createdAt, edited.createdAt)
        } finally { editorScope.cancel(); database.close() }
    }
}
