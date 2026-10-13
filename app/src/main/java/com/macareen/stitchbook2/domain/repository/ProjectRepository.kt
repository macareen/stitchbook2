package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>

    fun observeProject(id: String): Flow<Project?>

    /** Moving a project to Completed also finalizes that project's open sessions atomically in local storage. */
    suspend fun saveProject(project: Project)

    suspend fun deleteProject(project: Project)
}
