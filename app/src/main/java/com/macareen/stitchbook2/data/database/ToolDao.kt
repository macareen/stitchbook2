package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolDao {

    @Query(
        """
        SELECT * FROM tool_items
        ORDER BY updated_at DESC, name COLLATE NOCASE ASC, id ASC
        """
    )
    fun observeAllItems(): Flow<List<ToolItemEntity>>

    @Query("SELECT * FROM tool_items WHERE id = :id LIMIT 1")
    fun observeItemById(id: String): Flow<ToolItemEntity?>

    @Query(
        """
        SELECT * FROM tool_items WHERE set_id = :setId
        ORDER BY updated_at DESC, name COLLATE NOCASE ASC, id ASC
        """
    )
    fun observeItemsBySet(setId: String): Flow<List<ToolItemEntity>>

    @Upsert
    suspend fun upsertItem(item: ToolItemEntity)

    @Delete
    suspend fun deleteItem(item: ToolItemEntity)

    @Query(
        """
        SELECT * FROM tool_sets
        ORDER BY updated_at DESC, name COLLATE NOCASE ASC, id ASC
        """
    )
    fun observeAllSets(): Flow<List<ToolSetEntity>>

    @Query("SELECT * FROM tool_sets WHERE id = :id LIMIT 1")
    fun observeSetById(id: String): Flow<ToolSetEntity?>

    @Upsert
    suspend fun upsertSet(set: ToolSetEntity)

    @Delete
    suspend fun deleteSet(set: ToolSetEntity)

    @Query(
        """
        SELECT * FROM tool_templates
        ORDER BY updated_at DESC, name COLLATE NOCASE ASC, id ASC
        """
    )
    fun observeAllTemplates(): Flow<List<ToolTemplateEntity>>

    @Upsert
    suspend fun upsertTemplate(template: ToolTemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: ToolTemplateEntity)
}
