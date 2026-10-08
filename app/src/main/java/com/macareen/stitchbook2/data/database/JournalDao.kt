package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Query("SELECT * FROM photos ORDER BY created_at ASC, id ASC")
    fun observeAllPhotos(): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT * FROM photos WHERE project_id = :projectId
        ORDER BY COALESCE(taken_date, '') DESC, created_at DESC, id ASC
        """
    )
    fun observePhotosForProject(projectId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE stash_item_id = :stashItemId ORDER BY created_at DESC, id ASC")
    fun observePhotosForStashItem(stashItemId: String): Flow<List<PhotoEntity>>

    @Upsert
    suspend fun upsertPhoto(photo: PhotoEntity)

    @Delete
    suspend fun deletePhoto(photo: PhotoEntity)

    @Query("SELECT * FROM journal_entries ORDER BY entry_date DESC, created_at DESC, id ASC")
    fun observeAllEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE project_id = :projectId ORDER BY entry_date DESC, created_at DESC, id ASC")
    fun observeEntriesForProject(projectId: String): Flow<List<JournalEntryEntity>>

    @Upsert
    suspend fun upsertEntry(entry: JournalEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: JournalEntryEntity)

    @Query("SELECT * FROM milestones ORDER BY project_id ASC, position ASC, created_at ASC")
    fun observeAllMilestones(): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones WHERE project_id = :projectId ORDER BY position ASC, created_at ASC")
    fun observeMilestonesForProject(projectId: String): Flow<List<MilestoneEntity>>

    @Upsert
    suspend fun upsertMilestones(milestones: List<MilestoneEntity>)

    @Delete
    suspend fun deleteMilestone(milestone: MilestoneEntity)
}
