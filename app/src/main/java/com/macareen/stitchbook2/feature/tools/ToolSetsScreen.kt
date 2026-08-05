package com.macareen.stitchbook2.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.ToolSet
import com.macareen.stitchbook2.ui.components.LabelPill
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.cardTitle
import com.macareen.stitchbook2.ui.theme.textSecondary

@Composable
fun ToolSetsRoute(viewModel: ToolSetsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ToolSetsScreen(
        uiState = uiState,
        onRenameSet = viewModel::renameSet,
        onDeleteSet = viewModel::deleteSet
    )
}

@Composable
fun ToolSetsScreen(
    uiState: ToolSetsUiState,
    onRenameSet: (ToolSet, String, String, String) -> Unit,
    onDeleteSet: (ToolSet) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingSet by remember { mutableStateOf<ToolSet?>(null) }
    var deletingSet by remember { mutableStateOf<ToolSet?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            ToolSetsUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            ToolSetsUiState.Error -> {
                MessageState(
                    title = stringResource(R.string.tools_load_error_title),
                    description = stringResource(R.string.tools_load_error_description),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(StitchbookSpacing.extraLarge)
                )
            }

            is ToolSetsUiState.Content -> {
                ToolSetsContent(
                    summaries = uiState.summaries,
                    onEditSet = { editingSet = it },
                    onDeleteRequested = { deletingSet = it }
                )
            }
        }
    }

    editingSet?.let { set ->
        ToolSetDialog(
            set = set,
            onDismiss = { editingSet = null },
            onSave = { name, brand, notes ->
                onRenameSet(set, name, brand, notes)
                editingSet = null
            }
        )
    }

    deletingSet?.let { set ->
        AlertDialog(
            onDismissRequest = { deletingSet = null },
            title = { Text(text = stringResource(R.string.delete_tool_set_title)) },
            text = { Text(text = stringResource(R.string.delete_tool_set_confirmation, set.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSet(set)
                        deletingSet = null
                    }
                ) {
                    Text(text = stringResource(R.string.delete_tool_set))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSet = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ToolSetsContent(
    summaries: List<ToolSetSummary>,
    onEditSet: (ToolSet) -> Unit,
    onDeleteRequested: (ToolSet) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(StitchbookSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
    ) {
        item {
            Text(
                text = stringResource(R.string.tool_sets_header_title),
                style = MaterialTheme.typography.headlineMedium
            )
            QuietText(text = stringResource(R.string.tool_sets_header_subtitle))
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
        }

        if (summaries.isEmpty()) {
            item {
                MessageState(
                    title = stringResource(R.string.tool_sets_empty_title),
                    description = stringResource(R.string.tool_sets_empty_description)
                )
            }
        } else {
            items(items = summaries, key = { it.set.id }) { summary ->
                ToolSetCard(
                    summary = summary,
                    onEdit = { onEditSet(summary.set) },
                    onDelete = { onDeleteRequested(summary.set) }
                )
            }
        }
    }
}

@Composable
private fun ToolSetCard(
    summary: ToolSetSummary,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
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
                Text(
                    text = summary.set.name,
                    style = MaterialTheme.typography.cardTitle,
                    fontWeight = FontWeight.SemiBold
                )
                LabelPill(
                    text = stringResource(R.string.tool_set_item_count, summary.itemCount),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.textSecondary
                )
            }
            summary.set.brand?.let { QuietText(text = it) }
            summary.set.notes?.let { notes ->
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                QuietText(text = notes)
            }

            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.edit_tool_set)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete_tool_set)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolSetDialog(
    set: ToolSet,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(set.name) }
    var brand by remember { mutableStateOf(set.brand.orEmpty()) }
    var notes by remember { mutableStateOf(set.notes.orEmpty()) }
    var nameIsBlank by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.tool_set_form_title_edit)) },
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
                    label = { Text(text = stringResource(R.string.tool_set_field_name)) },
                    supportingText = if (nameIsBlank) {
                        { Text(text = stringResource(R.string.tool_set_field_name_required)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.tool_set_field_brand)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    minLines = 3,
                    label = { Text(text = stringResource(R.string.tool_set_field_notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameIsBlank = true
                    } else {
                        onSave(name, brand, notes)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ToolSetsScreenPreview() {
    StitchbookTheme {
        ToolSetsScreen(
            uiState = ToolSetsUiState.Content(
                listOf(
                    ToolSetSummary(
                        set = ToolSet(
                            id = "preview",
                            name = "ChiaoGoo Twist Red Lace set",
                            brand = "ChiaoGoo",
                            notes = "Complete 5\" and 3.5\" tips",
                            createdAt = 0,
                            updatedAt = 0
                        ),
                        itemCount = 11
                    )
                )
            ),
            onRenameSet = { _, _, _, _ -> },
            onDeleteSet = {}
        )
    }
}
