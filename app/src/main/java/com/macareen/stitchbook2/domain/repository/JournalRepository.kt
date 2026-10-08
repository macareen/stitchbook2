package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.JournalEntry
import com.macareen.stitchbook2.domain.model.Milestone
import com.macareen.stitchbook2.domain.model.Photo
import kotlinx.coroutines.flow.Flow

/** Photos (as references), journal entries, and milestones -- a project's private history. */
interface JournalRepository {
    fun observePhotos(): Flow<List<Photo>>

    fun observePhotosForProject(projectId: String): Flow<List<Photo>>

    fun observePhotosForStashItem(stashItemId: String): Flow<List<Photo>>

    suspend fun savePhoto(photo: Photo)

    /** Removes the reference only; the user's original file is never deleted. */
    suspend fun deletePhoto(photo: Photo)

    fun observeEntries(): Flow<List<JournalEntry>>

    fun observeEntriesForProject(projectId: String): Flow<List<JournalEntry>>

    suspend fun saveEntry(entry: JournalEntry)

    suspend fun deleteEntry(entry: JournalEntry)

    fun observeMilestones(): Flow<List<Milestone>>

    fun observeMilestonesForProject(projectId: String): Flow<List<Milestone>>

    suspend fun saveMilestones(milestones: List<Milestone>)

    suspend fun deleteMilestone(milestone: Milestone)
}
