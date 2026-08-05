package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.CounterNote
import kotlinx.coroutines.flow.Flow

interface CounterNoteRepository {
    fun observeNotes(): Flow<List<CounterNote>>

    fun observeNotesByCounter(counterId: String): Flow<List<CounterNote>>

    suspend fun saveNote(note: CounterNote)

    suspend fun deleteNote(note: CounterNote)
}
