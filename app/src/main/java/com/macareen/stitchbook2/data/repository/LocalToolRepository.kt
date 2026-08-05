package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.data.database.ToolDao
import com.macareen.stitchbook2.data.database.toDomain
import com.macareen.stitchbook2.data.database.toEntity
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.domain.model.ToolTemplate
import com.macareen.stitchbook2.domain.repository.ToolRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalToolRepository(
    private val toolDao: ToolDao
) : ToolRepository {

    override fun observeToolItems(): Flow<List<ToolItem>> {
        return toolDao.observeAllItems().map { items ->
            items.map { it.toDomain() }
        }
    }

    override fun observeToolItem(id: String): Flow<ToolItem?> {
        return toolDao.observeItemById(id).map { it?.toDomain() }
    }

    override fun observeToolItemsBySet(setId: String): Flow<List<ToolItem>> {
        return toolDao.observeItemsBySet(setId).map { items ->
            items.map { it.toDomain() }
        }
    }

    override suspend fun saveToolItem(item: ToolItem) {
        toolDao.upsertItem(item.toEntity())
    }

    override suspend fun deleteToolItem(item: ToolItem) {
        toolDao.deleteItem(item.toEntity())
    }

    override fun observeToolSets(): Flow<List<ToolSet>> {
        return toolDao.observeAllSets().map { sets ->
            sets.map { it.toDomain() }
        }
    }

    override fun observeToolSet(id: String): Flow<ToolSet?> {
        return toolDao.observeSetById(id).map { it?.toDomain() }
    }

    override suspend fun saveToolSet(set: ToolSet) {
        toolDao.upsertSet(set.toEntity())
    }

    override suspend fun deleteToolSet(set: ToolSet) {
        toolDao.deleteSet(set.toEntity())
    }

    override fun observeToolTemplates(): Flow<List<ToolTemplate>> {
        return toolDao.observeAllTemplates().map { templates ->
            templates.map { it.toDomain() }
        }
    }

    override suspend fun saveToolTemplate(template: ToolTemplate) {
        toolDao.upsertTemplate(template.toEntity())
    }

    override suspend fun deleteToolTemplate(template: ToolTemplate) {
        toolDao.deleteTemplate(template.toEntity())
    }

    override fun observeToolItemsForProject(projectId: String): Flow<List<ToolItem>> {
        return toolDao.observeItemsForProject(projectId).map { items ->
            items.map { it.toDomain() }
        }
    }

    override fun observeProjectIdsForToolItem(toolItemId: String): Flow<List<String>> {
        return toolDao.observeProjectIdsForItem(toolItemId)
    }

    override suspend fun setProjectAssignments(toolItemId: String, projectIds: Set<String>) {
        toolDao.replaceAssignmentsForItem(toolItemId, projectIds)
    }

    override suspend fun unassignToolFromProject(toolItemId: String, projectId: String) {
        toolDao.deleteAssignment(toolItemId, projectId)
    }
}
