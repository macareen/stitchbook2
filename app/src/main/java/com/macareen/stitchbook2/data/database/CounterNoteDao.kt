package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterNoteDao {

    @Query(
        """
        SELECT * FROM counter_notes
        ORDER BY created_at DESC, id ASC
        """
    )
    fun observeAll(): Flow<List<CounterNoteEntity>>

    @Query(
        """
        SELECT * FROM counter_notes WHERE counter_id = :counterId
        ORDER BY created_at DESC, id ASC
        """
    )
    fun observeByCounterId(counterId: String): Flow<List<CounterNoteEntity>>

    @Upsert
    suspend fun upsert(note: CounterNoteEntity)

    @Delete
    suspend fun delete(note: CounterNoteEntity)
}
