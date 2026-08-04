package com.macareen.stitchbook2.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
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
fun SettingsRoute(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onExport = viewModel::exportBackup,
        onImport = viewModel::importBackup,
        onReset = viewModel::resetAllData,
        onDismissFeedback = viewModel::dismissFeedback
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onExport: (suspend (String) -> Unit) -> Unit,
    onImport: (String) -> Unit,
    onReset: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
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
            DangerZoneCard(onResetClicked = { showResetConfirmation = true })
        }

        uiState.feedback?.let { feedback ->
            item {
                FeedbackBanner(feedback = feedback, onDismiss = onDismissFeedback)
            }
        }
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        )
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.large)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.Shield, contentDescription = null)
                Spacer(modifier = Modifier.width(StitchbookSpacing.small))
                Text(
                    text = stringResource(R.string.settings_guardrails_title),
                    style = MaterialTheme.typography.titleMedium
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
private fun BackupCard(
    isBusy: Boolean,
    pastedJson: String,
    onPastedJsonChanged: (String) -> Unit,
    onExportClicked: () -> Unit,
    onImportFileClicked: () -> Unit,
    onImportTextClicked: () -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(StitchbookSpacing.large)) {
            Text(
                text = stringResource(R.string.settings_backup_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            QuietText(text = stringResource(R.string.settings_backup_export_description))
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            PrimaryActionButton(
                text = stringResource(R.string.settings_export_action),
                onClick = onExportClicked,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(StitchbookSpacing.large))

            QuietText(text = stringResource(R.string.settings_backup_import_description))
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            OutlinedButton(
                onClick = onImportFileClicked,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_import_file_action))
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
private fun DangerZoneCard(onResetClicked: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.large)) {
            Text(
                text = stringResource(R.string.settings_danger_zone_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Text(
                text = stringResource(R.string.settings_danger_zone_description),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
            OutlinedButton(
                onClick = onResetClicked,
                modifier = Modifier.fillMaxWidth()
            ) {
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
    val (message, isError) = when (feedback) {
        SettingsFeedback.ExportFailed ->
            stringResource(R.string.settings_export_failed) to true
        is SettingsFeedback.ImportSucceeded ->
            stringResource(R.string.settings_import_succeeded) to false
        SettingsFeedback.ImportFailed ->
            stringResource(R.string.settings_import_failed) to true
        SettingsFeedback.ResetCompleted ->
            stringResource(R.string.settings_reset_completed) to false
        SettingsFeedback.ResetFailed ->
            stringResource(R.string.settings_reset_failed) to true
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(StitchbookSpacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
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
            onExport = {},
            onImport = {},
            onReset = {},
            onDismissFeedback = {}
        )
    }
}
