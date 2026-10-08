package com.macareen.stitchbook2.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.backup.RestoreMode
import com.macareen.stitchbook2.domain.preferences.MeasurementSystem
import com.macareen.stitchbook2.domain.preferences.ThemeMode
import com.macareen.stitchbook2.domain.preferences.UserPreferences
import com.macareen.stitchbook2.ui.components.ChoiceChipRow
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.cardTitle
import com.macareen.stitchbook2.ui.theme.textSecondary
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, ravelrySection: @Composable () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        preferences = preferences,
        onThemeModeChanged = viewModel::setThemeMode,
        onMeasurementSystemChanged = viewModel::setMeasurementSystem,
        onExport = viewModel::exportBackup,
        onImport = viewModel::previewImport,
        onConfirmImport = viewModel::confirmImport,
        onCancelImport = viewModel::cancelImport,
        onReset = viewModel::resetAllData,
        onDismissFeedback = viewModel::dismissFeedback,
        ravelrySection = ravelrySection
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    preferences: UserPreferences,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onMeasurementSystemChanged: (MeasurementSystem) -> Unit,
    onExport: (suspend (String) -> Unit) -> Unit,
    onImport: (String) -> Unit,
    onConfirmImport: (RestoreMode) -> Unit,
    onCancelImport: () -> Unit,
    onReset: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    ravelrySection: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pastedJson by remember { mutableStateOf("") }
    var showResetConfirmation by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExport { json ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        OutputStreamWriter(stream).use { it.write(json) }
                    }
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    }
                }
                if (text != null) {
                    onImport(text)
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(StitchbookSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.large)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_header_title),
                style = MaterialTheme.typography.headlineMedium
            )
            QuietText(text = stringResource(R.string.settings_header_subtitle))
        }

        item {
            GuardrailsCard()
        }

        item {
            PreferencesCard(
                preferences = preferences,
                onThemeModeChanged = onThemeModeChanged,
                onMeasurementSystemChanged = onMeasurementSystemChanged
            )
        }

        item {
            BackupCard(
                isBusy = uiState.isBusy,
                pastedJson = pastedJson,
                onPastedJsonChanged = { pastedJson = it },
                onExportClicked = {
                    createDocumentLauncher.launch(backupFileName())
                },
                onImportFileClicked = {
                    openDocumentLauncher.launch(arrayOf("application/json"))
                },
                onImportTextClicked = {
                    onImport(pastedJson)
                    pastedJson = ""
                }
            )
        }

        item {
            ravelrySection()
        }

        item {
            DangerZoneCard(onResetClicked = { showResetConfirmation = true })
        }

        uiState.feedback?.let { feedback ->
            item {
                FeedbackBanner(feedback = feedback, onDismiss = onDismissFeedback)
            }
        }
    }

    uiState.importReview?.let { review ->
        ImportReviewDialog(review = review, onConfirm = onConfirmImport, onDismiss = onCancelImport)
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(text = stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(text = stringResource(R.string.settings_reset_confirm_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        onReset()
                    }
                ) {
                    Text(text = stringResource(R.string.settings_reset_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun GuardrailsCard() {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        )
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inversePrimary
                )
                Spacer(modifier = Modifier.width(StitchbookSpacing.small))
                Text(
                    text = stringResource(R.string.settings_guardrails_title),
                    style = MaterialTheme.typography.cardTitle,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Text(
                text = stringResource(R.string.settings_guardrails_description),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PreferencesCard(
    preferences: UserPreferences,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onMeasurementSystemChanged: (MeasurementSystem) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(StitchbookSpacing.small))
                Text(
                    text = stringResource(R.string.settings_preferences_title),
                    style = MaterialTheme.typography.cardTitle,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
            QuietText(text = stringResource(R.string.settings_theme_label))
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            ChoiceChipRow(
                options = ThemeMode.entries,
                selected = preferences.themeMode,
                optionLabel = { stringResource(it.labelResource()) },
                onSelected = onThemeModeChanged
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
            QuietText(text = stringResource(R.string.settings_measurement_label))
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            ChoiceChipRow(
                options = MeasurementSystem.entries,
                selected = preferences.measurementSystem,
                optionLabel = { stringResource(it.labelResource()) },
                onSelected = onMeasurementSystemChanged
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            QuietText(text = stringResource(R.string.settings_measurement_description))
        }
    }
}

private fun ThemeMode.labelResource(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun MeasurementSystem.labelResource(): Int = when (this) {
    MeasurementSystem.METRIC -> R.string.settings_measurement_metric
    MeasurementSystem.IMPERIAL -> R.string.settings_measurement_imperial
}

@Composable
private fun BackupCard(
    isBusy: Boolean,
    pastedJson: String,
    onPastedJsonChanged: (String) -> Unit,
    onExportClicked: () -> Unit,
    onImportFileClicked: () -> Unit,
    onImportTextClicked: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(StitchbookSpacing.small))
                Text(
                    text = stringResource(R.string.settings_backup_title),
                    style = MaterialTheme.typography.cardTitle,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))

            BackupActionBox(
                icon = Icons.Filled.CloudDownload,
                description = stringResource(R.string.settings_backup_export_description)
            ) {
                PrimaryActionButton(
                    text = stringResource(R.string.settings_export_action),
                    onClick = onExportClicked,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))

            BackupActionBox(
                icon = Icons.Filled.CloudUpload,
                description = stringResource(R.string.settings_backup_import_description)
            ) {
                OutlinedButton(
                    onClick = onImportFileClicked,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.settings_import_file_action))
                }
            }

            Spacer(modifier = Modifier.height(StitchbookSpacing.large))

            QuietText(text = stringResource(R.string.settings_backup_paste_description))
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            OutlinedTextField(
                value = pastedJson,
                onValueChange = onPastedJsonChanged,
                minLines = 4,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            OutlinedButton(
                onClick = onImportTextClicked,
                enabled = !isBusy && pastedJson.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_import_text_action))
            }

            if (isBusy) {
                Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
                CircularProgressIndicator(modifier = Modifier)
            }
        }
    }
}

