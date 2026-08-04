package com.macareen.stitchbook2.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.macareen.stitchbook2.domain.backup.BackupImportResult
import com.macareen.stitchbook2.domain.backup.BackupService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SettingsFeedback {
    data object ExportFailed : SettingsFeedback
    data class ImportSucceeded(
        val projectCount: Int?,
        val libraryItemCount: Int?,
        val stashItemCount: Int?
    ) : SettingsFeedback
    data object ImportFailed : SettingsFeedback
    data object ResetCompleted : SettingsFeedback
    data object ResetFailed : SettingsFeedback
}

data class SettingsUiState(
    val isBusy: Boolean = false,
    val feedback: SettingsFeedback? = null
)

class SettingsViewModel(
    private val backupService: BackupService,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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

    fun importBackup(json: String) {
        if (_uiState.value.isBusy) return
        _uiState.value = SettingsUiState(isBusy = true)

        scope.launch {
            val result = backupService.importJson(json)
            _uiState.value = SettingsUiState(
                feedback = when (result) {
                    is BackupImportResult.Success -> SettingsFeedback.ImportSucceeded(
                        result.projectCount,
                        result.libraryItemCount,
                        result.stashItemCount
                    )
                    BackupImportResult.InvalidFormat -> SettingsFeedback.ImportFailed
                }
            )
        }
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
        fun factory(backupService: BackupService): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(backupService)
            }
        }
    }
}
