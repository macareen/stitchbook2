package com.macareen.stitchbook2.feature.projects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.ui.theme.StitchbookTheme

@Composable
fun ProjectFormRoute(
    viewModel: ProjectFormViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.savedEvents.collect {
            onSaved()
        }
    }

    ProjectFormScreen(
        uiState = uiState,
        isEditing = viewModel.isEditing,
        onNameChanged = viewModel::updateName,
        onCraftChanged = viewModel::updateCraft,
        onProjectTypeChanged = viewModel::updateProjectType,
        onStatusChanged = viewModel::updateStatus,
        onNotesChanged = viewModel::updateNotes,
        onSave = viewModel::saveProject,
        onCancel = onCancel
    )
}

@Composable
fun ProjectFormScreen(
    uiState: ProjectFormUiState,
    isEditing: Boolean,
    onNameChanged: (String) -> Unit,
    onCraftChanged: (Craft) -> Unit,
    onProjectTypeChanged: (ProjectType) -> Unit,
    onStatusChanged: (ProjectStatus) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDiscardConfirmation by remember { mutableStateOf(false) }

    fun requestExit() {
        if (uiState.hasUnsavedChanges) {
            showDiscardConfirmation = true
        } else {
            onCancel()
        }
    }

    BackHandler(
        enabled = uiState.hasUnsavedChanges,
        onBack = { showDiscardConfirmation = true }
    )

    when {
        uiState.isLoading -> {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.isNotFound -> {
            FormMessage(
                title = stringResource(R.string.project_not_found_title),
                description = stringResource(R.string.project_not_found_description),
                modifier = modifier
            )
        }

        uiState.loadFailed -> {
            FormMessage(
                title = stringResource(R.string.project_load_error_title),
                description = stringResource(R.string.project_load_error_description),
                modifier = modifier
            )
        }

        else -> {
            ProjectFormContent(
                uiState = uiState,
                isEditing = isEditing,
                onNameChanged = onNameChanged,
                onCraftChanged = onCraftChanged,
                onProjectTypeChanged = onProjectTypeChanged,
                onStatusChanged = onStatusChanged,
                onNotesChanged = onNotesChanged,
                onSave = onSave,
                onCancel = ::requestExit,
                modifier = modifier
            )
        }
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text(text = stringResource(R.string.discard_changes_title)) },
            text = { Text(text = stringResource(R.string.discard_changes_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        onCancel()
                    }
                ) {
                    Text(text = stringResource(R.string.discard_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text(text = stringResource(R.string.keep_editing))
                }
            }
        )
    }
}

@Composable
private fun ProjectFormContent(
    uiState: ProjectFormUiState,
    isEditing: Boolean,
    onNameChanged: (String) -> Unit,
    onCraftChanged: (Craft) -> Unit,
    onProjectTypeChanged: (ProjectType) -> Unit,
    onStatusChanged: (ProjectStatus) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = if (isEditing) {
                stringResource(R.string.edit_project)
            } else {
                stringResource(R.string.add_project)
            },
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChanged,
            label = { Text(text = stringResource(R.string.project_name_label)) },
            singleLine = true,
            isError = uiState.nameIsBlank,
            supportingText = if (uiState.nameIsBlank) {
                {
                    Text(text = stringResource(R.string.project_name_required))
                }
            } else {
                null
            },
            enabled = !uiState.isSaving,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        EnumSelector(
            label = stringResource(R.string.project_craft_label),
            selected = uiState.craft,
            options = Craft.entries,
            optionLabel = { stringResource(it.labelResource()) },
            onSelected = onCraftChanged,
            enabled = !uiState.isSaving
        )

        Spacer(modifier = Modifier.height(16.dp))

        EnumSelector(
            label = stringResource(R.string.project_type_label),
            selected = uiState.projectType,
            options = ProjectType.entries,
            optionLabel = { stringResource(it.labelResource()) },
            onSelected = onProjectTypeChanged,
            enabled = !uiState.isSaving
        )

        Spacer(modifier = Modifier.height(16.dp))

        EnumSelector(
            label = stringResource(R.string.project_status_label),
            selected = uiState.status,
            options = ProjectStatus.entries,
            optionLabel = { stringResource(it.labelResource()) },
            onSelected = onStatusChanged,
            enabled = !uiState.isSaving
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.notes,
            onValueChange = onNotesChanged,
            label = { Text(text = stringResource(R.string.project_notes_label)) },
            minLines = 4,
            enabled = !uiState.isSaving,
            modifier = Modifier.fillMaxWidth()
        )

        if (uiState.saveFailed) {
            Text(
                text = stringResource(R.string.project_save_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !uiState.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.cancel))
            }
            Button(
                onClick = onSave,
                enabled = !uiState.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (uiState.isSaving) {
                        stringResource(R.string.saving_project)
                    } else {
                        stringResource(R.string.save_project)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumSelector(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) expanded = !expanded
        }
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(text = label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FormMessage(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AddProjectPreview() {
    StitchbookTheme {
        ProjectFormScreen(
            uiState = ProjectFormUiState(),
            isEditing = false,
            onNameChanged = {},
            onCraftChanged = {},
            onProjectTypeChanged = {},
            onStatusChanged = {},
            onNotesChanged = {},
            onSave = {},
            onCancel = {}
        )
    }
}
