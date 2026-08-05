package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Query(
        """
        SELECT tool_items.* FROM tool_items
        INNER JOIN project_tool_assignments ON tool_items.id = project_tool_assignments.tool_item_id
        WHERE project_tool_assignments.project_id = :projectId
        ORDER BY tool_items.updated_at DESC, tool_items.name COLLATE NOCASE ASC, tool_items.id ASC
        """
    )
    fun observeItemsForProject(projectId: String): Flow<List<ToolItemEntity>>

    @Query("SELECT project_id FROM project_tool_assignments WHERE tool_item_id = :toolItemId")
    fun observeProjectIdsForItem(toolItemId: String): Flow<List<String>>

    @Query("DELETE FROM project_tool_assignments WHERE tool_item_id = :toolItemId")
    suspend fun clearAssignmentsForItem(toolItemId: String)

    @Insert
    suspend fun insertAssignments(assignments: List<ProjectToolAssignmentEntity>)

    /** Replaces every project this item is assigned to with exactly [projectIds] in one atomic step. */
    @Transaction
    suspend fun replaceAssignmentsForItem(toolItemId: String, projectIds: Set<String>) {
        clearAssignmentsForItem(toolItemId)
        if (projectIds.isNotEmpty()) {
            insertAssignments(projectIds.map { ProjectToolAssignmentEntity(projectId = it, toolItemId = toolItemId) })
        }
    }

    @Query(
        "DELETE FROM project_tool_assignments WHERE tool_item_id = :toolItemId AND project_id = :projectId"
    )
    suspend fun deleteAssignment(toolItemId: String, projectId: String)
}