@Composable
private fun BackupActionBox(
    icon: ImageVector,
    description: String,
    action: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.medium)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
            action()
        }
    }
}

@Composable
private fun DangerZoneCard(onResetClicked: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.large)) {
            Text(
                text = stringResource(R.string.settings_danger_zone_title),
                style = MaterialTheme.typography.cardTitle,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Text(
                text = stringResource(R.string.settings_danger_zone_description),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
            OutlinedButton(
                onClick = onResetClicked,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(StitchbookSpacing.small))
                Text(text = stringResource(R.string.settings_reset_action))
            }
        }
    }
}

@Composable
private fun FeedbackBanner(
    feedback: SettingsFeedback,
    onDismiss: () -> Unit
) {
    val isError = when (feedback) {
        SettingsFeedback.ExportFailed,
        SettingsFeedback.ImportUnreadable,
        is SettingsFeedback.ImportInvalid,
        SettingsFeedback.ImportFailed,
        SettingsFeedback.ResetFailed -> true
        is SettingsFeedback.ImportSucceeded,
        SettingsFeedback.ResetCompleted -> false
    }
    val message = when (feedback) {
        SettingsFeedback.ExportFailed -> stringResource(R.string.settings_export_failed)
        is SettingsFeedback.ImportSucceeded -> stringResource(
            if (feedback.mode == RestoreMode.MERGE) R.string.settings_import_done_merge else R.string.settings_import_done_replace
        )
        SettingsFeedback.ImportUnreadable -> stringResource(R.string.settings_import_failed)
        is SettingsFeedback.ImportInvalid -> stringResource(R.string.settings_import_invalid_description)
        SettingsFeedback.ImportFailed -> stringResource(R.string.settings_import_restore_failed)
        SettingsFeedback.ResetCompleted -> stringResource(R.string.settings_reset_completed)
        SettingsFeedback.ResetFailed -> stringResource(R.string.settings_reset_failed)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(StitchbookSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            when (feedback) {
                is SettingsFeedback.ImportSucceeded -> ImportSummary(feedback)
                is SettingsFeedback.ImportInvalid -> IssueList(feedback.issues)
                else -> Unit
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(text = stringResource(R.string.settings_dismiss))
            }
        }
    }
}

/** Everything a restore did or left undone: written counts, kept conflicts, and files to relink. */
@Composable
private fun ImportSummary(result: SettingsFeedback.ImportSucceeded) {
    val written = result.written.filterValues { it > 0 }
    if (written.isEmpty()) {
        Text(text = stringResource(R.string.settings_import_nothing_written), style = MaterialTheme.typography.bodySmall)
    } else {
        Text(text = stringResource(R.string.settings_import_written_title), style = MaterialTheme.typography.bodySmall)
        written.forEach { (type, count) ->
            Text(
                text = stringResource(R.string.settings_import_count_line, stringResource(type.labelResource()), count),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    if (result.conflictsKept > 0) {
        Text(
            text = pluralStringResource(R.plurals.settings_import_conflicts_kept, result.conflictsKept, result.conflictsKept),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (result.notices.isNotEmpty()) {
        Text(
            text = stringResource(R.string.settings_import_notices_title),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        NoticeList(result.notices)
    }
    if (result.missingFiles.isNotEmpty()) {
        Text(
            text = stringResource(R.string.settings_import_missing_files_title),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        result.missingFiles.forEach { name ->
            Text(text = "• $name", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun backupFileName(): String {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return "stitchbook_backup_$date.json"
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    StitchbookTheme {
        SettingsScreen(
            uiState = SettingsUiState(),
            preferences = UserPreferences(),
            onThemeModeChanged = {},
            onMeasurementSystemChanged = {},
            onExport = {},
            onImport = {},
            onConfirmImport = {},
            onCancelImport = {},
            onReset = {},
            onDismissFeedback = {}
        )
    }
}
