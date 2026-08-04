package com.macareen.stitchbook2

import android.content.Context
import com.macareen.stitchbook2.data.database.StitchbookDatabase
import com.macareen.stitchbook2.data.repository.LocalExecutionRepository
import com.macareen.stitchbook2.data.repository.LocalGuideRepository
import com.macareen.stitchbook2.data.repository.LocalProjectRepository
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository

interface AppContainer {
    val projectRepository: ProjectRepository
    val guideRepository: GuideRepository
    val executionRepository: ExecutionRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val database = StitchbookDatabase.getInstance(context)

    override val projectRepository: ProjectRepository =
        LocalProjectRepository(database.projectDao())

    override val guideRepository: GuideRepository =
        LocalGuideRepository(database.guideDao())

    override val executionRepository: ExecutionRepository =
        LocalExecutionRepository(database.executionDao())
}
