package com.macareen.stitchbook2

import android.content.Context
import androidx.core.net.toUri
import com.macareen.stitchbook2.data.backup.LocalBackupService
import com.macareen.stitchbook2.data.database.StitchbookDatabase
import com.macareen.stitchbook2.data.parsing.MlKitPdfPageOcr
import com.macareen.stitchbook2.data.parsing.PdfBoxTextExtractor
import com.macareen.stitchbook2.data.parsing.StructuredGuideJsonDecoder
import com.macareen.stitchbook2.data.ravelry.HttpRavelryApi
import com.macareen.stitchbook2.data.ravelry.KeystoreRavelryCredentialStore
import com.macareen.stitchbook2.data.preferences.SharedPreferencesUserPreferencesRepository
import com.macareen.stitchbook2.data.repository.LocalCounterNoteRepository
import com.macareen.stitchbook2.data.repository.LocalCounterRepository
import com.macareen.stitchbook2.data.repository.LocalExecutionRepository
import com.macareen.stitchbook2.data.repository.LocalGuideRepository
import com.macareen.stitchbook2.data.repository.LocalJournalRepository
import com.macareen.stitchbook2.data.repository.LocalLibraryRepository
import com.macareen.stitchbook2.data.repository.LocalMaterialsRepository
import com.macareen.stitchbook2.data.repository.LocalProjectRepository
import com.macareen.stitchbook2.data.repository.LocalSessionRepository
import com.macareen.stitchbook2.data.repository.LocalStashRepository
import com.macareen.stitchbook2.data.repository.LocalToolRepository
import com.macareen.stitchbook2.domain.backup.BackupService
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.parsing.StructuredGuideDecoder
import com.macareen.stitchbook2.domain.ravelry.RavelryCredentialStore
import com.macareen.stitchbook2.domain.ravelry.RavelrySync
import com.macareen.stitchbook2.domain.preferences.UserPreferencesRepository
import com.macareen.stitchbook2.domain.repository.CounterNoteRepository
import com.macareen.stitchbook2.domain.repository.CounterRepository
import com.macareen.stitchbook2.domain.repository.ExecutionRepository
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.JournalRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.repository.MaterialsRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.SessionRepository
import com.macareen.stitchbook2.domain.repository.StashRepository
import com.macareen.stitchbook2.domain.repository.ToolRepository
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromPdfUseCase
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromStructuredGuideUseCase
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AppContainer {
    val projectRepository: ProjectRepository
    val guideRepository: GuideRepository
    val executionRepository: ExecutionRepository
    val libraryRepository: LibraryRepository
    val stashRepository: StashRepository
    val toolRepository: ToolRepository
    val counterRepository: CounterRepository
    val counterNoteRepository: CounterNoteRepository
    val materialsRepository: MaterialsRepository
    val journalRepository: JournalRepository
    val sessionRepository: SessionRepository
    val backupService: BackupService
    val pdfTextExtractor: PdfTextExtractor
    val createGuideFromPdfUseCase: CreateGuideFromPdfUseCase
    val structuredGuideDecoder: StructuredGuideDecoder
    val ravelryCredentialStore: RavelryCredentialStore
    val ravelrySync: RavelrySync
    val createGuideFromStructuredGuideUseCase: CreateGuideFromStructuredGuideUseCase
    val userPreferencesRepository: UserPreferencesRepository
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

    override val counterNoteRepository: CounterNoteRepository =
        LocalCounterNoteRepository(database.counterNoteDao())

    override val materialsRepository: MaterialsRepository =
        LocalMaterialsRepository(database.materialsDao())

    override val journalRepository: JournalRepository =
        LocalJournalRepository(database.journalDao())

    override val sessionRepository: SessionRepository =
        LocalSessionRepository(database.craftingSessionDao())

    override val backupService: BackupService =
        LocalBackupService(
            projectRepository,
            libraryRepository,
            stashRepository,
            toolRepository,
            counterRepository,
            counterNoteRepository,
            materialsRepository = materialsRepository,
            journalRepository = journalRepository,
            sessionRepository = sessionRepository,
            isFileAccessible = { uri -> isContentAccessible(context.applicationContext, uri) }
        )

    override val pdfTextExtractor: PdfTextExtractor = PdfBoxTextExtractor(context, MlKitPdfPageOcr())

    override val createGuideFromPdfUseCase: CreateGuideFromPdfUseCase =
        CreateGuideFromPdfUseCase(
            textExtractor = pdfTextExtractor,
            guideRepository = guideRepository,
            newNodeId = { UUID.randomUUID().toString() }
        )

    override val structuredGuideDecoder: StructuredGuideDecoder = StructuredGuideJsonDecoder()

    override val ravelryCredentialStore: RavelryCredentialStore = KeystoreRavelryCredentialStore(context)

    override val ravelrySync: RavelrySync = RavelrySync(
        api = HttpRavelryApi(),
        credentialStore = ravelryCredentialStore,
        stashRepository = stashRepository,
        toolRepository = toolRepository,
        projectRepository = projectRepository,
        libraryRepository = libraryRepository
    )

    override val createGuideFromStructuredGuideUseCase: CreateGuideFromStructuredGuideUseCase =
        CreateGuideFromStructuredGuideUseCase(
            guideRepository = guideRepository,
            newNodeId = { UUID.randomUUID().toString() }
        )

    override val userPreferencesRepository: UserPreferencesRepository =
        SharedPreferencesUserPreferencesRepository(context)
}

/**
 * Whether a referenced document can be opened right now. Any failure --
 * revoked permission, deleted file, unmounted storage, malformed URI --
 * means "relink needed", so every exception maps to false.
 */
private suspend fun isContentAccessible(context: Context, uri: String): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openFileDescriptor(uri.toUri(), "r")?.close() != null
    } catch (_: Exception) {
        false
    }
}
