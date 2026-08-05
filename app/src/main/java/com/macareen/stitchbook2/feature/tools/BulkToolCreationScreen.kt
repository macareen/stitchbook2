package com.macareen.stitchbook2.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.BulkSizeInputMode
import com.macareen.stitchbook2.domain.model.ToolCategory
import com.macareen.stitchbook2.domain.model.ToolTemplate
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing

@Composable
fun BulkToolCreationRoute(
    viewModel: BulkToolCreationViewModel,
    onDone: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()

    BulkToolCreationScreen(
        uiState = uiState,
        templates = templates,
        onCategoryChanged = viewModel::updateCategory,
        onBrandChanged = viewModel::updateBrand,
        onMaterialChanged = viewModel::updateMaterial,
        onSizeInputModeChanged = viewModel::updateSizeInputMode,
        onRangeStartChanged = viewModel::updateRangeStart,
        onRangeEndChanged = viewModel::updateRangeEnd,
        onRangeIncrementChanged = viewModel::updateRangeIncrement,
        onCustomSizesChanged = viewModel::updateCustomSizes,
        onQuantityPerSizeChanged = viewModel::updateQuantityPerSize,
        onStorageLocationChanged = viewModel::updateStorageLocation,
        onNotesChanged = viewModel::updateNotes,
        onCreateAsSetChanged = viewModel::updateCreateAsSet,
        onSetNameChanged = viewModel::updateSetName,
        onCreateAll = viewModel::createAll,
        onApplyTemplate = viewModel::applyTemplate,
        onSaveAsTemplate = viewModel::saveCurrentAsTemplate,
        onDeleteTemplate = viewModel::deleteTemplate
    )

    LaunchedEffect(uiState.didCreate) {
        if (uiState.didCreate) {
            onDone()
        }
    }
}

