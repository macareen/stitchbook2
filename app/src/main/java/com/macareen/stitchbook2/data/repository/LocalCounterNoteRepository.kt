package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.data.database.CounterNoteDao
import com.macareen.stitchbook2.data.database.toDomain
import com.macareen.stitchbook2.data.database.toEntity
import com.macareen.stitchbook2.domain.model.CounterNote
import com.macareen.stitchbook2.domain.repository.CounterNoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalCounterNoteRepository(
    private val counterNoteDao: CounterNoteDao
) : CounterNoteRepository {

    override fun observeNotes(): Flow<List<CounterNote>> {
        return counterNoteDao.observeAll().map { notes ->
            notes.map { it.toDomain() }
        }
    }

    override fun observeNotesByCounter(counterId: String): Flow<List<CounterNote>> {
        return counterNoteDao.observeByCounterId(counterId).map { notes ->
            notes.map { it.toDomain() }
        }
    }

    override suspend fun saveNote(note: CounterNote) {
        counterNoteDao.upsert(note.toEntity())
    }

    override suspend fun deleteNote(note: CounterNote) {
        counterNoteDao.delete(note.toEntity())
    }
}
