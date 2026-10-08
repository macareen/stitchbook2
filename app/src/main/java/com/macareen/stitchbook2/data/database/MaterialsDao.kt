package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * An abstract class (not an interface like the older DAOs) because
 * consumption must update a stash item and an allocation atomically
 * (PRODUCT_SPEC.md 6.7: "transactional"), which needs an `open`
 * `@Transaction` method body.
 */
@Dao
abstract class MaterialsDao {

    @Query("SELECT * FROM yarn_allocations ORDER BY created_at ASC, id ASC")
    abstract fun observeAllAllocations(): Flow<List<YarnAllocationEntity>>

    @Query("SELECT * FROM yarn_allocations WHERE project_id = :projectId ORDER BY created_at ASC, id ASC")
    abstract fun observeAllocationsForProject(projectId: String): Flow<List<YarnAllocationEntity>>

    @Upsert
    abstract suspend fun upsertAllocation(allocation: YarnAllocationEntity)

    @Delete
    abstract suspend fun deleteAllocation(allocation: YarnAllocationEntity)

    @Upsert
    abstract suspend fun upsertStashItem(item: StashItemEntity)

    @Transaction
    open suspend fun recordConsumption(item: StashItemEntity, allocation: YarnAllocationEntity) {
        upsertStashItem(item)
        upsertAllocation(allocation)
    }

    @Query("SELECT * FROM project_pattern_links")
    abstract fun observeAllPatternLinks(): Flow<List<ProjectPatternLinkEntity>>

    @Query(
        """
        SELECT library_items.* FROM library_items
        INNER JOIN project_pattern_links ON library_items.id = project_pattern_links.library_item_id
        WHERE project_pattern_links.project_id = :projectId
        ORDER BY library_items.title COLLATE NOCASE ASC, library_items.id ASC
        """
    )
    abstract fun observePatternsForProject(projectId: String): Flow<List<LibraryItemEntity>>

    @Upsert
    abstract suspend fun upsertPatternLink(link: ProjectPatternLinkEntity)

    @Delete
    abstract suspend fun deletePatternLink(link: ProjectPatternLinkEntity)
}
