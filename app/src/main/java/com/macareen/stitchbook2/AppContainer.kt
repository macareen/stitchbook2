package com.macareen.stitchbook2

import android.content.Context
import com.macareen.stitchbook2.data.backup.LocalBackupService
import com.macareen.stitchbook2.data.database.StitchbookDatabase
import com.macareen.stitchbook2.data.parsing.MlKitPdfPageOcr
import com.macareen.stitchbook2.data.parsing.PdfBoxTextExtractor
import com.macareen.stitchbook2.data.repository.LocalCounterRepository
import com.macareen.stitchbook2.data.repository.LocalExecutionRepository
import com.macareen.stitchbook2.data.repository.LocalGuideRepository
import com.macareen.stitchbook2.data.repository.LocalLibraryRepository
import com.macareen.stitchbook2.data.repository.LocalProjectRepository
import com.macareen.stitchbook2.data.repository.LocalStashRepository
import com.macareen.stitchbook2.data.repository.LocalToolRepository
import com.macareen.stitchbook2.domain.backup.BackupService
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.StashRepository
import com.macareen.stitchbook2.domain.repository.ToolRepository
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromPdfUseCase
import java.util.UUID

interface AppContainer {
    val projectRepository: ProjectRepository
    val guideRepository: GuideRepository
    val executionRepository: ExecutionRepository
    val libraryRepository: LibraryRepository
    val stashRepository: StashRepository
    val toolRepository: ToolRepository
    val counterRepository: CounterRepository
    val backupService: BackupService
    val pdfTextExtractor: PdfTextExtractor
    val createGuideFromPdfUseCase: CreateGuideFromPdfUseCase
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

    override val toolRepository: ToolRepository =
        LocalToolRepository(database.toolDao())

    override val counterRepository: CounterRepository =
        LocalCounterRepository(database.counterDao())

    override val backupService: BackupService =
        LocalBackupService(
            projectRepository,
            libraryRepository,
            stashRepository,
            toolRepository,
            counterRepository
        )

    override val pdfTextExtractor: PdfTextExtractor = PdfBoxTextExtractor(context, MlKitPdfPageOcr())

    override val createGuideFromPdfUseCase: CreateGuideFromPdfUseCase =
        CreateGuideFromPdfUseCase(
            textExtractor = pdfTextExtractor,
            guideRepository = guideRepository,
            newNodeId = { UUID.randomUUID().toString() }
        )
}
