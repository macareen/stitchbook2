package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.data.database.ProjectDao
import com.macareen.stitchbook2.data.database.toDomain
import com.macareen.stitchbook2.data.database.toEntity
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalProjectRepository(
    private val projectDao: ProjectDao
) : ProjectRepository {

    override fun observeProjects(): Flow<List<Project>> {
        return projectDao.observeAll().map { projects ->
            projects.map { it.toDomain() }
        }
    }

    override fun observeProject(id: String): Flow<Project?> {
        return projectDao.observeById(id).map { it?.toDomain() }
    }

    override suspend fun saveProject(project: Project) {
        projectDao.upsert(project.toEntity())
    }

    override suspend fun deleteProject(project: Project) {
        projectDao.delete(project.toEntity())
    }
}
