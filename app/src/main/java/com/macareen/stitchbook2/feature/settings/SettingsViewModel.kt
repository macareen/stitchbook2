package com.macareen.stitchbook2.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.backup.BackupImportResult
import com.macareen.stitchbook2.domain.backup.BackupIssue
import com.macareen.stitchbook2.domain.backup.BackupNotice
import com.macareen.stitchbook2.domain.backup.BackupPreview
import com.macareen.stitchbook2.domain.backup.BackupRecordType
import com.macareen.stitchbook2.domain.backup.BackupService
import com.macareen.stitchbook2.domain.backup.RestoreMode
import com.macareen.stitchbook2.domain.preferences.MeasurementSystem
import com.macareen.stitchbook2.domain.preferences.ThemeMode
import com.macareen.stitchbook2.domain.preferences.UserPreferences
import com.macareen.stitchbook2.domain.preferences.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SettingsFeedback {
    data object ExportFailed : SettingsFeedback

    /** What a restore actually did, so nothing it skipped or couldn't find is hidden. */
    data class ImportSucceeded(
        val mode: RestoreMode,
        /** Records written per type (MERGE skips identical and conflicting records). */
        val written: Map<BackupRecordType, Int>,
        /** Records left as they are locally because the file's copy differs (MERGE only). */
        val conflictsKept: Int,
        /** Display names of referenced PDFs and photos this device can't open -- relink these. */
        val missingFiles: List<String>,
        /** Links cleared and records left out instead of failing (MERGE only). */
        val notices: List<BackupNotice> = emptyList()
    ) : SettingsFeedback

    /** The file isn't a Stitchbook backup at all. */
    data object ImportUnreadable : SettingsFeedback

    /** Validation failed at write time; nothing was written. */
    data class ImportInvalid(val issues: List<BackupIssue>) : SettingsFeedback

    data object ImportFailed : SettingsFeedback
    data object ResetCompleted : SettingsFeedback
    data object ResetFailed : SettingsFeedback
}

/** A parsed backup awaiting the user's choice of restore mode; nothing has been written yet. */
data class ImportReview(
    val json: String,
    val preview: BackupPreview
)

data class SettingsUiState(
    val isBusy: Boolean = false,
    val feedback: SettingsFeedback? = null,
    val importReview: ImportReview? = null
)

class SettingsViewModel(
    private val backupService: BackupService,
    private val preferencesRepository: UserPreferencesRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences

    fun setThemeMode(mode: ThemeMode) = preferencesRepository.setThemeMode(mode)

    fun setMeasurementSystem(system: MeasurementSystem) = preferencesRepository.setMeasurementSystem(system)

    /**
     * Generates the backup JSON and hands it to [onReady] (a caller-supplied
     * write to wherever the user picked via the SAF document-creation
     * picker). File I/O stays out of this ViewModel; only the JSON content
     * is this layer's responsibility.
     */
    fun exportBackup(onReady: suspend (String) -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.value = SettingsUiState(isBusy = true)

        scope.launch {
            try {
                val json = backupService.exportJson()
                onReady(json)
                _uiState.value = SettingsUiState()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = SettingsUiState(feedback = SettingsFeedback.ExportFailed)
            }
        }
    }

    /**
     * Step one of a restore: parse, validate, and compare [json] with the
     * library without writing anything. An unreadable file goes straight to
     * feedback; anything else opens the review in [SettingsUiState.importReview].
     */
    fun previewImport(json: String) {
        if (_uiState.value.isBusy) return
        _uiState.value = SettingsUiState(isBusy = true)

        scope.launch {
            _uiState.value = try {
                when (val preview = backupService.previewImport(json)) {
                    BackupPreview.Unreadable -> SettingsUiState(feedback = SettingsFeedback.ImportUnreadable)
                    else -> SettingsUiState(importReview = ImportReview(json, preview))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                SettingsUiState(feedback = SettingsFeedback.ImportFailed)
            }
        }
    }

    /** Step two: restore the reviewed file in [mode]. The service re-validates before writing. */
    fun confirmImport(mode: RestoreMode) {
        val review = _uiState.value.importReview ?: return
        if (_uiState.value.isBusy || review.preview !is BackupPreview.Ready) return
        _uiState.value = SettingsUiState(isBusy = true)

        scope.launch {
            _uiState.value = try {
                val feedback = when (val result = backupService.importJson(review.json, mode)) {
                    is BackupImportResult.Success -> SettingsFeedback.ImportSucceeded(
                        mode = mode,
                        written = result.written,
                        conflictsKept = result.conflictsKept,
                        missingFiles = result.missingFiles,
                        notices = result.notices
                    )
                    BackupImportResult.InvalidFormat -> SettingsFeedback.ImportUnreadable
                    is BackupImportResult.ValidationFailed -> SettingsFeedback.ImportInvalid(result.issues)
                }
                SettingsUiState(feedback = feedback)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                SettingsUiState(feedback = SettingsFeedback.ImportFailed)
            }
        }
    }

    fun cancelImport() {
        _uiState.value = _uiState.value.copy(importReview = null)
    }

    fun resetAllData() {
        if (_uiState.value.isBusy) return
        _uiState.value = SettingsUiState(isBusy = true)

        scope.launch {
            try {
                backupService.resetAllData()
                _uiState.value = SettingsUiState(feedback = SettingsFeedback.ResetCompleted)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = SettingsUiState(feedback = SettingsFeedback.ResetFailed)
            }
        }
    }

    fun dismissFeedback() {
        _uiState.value = _uiState.value.copy(feedback = null)
    }

    companion object {
        fun factory(
            backupService: BackupService,
            preferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(backupService, preferencesRepository)
            }
        }
    }
}
