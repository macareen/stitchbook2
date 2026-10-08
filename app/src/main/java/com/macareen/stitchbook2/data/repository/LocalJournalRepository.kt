package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.data.database.JournalDao
import com.macareen.stitchbook2.data.database.toDomain
import com.macareen.stitchbook2.data.database.toEntity
import com.macareen.stitchbook2.domain.model.JournalEntry
import com.macareen.stitchbook2.domain.model.Milestone
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalJournalRepository(
    private val dao: JournalDao
) : JournalRepository {

    override fun observePhotos(): Flow<List<Photo>> =
        dao.observeAllPhotos().map { rows -> rows.map { it.toDomain() } }

    override fun observePhotosForProject(projectId: String): Flow<List<Photo>> =
        dao.observePhotosForProject(projectId).map { rows -> rows.map { it.toDomain() } }

    override fun observePhotosForStashItem(stashItemId: String): Flow<List<Photo>> =
        dao.observePhotosForStashItem(stashItemId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun savePhoto(photo: Photo) = dao.upsertPhoto(photo.toEntity())

    override suspend fun deletePhoto(photo: Photo) = dao.deletePhoto(photo.toEntity())

    override fun observeEntries(): Flow<List<JournalEntry>> =
        dao.observeAllEntries().map { rows -> rows.map { it.toDomain() } }

    override fun observeEntriesForProject(projectId: String): Flow<List<JournalEntry>> =
        dao.observeEntriesForProject(projectId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun saveEntry(entry: JournalEntry) = dao.upsertEntry(entry.toEntity())

    override suspend fun deleteEntry(entry: JournalEntry) = dao.deleteEntry(entry.toEntity())

    override fun observeMilestones(): Flow<List<Milestone>> =
        dao.observeAllMilestones().map { rows -> rows.map { it.toDomain() } }

    override fun observeMilestonesForProject(projectId: String): Flow<List<Milestone>> =
        dao.observeMilestonesForProject(projectId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun saveMilestones(milestones: List<Milestone>) =
        dao.upsertMilestones(milestones.map { it.toEntity() })

    override suspend fun deleteMilestone(milestone: Milestone) = dao.deleteMilestone(milestone.toEntity())
}
