package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.data.database.MaterialsDao
import com.macareen.stitchbook2.data.database.toDomain
import com.macareen.stitchbook2.data.database.toEntity
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.ProjectPatternLink
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.YarnAllocation
import com.macareen.stitchbook2.domain.repository.MaterialsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalMaterialsRepository(
    private val dao: MaterialsDao
) : MaterialsRepository {

    override fun observeAllocations(): Flow<List<YarnAllocation>> =
        dao.observeAllAllocations().map { rows -> rows.map { it.toDomain() } }

    override fun observeAllocationsForProject(projectId: String): Flow<List<YarnAllocation>> =
        dao.observeAllocationsForProject(projectId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun saveAllocation(allocation: YarnAllocation) =
        dao.upsertAllocation(allocation.toEntity())

    override suspend fun deleteAllocation(allocation: YarnAllocation) =
        dao.deleteAllocation(allocation.toEntity())

    override suspend fun recordConsumption(item: StashItem, allocation: YarnAllocation) =
        dao.recordConsumption(item.toEntity(), allocation.toEntity())

    override fun observePatternLinks(): Flow<List<ProjectPatternLink>> =
        dao.observeAllPatternLinks().map { rows -> rows.map { it.toDomain() } }

    override fun observePatternsForProject(projectId: String): Flow<List<LibraryItem>> =
        dao.observePatternsForProject(projectId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun linkPattern(link: ProjectPatternLink) =
        dao.upsertPatternLink(link.toEntity())

    override suspend fun unlinkPattern(link: ProjectPatternLink) =
        dao.deletePatternLink(link.toEntity())
}
