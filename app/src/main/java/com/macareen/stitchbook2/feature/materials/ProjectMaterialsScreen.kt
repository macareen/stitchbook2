package com.macareen.stitchbook2.feature.materials

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.model.YarnAllocation
import com.macareen.stitchbook2.ui.components.ContentCard
import com.macareen.stitchbook2.ui.components.EmptyState
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SectionHeader
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.cardTitle

@Composable
fun ProjectMaterialsRoute(viewModel: ProjectMaterialsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProjectMaterialsScreen(
        uiState = uiState,
        onAllocate = viewModel::allocate,
        onRecordUse = viewModel::recordUse,
        onRelease = viewModel::release,
        onLinkPattern = viewModel::linkPattern,
        onUnlinkPattern = viewModel::unlinkPattern,
        onDismissFeedback = viewModel::dismissFeedback
    )
}

@Composable
fun ProjectMaterialsScreen(
    uiState: ProjectMaterialsUiState,
    onAllocate: (stashItemId: String, amount: String, notes: String) -> Unit,
    onRecordUse: (allocationId: String, amount: String) -> Unit,
    onRelease: (YarnAllocation) -> Unit,
    onLinkPattern: (String) -> Unit,
    onUnlinkPattern: (String) -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAllocate by remember { mutableStateOf(false) }
    var useTarget by remember { mutableStateOf<AllocationRow?>(null) }
    var showLinkPattern by remember { mutableStateOf(false) }

    if (uiState.isLoading) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(StitchbookSpacing.large),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium)
    ) {
        item {
            QuietText(text = uiState.projectName)
            Text(
                text = stringResource(R.string.materials_title),
                style = MaterialTheme.typography.headlineMedium
            )
        }

        uiState.feedback?.let { message ->
            item {
                FeedbackLine(message = message, onDismiss = onDismissFeedback)
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.materials_yarn_section),
                icon = Icons.Outlined.Inventory2,
                actionLabel = if (uiState.availableStash.isNotEmpty()) stringResource(R.string.materials_allocate) else null,
                onAction = { showAllocate = true }
            )
        }
        if (uiState.allocations.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.materials_yarn_empty_title),
                    description = if (uiState.availableStash.isEmpty()) {
                        stringResource(R.string.materials_yarn_empty_no_stash)
                    } else {
                        stringResource(R.string.materials_yarn_empty_description)
                    },
                    icon = Icons.Outlined.Inventory2
                )
            }
        } else {
            items(uiState.allocations, key = { it.allocation.id }) { row ->
                AllocationCard(
                    row = row,
                    onRecordUse = { useTarget = row },
                    onRelease = { onRelease(row.allocation) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            SectionHeader(
                title = stringResource(R.string.materials_patterns_section),
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                actionLabel = if (uiState.unlinkedPatterns.isNotEmpty()) stringResource(R.string.materials_link_pattern) else null,
                onAction = { showLinkPattern = true }
            )
        }
        if (uiState.linkedPatterns.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.materials_patterns_empty_title),
                    description = stringResource(R.string.materials_patterns_empty_description),
                    icon = Icons.AutoMirrored.Outlined.MenuBook
                )
            }
        } else {
            items(uiState.linkedPatterns, key = { it.id }) { pattern ->
                PatternCard(pattern = pattern, onUnlink = { onUnlinkPattern(pattern.id) })
            }
        }
    }

    if (showAllocate) {
        AllocateDialog(
            available = uiState.availableStash,
            onConfirm = { id, amount, notes ->
                showAllocate = false
                onAllocate(id, amount, notes)
            },
            onDismiss = { showAllocate = false }
        )
    }

    useTarget?.let { row ->
        AmountDialog(
            title = stringResource(R.string.materials_record_use_title, row.item.name),
            description = stringResource(
                R.string.materials_record_use_description,
                formatAmount(row.allocation.quantityReserved),
                row.item.unitLabel
            ),
            confirmLabel = stringResource(R.string.materials_record_use),
            onConfirm = { amount ->
                useTarget = null
                onRecordUse(row.allocation.id, amount)
            },
            onDismiss = { useTarget = null }
        )
    }

    if (showLinkPattern) {
        AlertDialog(
            onDismissRequest = { showLinkPattern = false },
            title = { Text(text = stringResource(R.string.materials_link_pattern)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    uiState.unlinkedPatterns.forEach { pattern ->
                        Text(
                            text = pattern.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable {
                                    showLinkPattern = false
                                    onLinkPattern(pattern.id)
                                }
                                .padding(vertical = StitchbookSpacing.small)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLinkPattern = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AllocationCard(
    row: AllocationRow,
    onRecordUse: () -> Unit,
    onRelease: () -> Unit
) {
    ContentCard {
        Text(text = row.item.name, style = MaterialTheme.typography.cardTitle)
        val subtitle = listOfNotNull(row.item.brand, row.item.colorway, row.item.dyeLot).joinToString(" · ")
        if (subtitle.isNotEmpty()) QuietText(text = subtitle)
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        Text(
            text = stringResource(
                R.string.materials_reserved_used,
                formatAmount(row.allocation.quantityReserved),
                formatAmount(row.allocation.quantityUsed),
                row.item.unitLabel
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        row.allocation.notes?.let { QuietText(text = it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onRelease) { Text(text = stringResource(R.string.materials_release)) }
            TextButton(onClick = onRecordUse) { Text(text = stringResource(R.string.materials_record_use)) }
        }
    }
}

@Composable
private fun PatternCard(pattern: LibraryItem, onUnlink: () -> Unit) {
    ContentCard {
        Text(text = pattern.title, style = MaterialTheme.typography.cardTitle)
        pattern.author?.let { QuietText(text = it) }
        val facts = listOfNotNull(pattern.gauge, pattern.sizes).joinToString(" · ")
        if (facts.isNotEmpty()) QuietText(text = facts)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onUnlink) { Text(text = stringResource(R.string.materials_unlink)) }
        }
    }
}

@Composable
private fun FeedbackLine(message: MaterialsFeedback, onDismiss: () -> Unit) {
    val text = when (message) {
        MaterialsFeedback.INVALID_AMOUNT -> stringResource(R.string.materials_error_invalid_amount)
        MaterialsFeedback.EXCEEDS_UNALLOCATED -> stringResource(R.string.materials_error_exceeds_unallocated)
        MaterialsFeedback.EXCEEDS_AVAILABLE -> stringResource(R.string.materials_error_exceeds_available)
        MaterialsFeedback.SAVE_FAILED -> stringResource(R.string.materials_error_save_failed)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.dismiss)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllocateDialog(
    available: List<AvailableStashItem>,
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(available.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.materials_allocate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selected?.item?.name.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(text = stringResource(R.string.materials_stash_item)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        available.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(
                                            R.string.materials_stash_option,
                                            option.item.name,
                                            formatAmount(option.unallocated),
                                            option.item.unitLabel
                                        )
                                    )
                                },
                                onClick = {
                                    selected = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = {
                        Text(
                            text = stringResource(
                                R.string.materials_amount_label,
                                selected?.item?.unitLabel.orEmpty()
                            )
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(text = stringResource(R.string.materials_notes_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onConfirm(it.item.id, amount, notes) } },
                enabled = selected != null && amount.isNotBlank()
            ) { Text(text = stringResource(R.string.materials_allocate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun AmountDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(amount) }, enabled = amount.isNotBlank()) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        }
    )
}

internal fun formatAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(java.util.Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')

@Preview(showBackground = true)
@Composable
private fun ProjectMaterialsEmptyPreview() {
    StitchbookTheme {
        ProjectMaterialsScreen(
            uiState = ProjectMaterialsUiState(isLoading = false, projectName = "Harbour cardigan"),
            onAllocate = { _, _, _ -> },
            onRecordUse = { _, _ -> },
            onRelease = {},
            onLinkPattern = {},
            onUnlinkPattern = {},
            onDismissFeedback = {}
        )
    }
}
