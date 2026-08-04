package com.macareen.stitchbook2

import android.content.Context
import com.macareen.stitchbook2.data.backup.LocalBackupService
import com.macareen.stitchbook2.data.database.StitchbookDatabase
import com.macareen.stitchbook2.data.repository.LocalExecutionRepository
import com.macareen.stitchbook2.data.repository.LocalGuideRepository
import com.macareen.stitchbook2.data.repository.LocalLibraryRepository
import com.macareen.stitchbook2.data.repository.LocalProjectRepository
import com.macareen.stitchbook2.data.repository.LocalStashRepository
import com.macareen.stitchbook2.domain.backup.BackupService
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.StashRepository

interface AppContainer {
    val projectRepository: ProjectRepository
    val guideRepository: GuideRepository
    val executionRepository: ExecutionRepository
    val libraryRepository: LibraryRepository
    val stashRepository: StashRepository
    val backupService: BackupService
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val database = StitchbookDatabase.getInstance(context)

    override val projectRepository: ProjectRepository =
        LocalProjectRepository(database.projectDao())

    override val guideRepository: GuideRepository =
        LocalGuideRepository(database.guideDao())

    override val executionRepository: ExecutionRepository =
        LocalExecutionRepository(database.executionDao())

    override val libraryRepository: LibraryRepository =
        LocalLibraryRepository(database.libraryDao())

    override val stashRepository: StashRepository =
        LocalStashRepository(database.stashDao())

    override val backupService: BackupService =
        LocalBackupService(projectRepository, libraryRepository, stashRepository)
}