@Composable
fun BulkToolCreationScreen(
    uiState: BulkToolCreationUiState,
    templates: List<ToolTemplate>,
    onCategoryChanged: (ToolCategory) -> Unit,
    onBrandChanged: (String) -> Unit,
    onMaterialChanged: (String) -> Unit,
    onSizeInputModeChanged: (BulkSizeInputMode) -> Unit,
    onRangeStartChanged: (String) -> Unit,
    onRangeEndChanged: (String) -> Unit,
    onRangeIncrementChanged: (String) -> Unit,
    onCustomSizesChanged: (String) -> Unit,
    onQuantityPerSizeChanged: (String) -> Unit,
    onStorageLocationChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onCreateAsSetChanged: (Boolean) -> Unit,
    onSetNameChanged: (String) -> Unit,
    onCreateAll: () -> Unit,
    onApplyTemplate: (ToolTemplate) -> Unit,
    onSaveAsTemplate: (String) -> Unit,
    onDeleteTemplate: (ToolTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    val form = uiState.form
    var isSavingTemplate by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(StitchbookSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
    ) {
        item {
            Text(
                text = stringResource(R.string.tools_bulk_header_title),
                style = MaterialTheme.typography.headlineMedium
            )
            QuietText(text = stringResource(R.string.tools_bulk_header_subtitle))
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
        }

        item {
            TemplatePicker(
                templates = templates,
                onApplyTemplate = onApplyTemplate,
                onDeleteTemplate = onDeleteTemplate
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            TextButton(onClick = { isSavingTemplate = true }) {
                Text(text = stringResource(R.string.tools_bulk_save_as_template_action))
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        }

        item {
            SizedCategoryDropdown(selected = form.category, onSelected = onCategoryChanged)
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                OutlinedTextField(
                    value = form.brand,
                    onValueChange = onBrandChanged,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.tools_field_brand)) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = form.material,
                    onValueChange = onMaterialChanged,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.tools_field_material)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                FilterChip(
                    selected = form.sizeInputMode == BulkSizeInputMode.RANGE,
                    onClick = { onSizeInputModeChanged(BulkSizeInputMode.RANGE) },
                    label = { Text(text = stringResource(R.string.tools_bulk_mode_range)) }
                )
                FilterChip(
                    selected = form.sizeInputMode == BulkSizeInputMode.CUSTOM_LIST,
                    onClick = { onSizeInputModeChanged(BulkSizeInputMode.CUSTOM_LIST) },
                    label = { Text(text = stringResource(R.string.tools_bulk_mode_custom_list)) }
                )
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        }

        if (form.sizeInputMode == BulkSizeInputMode.RANGE) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                    OutlinedTextField(
                        value = form.rangeStartText,
                        onValueChange = onRangeStartChanged,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text(text = stringResource(R.string.tools_bulk_range_start)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = form.rangeEndText,
                        onValueChange = onRangeEndChanged,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text(text = stringResource(R.string.tools_bulk_range_end)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = form.rangeIncrementText,
                        onValueChange = onRangeIncrementChanged,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text(text = stringResource(R.string.tools_bulk_range_increment)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            }
        } else {
            item {
                OutlinedTextField(
                    value = form.customSizesText,
                    onValueChange = onCustomSizesChanged,
                    label = { Text(text = stringResource(R.string.tools_bulk_custom_sizes)) },
                    supportingText = { Text(text = stringResource(R.string.tools_bulk_custom_sizes_help)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            }
        }

        item {
            OutlinedTextField(
                value = form.quantityPerSizeText,
                onValueChange = onQuantityPerSizeChanged,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(text = stringResource(R.string.tools_bulk_quantity_per_size)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        }

        item {
            OutlinedTextField(
                value = form.storageLocation,
                onValueChange = onStorageLocationChanged,
                singleLine = true,
                label = { Text(text = stringResource(R.string.tools_field_storage_location)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        }

        item {
            OutlinedTextField(
                value = form.notes,
                onValueChange = onNotesChanged,
                minLines = 2,
                label = { Text(text = stringResource(R.string.tools_field_notes)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
        }

        item {
            FilterChip(
                selected = form.createAsSet,
                onClick = { onCreateAsSetChanged(!form.createAsSet) },
                label = { Text(text = stringResource(R.string.tools_bulk_create_as_set)) }
            )
            if (form.createAsSet) {
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = form.setName,
                    onValueChange = onSetNameChanged,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.tools_bulk_set_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
        }

        item {
            Text(
                text = stringResource(R.string.tools_bulk_preview_title, uiState.preview.size),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        }

        if (uiState.preview.isEmpty()) {
            item {
                QuietText(text = stringResource(R.string.tools_bulk_preview_empty))
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            }
        } else {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(StitchbookSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        uiState.preview.forEach { previewItem ->
                            QuietText(text = previewItem.name)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
            }
        }

        uiState.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            }
        }

        item {
            Button(
                onClick = onCreateAll,
                enabled = !uiState.isSaving && uiState.preview.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.tools_bulk_create_action, uiState.preview.size)
                )
            }
        }
    }

    if (isSavingTemplate) {
        SaveTemplateDialog(
            onDismiss = { isSavingTemplate = false },
            onSave = { name ->
                onSaveAsTemplate(name)
                isSavingTemplate = false
            }
        )
    }
}

/** Applying a template only pre-fills the form above -- it never creates a ToolItem on its own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePicker(
    templates: List<ToolTemplate>,
    onApplyTemplate: (ToolTemplate) -> Unit,
    onDeleteTemplate: (ToolTemplate) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    if (templates.isEmpty()) {
        QuietText(text = stringResource(R.string.tools_bulk_no_templates))
        return
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = stringResource(R.string.tools_bulk_load_template_placeholder),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(R.string.tools_bulk_load_template_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            templates.forEach { template ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = template.name)
                            IconButton(
                                onClick = {
                                    onDeleteTemplate(template)
                                    expanded = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(
                                        R.string.tools_bulk_delete_template_action
                                    )
                                )
                            }
                        }
                    },
                    onClick = {
                        onApplyTemplate(template)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SaveTemplateDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var nameIsBlank by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.tools_bulk_save_as_template_action)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameIsBlank = false
                },
                singleLine = true,
                isError = nameIsBlank,
                label = { Text(text = stringResource(R.string.tools_bulk_template_name)) },
                supportingText = if (nameIsBlank) {
                    { Text(text = stringResource(R.string.tools_bulk_template_name_required)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameIsBlank = true
                    } else {
                        onSave(name)
                    }
                }
            ) {
                Text(text = stringResource(R.string.save_tool_set))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SizedCategoryDropdown(
    selected: ToolCategory,
    onSelected: (ToolCategory) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val sizedCategories = remember { ToolCategory.entries.filter { it.usesSizeFields() } }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelResource()),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(R.string.tools_field_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            sizedCategories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(category.labelResource())) },
                    onClick = {
                        onSelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}
