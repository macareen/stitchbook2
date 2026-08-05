package com.macareen.stitchbook2.feature.counters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.domain.model.CounterNote
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.ui.components.LabelPill
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.cardTitle
import com.macareen.stitchbook2.ui.theme.textSecondary

@Composable
fun CountersRoute(viewModel: CountersViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val notesUiState by viewModel.notesUiState.collectAsStateWithLifecycle()

    CountersScreen(
        uiState = uiState,
        notesUiState = notesUiState,
        onSearchQueryChanged = viewModel::updateSearchQuery,
        onSaveCounter = viewModel::saveCounter,
        onIncrement = viewModel::increment,
        onDecrement = viewModel::decrement,
        onReset = viewModel::reset,
        onDeleteCounter = viewModel::deleteCounter,
        onOpenNotes = viewModel::openNotes,
        onCloseNotes = viewModel::closeNotes,
        onAddNote = viewModel::addNote,
        onDeleteNote = viewModel::deleteNote
    )
}

@Composable
fun CountersScreen(
    uiState: CountersUiState,
    notesUiState: CounterNotesUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSaveCounter: (Counter?, CounterFormInput) -> Unit,
    onIncrement: (Counter) -> Unit,
    onDecrement: (Counter) -> Unit,
    onReset: (Counter) -> Unit,
    onDeleteCounter: (Counter) -> Unit,
    onOpenNotes: (Counter) -> Unit,
    onCloseNotes: () -> Unit,
    onAddNote: (Counter, Int, String) -> Unit,
    onDeleteNote: (CounterNote) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingCounter by remember { mutableStateOf<Counter?>(null) }
    var isAddingCounter by remember { mutableStateOf(false) }
    var resettingCounter by remember { mutableStateOf<Counter?>(null) }
    var deletingCounter by remember { mutableStateOf<Counter?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            CountersUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            CountersUiState.Error -> {
                MessageState(
                    title = stringResource(R.string.counters_load_error_title),
                    description = stringResource(R.string.counters_load_error_description),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(StitchbookSpacing.extraLarge)
                )
            }

            is CountersUiState.Content -> {
                CountersContent(
                    uiState = uiState,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onEditCounter = { editingCounter = it },
                    onResetRequested = { resettingCounter = it },
                    onDeleteRequested = { deletingCounter = it },
                    onNotesRequested = onOpenNotes,
                    onIncrement = onIncrement,
                    onDecrement = onDecrement
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { isAddingCounter = true },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.add_counter)
                )
            },
            text = { Text(text = stringResource(R.string.add_counter)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(StitchbookSpacing.medium)
        )
    }

    val projects = (uiState as? CountersUiState.Content)?.projects.orEmpty()

    if (isAddingCounter) {
        CounterDialog(
            original = null,
            projects = projects,
            onDismiss = { isAddingCounter = false },
            onSave = { form ->
                onSaveCounter(null, form)
                isAddingCounter = false
            }
        )
    }

    editingCounter?.let { counter ->
        CounterDialog(
            original = counter,
            projects = projects,
            onDismiss = { editingCounter = null },
            onSave = { form ->
                onSaveCounter(counter, form)
                editingCounter = null
            }
        )
    }

    resettingCounter?.let { counter ->
        AlertDialog(
            onDismissRequest = { resettingCounter = null },
            title = { Text(text = stringResource(R.string.reset_counter_title)) },
            text = { Text(text = stringResource(R.string.reset_counter_confirmation, counter.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset(counter)
                        resettingCounter = null
                    }
                ) {
                    Text(text = stringResource(R.string.counters_reset_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { resettingCounter = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    deletingCounter?.let { counter ->
        AlertDialog(
            onDismissRequest = { deletingCounter = null },
            title = { Text(text = stringResource(R.string.delete_counter_title)) },
            text = { Text(text = stringResource(R.string.delete_counter_confirmation, counter.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCounter(counter)
                        deletingCounter = null
                    }
                ) {
                    Text(text = stringResource(R.string.delete_counter))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCounter = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    (notesUiState as? CounterNotesUiState.Content)?.let { content ->
        CounterNotesDialog(
            counter = content.counter,
            notes = content.notes,
            onDismiss = onCloseNotes,
            onAddNote = { value, note -> onAddNote(content.counter, value, note) },
            onDeleteNote = onDeleteNote
        )
    }
}

@Composable
private fun CountersContent(
    uiState: CountersUiState.Content,
    onSearchQueryChanged: (String) -> Unit,
    onEditCounter: (Counter) -> Unit,
    onResetRequested: (Counter) -> Unit,
    onDeleteRequested: (Counter) -> Unit,
    onNotesRequested: (Counter) -> Unit,
    onIncrement: (Counter) -> Unit,
    onDecrement: (Counter) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = StitchbookSpacing.medium,
            top = StitchbookSpacing.medium,
            end = StitchbookSpacing.medium,
            bottom = 104.dp
        ),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
    ) {
        item {
            Text(
                text = stringResource(R.string.counters_header_title),
                style = MaterialTheme.typography.headlineMedium
            )
            QuietText(text = stringResource(R.string.counters_header_subtitle))
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
        }

        item {
            OutlinedTextField(
                value = uiState.filter.searchQuery,
                onValueChange = onSearchQueryChanged,
                singleLine = true,
                label = { Text(text = stringResource(R.string.counters_search_placeholder)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
        }

        if (uiState.entries.isEmpty()) {
            item {
                MessageState(
                    title = stringResource(R.string.counters_empty_title),
                    description = stringResource(R.string.counters_empty_description)
                )
            }
        } else {
            items(items = uiState.entries, key = { it.counter.id }) { entry ->
                CounterCard(
                    entry = entry,
                    onEdit = { onEditCounter(entry.counter) },
                    onResetRequested = { onResetRequested(entry.counter) },
                    onDeleteRequested = { onDeleteRequested(entry.counter) },
                    onNotesRequested = { onNotesRequested(entry.counter) },
                    onIncrement = { onIncrement(entry.counter) },
                    onDecrement = { onDecrement(entry.counter) }
                )
            }
        }
    }
}

@Composable
private fun CounterCard(
    entry: CounterListEntry,
    onEdit: () -> Unit,
    onResetRequested: () -> Unit,
    onDeleteRequested: () -> Unit,
    onNotesRequested: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val counter = entry.counter
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                entry.projectName?.let { projectName ->
                    LabelPill(
                        text = projectName,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } ?: Spacer(modifier = Modifier)
                LabelPill(
                    text = counter.goal?.let {
                        stringResource(R.string.counters_value_with_goal_pill, counter.currentValue, it, counter.unitLabel)
                    } ?: stringResource(R.string.counters_value_pill, counter.currentValue, counter.unitLabel),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Text(
                text = counter.name,
                style = MaterialTheme.typography.cardTitle
            )

            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDecrement) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = stringResource(R.string.counters_decrement_action)
                    )
                }
                Text(
                    text = counter.currentValue.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = StitchbookSpacing.large)
                )
                IconButton(onClick = onIncrement) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.counters_increment_action)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onResetRequested) {
                    Text(text = stringResource(R.string.counters_reset_action))
                }
                Row {
                    IconButton(onClick = onNotesRequested) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = stringResource(R.string.counter_notes_action)
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.edit_counter)
                        )
                    }
                    IconButton(onClick = onDeleteRequested) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete_counter)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterDialog(
    original: Counter?,
    projects: List<Project>,
    onDismiss: () -> Unit,
    onSave: (CounterFormInput) -> Unit
) {
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var unitLabel by remember { mutableStateOf(original?.unitLabel.orEmpty()) }
    var goalText by remember { mutableStateOf(original?.goal?.toString().orEmpty()) }
    var projectId by remember { mutableStateOf(original?.projectId) }
    var nameIsBlank by remember { mutableStateOf(false) }
    var unitLabelIsBlank by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (original == null) {
                    stringResource(R.string.counters_form_title_add)
                } else {
                    stringResource(R.string.counters_form_title_edit)
                }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameIsBlank = false
                    },
                    singleLine = true,
                    isError = nameIsBlank,
                    label = { Text(text = stringResource(R.string.counters_field_name)) },
                    supportingText = if (nameIsBlank) {
                        { Text(text = stringResource(R.string.counters_field_name_required)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = unitLabel,
                    onValueChange = {
                        unitLabel = it
                        unitLabelIsBlank = false
                    },
                    singleLine = true,
                    isError = unitLabelIsBlank,
                    label = { Text(text = stringResource(R.string.counters_field_unit_label)) },
                    supportingText = if (unitLabelIsBlank) {
                        { Text(text = stringResource(R.string.counters_field_unit_label_required)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.counters_field_goal)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                ProjectDropdown(
                    projects = projects,
                    selectedProjectId = projectId,
                    onSelected = { projectId = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = name.trim()
                    val trimmedUnitLabel = unitLabel.trim()
                    nameIsBlank = trimmedName.isEmpty()
                    unitLabelIsBlank = trimmedUnitLabel.isEmpty()
                    if (nameIsBlank || unitLabelIsBlank) return@TextButton
                    onSave(
                        CounterFormInput(
                            name = trimmedName,
                            unitLabel = trimmedUnitLabel,
                            goalText = goalText,
                            projectId = projectId
                        )
                    )
                }
            ) {
                Text(text = stringResource(R.string.counters_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDropdown(
    projects: List<Project>,
    selectedProjectId: String?,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = projects.firstOrNull { it.id == selectedProjectId }?.name
        ?: stringResource(R.string.counters_field_project_none)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(R.string.counters_field_project_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.counters_field_project_none)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            projects.forEach { project ->
                DropdownMenuItem(
                    text = { Text(text = project.name) },
                    onClick = {
                        onSelected(project.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CounterNotesDialog(
    counter: Counter,
    notes: List<CounterNote>,
    onDismiss: () -> Unit,
    onAddNote: (Int, String) -> Unit,
    onDeleteNote: (CounterNote) -> Unit
) {
    var valueText by remember(counter.id) { mutableStateOf(counter.currentValue.toString()) }
    var noteText by remember(counter.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.counter_notes_title, counter.name)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
                ) {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.counter_notes_field_value)) },
                        modifier = Modifier.width(88.dp)
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(text = stringResource(R.string.counter_notes_field_note)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                TextButton(
                    onClick = {
                        onAddNote(valueText.toIntOrNull() ?: counter.currentValue, noteText)
                        noteText = ""
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(text = stringResource(R.string.counter_notes_add_action))
                }

                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                if (notes.isEmpty()) {
                    QuietText(text = stringResource(R.string.counter_notes_empty))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                        notes.forEach { note ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.counter_notes_entry,
                                        note.value,
                                        counter.unitLabel,
                                        note.note
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onDeleteNote(note) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.counter_notes_delete_action)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.counter_notes_close_action))
            }
        }
    )
}

@Composable
private fun MessageState(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.textSecondary
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CountersScreenPreview() {
    StitchbookTheme {
        CountersScreen(
            uiState = CountersUiState.Content(
                entries = listOf(
                    CounterListEntry(
                        counter = Counter(
                            id = "preview",
                            projectId = "project-1",
                            name = "Right Sleeve",
                            unitLabel = "rows",
                            currentValue = 12,
                            goal = 60,
                            createdAt = 0,
                            updatedAt = 0
                        ),
                        projectName = "Everyday Cardigan"
                    ),
                    CounterListEntry(
                        counter = Counter(
                            id = "preview-2",
                            projectId = null,
                            name = "Cable Repeat",
                            unitLabel = "repeats",
                            currentValue = 3,
                            goal = null,
                            createdAt = 0,
                            updatedAt = 0
                        ),
                        projectName = null
                    )
                ),
                filter = CounterFilterState(),
                projects = listOf(
                    Project(
                        id = "project-1",
                        name = "Everyday Cardigan",
                        craft = Craft.KNITTING,
                        projectType = ProjectType.CARDIGAN,
                        status = ProjectStatus.ACTIVE,
                        notes = null,
                        createdAt = 0,
                        updatedAt = 0
                    )
                ),
                hasAnyCounters = true
            ),
            notesUiState = CounterNotesUiState.Closed,
            onSearchQueryChanged = {},
            onSaveCounter = { _, _ -> },
            onIncrement = {},
            onDecrement = {},
            onReset = {},
            onDeleteCounter = {},
            onOpenNotes = {},
            onCloseNotes = {},
            onAddNote = { _, _, _ -> },
            onDeleteNote = {}
        )
    }
}
