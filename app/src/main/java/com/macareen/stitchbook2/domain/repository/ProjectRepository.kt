package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>

    fun observeProject(id: String): Flow<Project?>

    suspend fun saveProject(project: Project)

    suspend fun deleteProject(project: Project)
}
