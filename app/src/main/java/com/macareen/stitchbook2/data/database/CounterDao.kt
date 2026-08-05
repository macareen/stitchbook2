package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterDao {

    /**
     * Atomically adds [amount] to a counter's current_value in a single SQL
     * statement, rather than a read-then-write round trip -- so two
     * concurrent callers targeting the same row can never lose one of
     * their updates.
     */
    @Query(
        """
        UPDATE counters
        SET current_value = current_value + :amount, updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun incrementValue(id: String, amount: Int, updatedAt: Long)

    @Query(
        """
        SELECT * FROM counters
        ORDER BY updated_at DESC, name COLLATE NOCASE ASC, id ASC
        """
    )
    fun observeAll(): Flow<List<CounterEntity>>

    @Query(
        """
        SELECT * FROM counters WHERE project_id = :projectId
        ORDER BY updated_at DESC, name COLLATE NOCASE ASC, id ASC
        """
    )
    fun observeByProjectId(projectId: String): Flow<List<CounterEntity>>

    @Query("SELECT * FROM counters WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<CounterEntity?>

    @Upsert
    suspend fun upsert(counter: CounterEntity)

    @Delete
    suspend fun delete(counter: CounterEntity)
}
