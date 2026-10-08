package com.macareen.stitchbook2.feature.settings

import com.macareen.stitchbook2.domain.backup.BackupImportResult
import com.macareen.stitchbook2.domain.backup.BackupIssue
import com.macareen.stitchbook2.domain.backup.BackupIssueKind
import com.macareen.stitchbook2.domain.backup.BackupPreview
import com.macareen.stitchbook2.domain.backup.BackupRecordType
import com.macareen.stitchbook2.domain.backup.BackupService
import com.macareen.stitchbook2.domain.backup.RestoreMode
import com.macareen.stitchbook2.domain.backup.TypeComparison
import com.macareen.stitchbook2.domain.preferences.MeasurementSystem
import com.macareen.stitchbook2.domain.preferences.ThemeMode
import com.macareen.stitchbook2.domain.preferences.UserPreferences
import com.macareen.stitchbook2.domain.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    // Dispatchers.Unconfined runs launched coroutines synchronously.
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val ready = BackupPreview.Ready(
        formatVersion = 2,
        comparison = mapOf(BackupRecordType.PROJECTS to TypeComparison(incoming = 2, new = 1, identical = 0, conflicting = 1, onlyLocal = 3)),
        conflicts = emptyList()
    )

    private val issue = BackupIssue(BackupIssueKind.MISSING_REFERENCE, BackupRecordType.COUNTERS, "c1", "Refers to project \"p9\".")

    @Test
    fun previewOpensAReviewWithoutWritingAnything() {
        val service = FakeBackupService(preview = ready)
        val viewModel = SettingsViewModel(service, FakePreferences(), scope)

        viewModel.previewImport("{}")

        assertEquals(ImportReview("{}", ready), viewModel.uiState.value.importReview)
        assertFalse(viewModel.uiState.value.isBusy)
        assertTrue(service.imports.isEmpty())
    }

    @Test
    fun anUnreadableFileIsReportedAsFeedbackNotAReview() {
        val viewModel = SettingsViewModel(FakeBackupService(preview = BackupPreview.Unreadable), FakePreferences(), scope)

        viewModel.previewImport("not json")

        assertNull(viewModel.uiState.value.importReview)
        assertEquals(SettingsFeedback.ImportUnreadable, viewModel.uiState.value.feedback)
    }

    @Test
    fun confirmingImportsTheReviewedJsonInTheChosenModeAndReportsEverything() {
        val service = FakeBackupService(
            preview = ready,
            result = BackupImportResult.Success(
                projectCount = 2, libraryItemCount = null, stashItemCount = null, toolSetCount = null,
                toolItemCount = null, counterCount = null, counterNoteCount = null,
                written = mapOf(BackupRecordType.PROJECTS to 1),
                conflictsKept = 1,
                missingFiles = listOf("sleeve.jpg")
            )
        )
        val viewModel = SettingsViewModel(service, FakePreferences(), scope)

        viewModel.previewImport("{\"a\":1}")
        viewModel.confirmImport(RestoreMode.MERGE)

        assertEquals(listOf("{\"a\":1}" to RestoreMode.MERGE), service.imports)
        assertNull(viewModel.uiState.value.importReview)
        assertEquals(
            SettingsFeedback.ImportSucceeded(
                mode = RestoreMode.MERGE,
                written = mapOf(BackupRecordType.PROJECTS to 1),
                conflictsKept = 1,
                missingFiles = listOf("sleeve.jpg")
            ),
            viewModel.uiState.value.feedback
        )
    }

    @Test
    fun aValidationFailureAtWriteTimeCarriesItsIssues() {
        val service = FakeBackupService(preview = ready, result = BackupImportResult.ValidationFailed(listOf(issue)))
        val viewModel = SettingsViewModel(service, FakePreferences(), scope)

        viewModel.previewImport("{}")
        viewModel.confirmImport(RestoreMode.REPLACE)

        assertEquals(SettingsFeedback.ImportInvalid(listOf(issue)), viewModel.uiState.value.feedback)
    }

    @Test
    fun anInvalidPreviewCannotBeConfirmed() {
        val service = FakeBackupService(preview = BackupPreview.Invalid(listOf(issue)))
        val viewModel = SettingsViewModel(service, FakePreferences(), scope)

        viewModel.previewImport("{}")
        viewModel.confirmImport(RestoreMode.REPLACE)

        assertTrue(service.imports.isEmpty())
        assertEquals(BackupPreview.Invalid(listOf(issue)), viewModel.uiState.value.importReview?.preview)
    }

    @Test
    fun cancellingDropsTheReviewWithoutImporting() {
        val service = FakeBackupService(preview = ready)
        val viewModel = SettingsViewModel(service, FakePreferences(), scope)

        viewModel.previewImport("{}")
        viewModel.cancelImport()
        viewModel.confirmImport(RestoreMode.MERGE)

        assertNull(viewModel.uiState.value.importReview)
        assertTrue(service.imports.isEmpty())
    }

    private class FakeBackupService(
        private val preview: BackupPreview,
        private val result: BackupImportResult = BackupImportResult.InvalidFormat
    ) : BackupService {
        val imports = mutableListOf<Pair<String, RestoreMode>>()

        override suspend fun exportJson(): String = "{}"
        override suspend fun exportProjectJson(projectId: String): String? = null
        override suspend fun exportProjectMarkdown(projectId: String): String? = null
        override suspend fun previewImport(json: String): BackupPreview = preview
        override suspend fun importJson(json: String, mode: RestoreMode): BackupImportResult {
            imports += json to mode
            return result
        }
        override suspend fun resetAllData() = Unit
    }

    private class FakePreferences : UserPreferencesRepository {
        override val preferences: StateFlow<UserPreferences> = MutableStateFlow(UserPreferences())
        override fun setThemeMode(mode: ThemeMode) = Unit
        override fun setMeasurementSystem(system: MeasurementSystem) = Unit
    }
}
