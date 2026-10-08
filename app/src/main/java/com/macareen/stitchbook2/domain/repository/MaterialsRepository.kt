package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.ProjectPatternLink
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.model.YarnAllocation
import kotlinx.coroutines.flow.Flow

/** Project materials: yarn allocations against the stash, and linked library patterns. */
interface MaterialsRepository {
    fun observeAllocations(): Flow<List<YarnAllocation>>

    fun observeAllocationsForProject(projectId: String): Flow<List<YarnAllocation>>

    suspend fun saveAllocation(allocation: YarnAllocation)

    suspend fun deleteAllocation(allocation: YarnAllocation)

    /** Writes both records in one transaction; see [com.macareen.stitchbook2.domain.model.consume]. */
    suspend fun recordConsumption(item: StashItem, allocation: YarnAllocation)

    fun observePatternLinks(): Flow<List<ProjectPatternLink>>

    fun observePatternsForProject(projectId: String): Flow<List<LibraryItem>>

    suspend fun linkPattern(link: ProjectPatternLink)

    suspend fun unlinkPattern(link: ProjectPatternLink)
}
