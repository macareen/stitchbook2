package com.macareen.stitchbook2.feature.draft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.guide.DraftNode
import com.macareen.stitchbook2.domain.guide.DraftNodeType
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SecondaryActionButton
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme

@Composable
fun DraftEditorRoute(
    viewModel: DraftEditorViewModel,
    onDone: () -> Unit,
    onStartOrContinue: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DraftEditorScreen(
        uiState = uiState,
        onAddNode = viewModel::addNode,
        onUpdateNode = viewModel::updateNode,
        onDeleteNode = viewModel::deleteNode,
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onPublish = viewModel::publish,
        onDismissError = viewModel::dismissError,
        onDone = onDone,
        onStartOrContinue = onStartOrContinue
    )
}

@Composable
fun DraftEditorScreen(
    uiState: DraftEditorUiState,
    onAddNode: (
        type: DraftNodeType,
        parentId: NodeId?,
        title: String?,
        instructionText: String?,
        rangeUnitLabel: String?,
        rangeStartInclusive: Int?,
        rangeEndInclusive: Int?,
        repeatCount: Int?,
        repeatLabel: String?
    ) -> Unit,
    onUpdateNode: (
        nodeId: NodeId,
        title: String?,
        instructionText: String?,
        rangeUnitLabel: String?,
        rangeStartInclusive: Int?,
        rangeEndInclusive: Int?,
        repeatCount: Int?,
        repeatLabel: String?
    ) -> Unit,
    onDeleteNode: (NodeId) -> Unit,
    onMoveUp: (NodeId) -> Unit,
    onMoveDown: (NodeId) -> Unit,
    onPublish: () -> Unit,
    onDismissError: () -> Unit,
    onDone: () -> Unit,
    onStartOrContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        DraftEditorUiState.Loading -> {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        DraftEditorUiState.NotFound -> {
            EditorMessage(
                title = stringResource(R.string.guide_not_found_title),
                description = stringResource(R.string.guide_not_found_description),
                modifier = modifier
            )
        }

        DraftEditorUiState.LoadError -> {
            EditorMessage(
                title = stringResource(R.string.guide_load_error_title),
                description = stringResource(R.string.guide_load_error_description),
                modifier = modifier
            )
        }

        is DraftEditorUiState.Content -> {
            DraftEditorContent(
                state = uiState,
                onAddNode = onAddNode,
                onUpdateNode = onUpdateNode,
                onDeleteNode = onDeleteNode,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onPublish = onPublish,
                onDismissError = onDismissError,
                onDone = onDone,
                onStartOrContinue = onStartOrContinue,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun DraftEditorContent(
    state: DraftEditorUiState.Content,
    onAddNode: (
        type: DraftNodeType,
        parentId: NodeId?,
        title: String?,
        instructionText: String?,
        rangeUnitLabel: String?,
        rangeStartInclusive: Int?,
        rangeEndInclusive: Int?,
        repeatCount: Int?,
        repeatLabel: String?
    ) -> Unit,
    onUpdateNode: (
        nodeId: NodeId,
        title: String?,
        instructionText: String?,
        rangeUnitLabel: String?,
        rangeStartInclusive: Int?,
        rangeEndInclusive: Int?,
        repeatCount: Int?,
        repeatLabel: String?
    ) -> Unit,
    onDeleteNode: (NodeId) -> Unit,
    onMoveUp: (NodeId) -> Unit,
    onMoveDown: (NodeId) -> Unit,
    onPublish: () -> Unit,
    onDismissError: () -> Unit,
    onDone: () -> Unit,
    onStartOrContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dialogState by remember { mutableStateOf<EditorDialogState?>(null) }
    var pendingDelete by remember { mutableStateOf<DraftNode?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(StitchbookSpacing.large)) {
        Text(text = state.guideName, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))

        state.errorMessage?.let { message ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = StitchbookSpacing.small)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(StitchbookSpacing.medium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissError) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            }
        }

        if (state.rows.isEmpty()) {
            QuietText(
                text = stringResource(R.string.draft_editor_empty_description),
                modifier = Modifier.padding(bottom = StitchbookSpacing.medium)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.rows, key = { it.node.id.value }) { row ->
                    DraftOutlineRowItem(
                        row = row,
                        isSaving = state.isSaving,
                        onEdit = { dialogState = EditorDialogState.editing(row.node) },
                        onDelete = { pendingDelete = row.node },
                        onMoveUp = { onMoveUp(row.node.id) },
                        onMoveDown = { onMoveDown(row.node.id) },
                        onAddInside = { dialogState = EditorDialogState.ChoosingType(row.node.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(StitchbookSpacing.medium))

        // Primary action hierarchy: before publication, Publish is the one
        // obvious next step; once published, Start/Continue Knitting takes
        // over as the standout call to action (Publish stays available,
        // de-emphasized, since the Draft remains editable and a later
        // correction needs a way to become a new Revision too).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
        ) {
            SecondaryActionButton(
                text = stringResource(R.string.draft_editor_add_step),
                onClick = { dialogState = EditorDialogState.ChoosingType(parentId = null) },
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f)
            )
            if (state.isPublished) {
                SecondaryActionButton(
                    text = stringResource(R.string.draft_publish_action),
                    onClick = onPublish,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f)
                )
            } else {
                PrimaryActionButton(
                    text = stringResource(R.string.draft_publish_action),
                    onClick = onPublish,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (state.isPublished) {
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            PrimaryActionButton(
                text = if (state.hasActiveExecution) {
                    stringResource(R.string.draft_continue_knitting_action)
                } else {
                    stringResource(R.string.draft_start_knitting_action)
                },
                onClick = onStartOrContinue,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(StitchbookSpacing.extraSmall))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.draft_editor_done_action))
        }
    }

    dialogState?.let { current ->
        when (current) {
            is EditorDialogState.ChoosingType -> ChooseTypeDialog(
                onChoose = { type -> dialogState = EditorDialogState.creating(type, current.parentId) },
                onDismiss = { dialogState = null }
            )

            is EditorDialogState.EditingFields -> NodeFieldsDialog(
                state = current,
                onChange = { dialogState = it },
                onConfirm = {
                    if (current.editingNodeId == null) {
                        onAddNode(
                            current.type,
                            current.parentId,
                            current.titleOrNull(),
                            current.instructionTextOrNull(),
                            current.rangeUnitLabelOrNull(),
                            current.rangeStart.toIntOrNull(),
                            current.rangeEnd.toIntOrNull(),
                            current.repeatCount.toIntOrNull(),
                            current.repeatLabelOrNull()
                        )
                    } else {
                        onUpdateNode(
                            current.editingNodeId,
                            current.titleOrNull(),
                            current.instructionTextOrNull(),
                            current.rangeUnitLabelOrNull(),
                            current.rangeStart.toIntOrNull(),
                            current.rangeEnd.toIntOrNull(),
                            current.repeatCount.toIntOrNull(),
                            current.repeatLabelOrNull()
                        )
                    }
                    dialogState = null
                },
                onDismiss = { dialogState = null }
            )
        }
    }

    pendingDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = stringResource(R.string.draft_delete_step_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.draft_delete_step_confirmation,
                        node.summary()
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteNode(node.id)
                        pendingDelete = null
                    }
                ) {
                    Text(text = stringResource(R.string.draft_delete_step_title))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DraftOutlineRowItem(
    row: DraftOutlineRow,
    isSaving: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAddInside: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = StitchbookSpacing.medium * row.depth,
                bottom = StitchbookSpacing.small
            )
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.medium)) {
            Text(text = row.node.summary(), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(StitchbookSpacing.extraSmall))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = !isSaving && row.canMoveUp) {
                    Icon(imageVector = Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.draft_move_up_action))
                }
                IconButton(onClick = onMoveDown, enabled = !isSaving && row.canMoveDown) {
                    Icon(imageVector = Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.draft_move_down_action))
                }
                if (row.node.type != DraftNodeType.INSTRUCTION) {
                    IconButton(onClick = onAddInside, enabled = !isSaving) {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = stringResource(R.string.draft_add_inside_action))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit, enabled = !isSaving) {
                    Icon(imageVector = Icons.Outlined.Edit, contentDescription = stringResource(R.string.draft_edit_step_action))
                }
                IconButton(onClick = onDelete, enabled = !isSaving) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = stringResource(R.string.draft_delete_step_title))
                }
            }
        }
    }
}

@Composable
private fun ChooseTypeDialog(
    onChoose: (DraftNodeType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.draft_choose_type_title)) },
        text = {
            Column {
                DraftNodeType.entries.forEach { type ->
                    // The hint is a sibling of the TextButton, not a child
                    // inside it: Compose only merges a clickable node's own
                    // descendants into its semantics, so this keeps the
                    // button discoverable by its type name alone.
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onChoose(type) }, modifier = Modifier.fillMaxWidth()) {
                            Text(text = type.label(), modifier = Modifier.weight(1f))
                        }
                        QuietText(
                            text = type.hint(),
                            modifier = Modifier.padding(
                                start = StitchbookSpacing.medium,
                                bottom = StitchbookSpacing.small
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun NodeFieldsDialog(
    state: EditorDialogState.EditingFields,
    onChange: (EditorDialogState.EditingFields) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (state.editingNodeId == null) {
                    stringResource(R.string.draft_editor_add_step)
                } else {
                    stringResource(R.string.draft_edit_step_action)
                }
            )
        },
        text = {
            Column {
                when (state.type) {
                    DraftNodeType.SECTION -> OutlinedTextField(
                        value = state.title,
                        onValueChange = { onChange(state.copy(title = it)) },
                        label = { Text(text = stringResource(R.string.draft_field_title)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    DraftNodeType.INSTRUCTION -> OutlinedTextField(
                        value = state.instructionText,
                        onValueChange = { onChange(state.copy(instructionText = it)) },
                        label = { Text(text = stringResource(R.string.draft_field_instruction_text)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    DraftNodeType.RANGE -> Column {
                        OutlinedTextField(
                            value = state.rangeUnitLabel,
                            onValueChange = { onChange(state.copy(rangeUnitLabel = it)) },
                            label = { Text(text = stringResource(R.string.draft_field_range_unit_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = state.rangeStart,
                                onValueChange = { onChange(state.copy(rangeStart = it.filterDigits())) },
                                label = { Text(text = stringResource(R.string.draft_field_range_start)) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(StitchbookSpacing.small))
                            OutlinedTextField(
                                value = state.rangeEnd,
                                onValueChange = { onChange(state.copy(rangeEnd = it.filterDigits())) },
                                label = { Text(text = stringResource(R.string.draft_field_range_end)) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    DraftNodeType.REPEAT -> Column {
                        OutlinedTextField(
                            value = state.repeatCount,
                            onValueChange = { onChange(state.copy(repeatCount = it.filterDigits())) },
                            label = { Text(text = stringResource(R.string.draft_field_repeat_count)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.repeatLabel,
                            onValueChange = { onChange(state.copy(repeatLabel = it)) },
                            label = { Text(text = stringResource(R.string.draft_field_repeat_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.isValid()) {
                Text(
                    text = if (state.editingNodeId == null) {
                        stringResource(R.string.draft_action_add)
                    } else {
                        stringResource(R.string.draft_action_save)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun EditorMessage(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(StitchbookSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        Text(text = description, style = MaterialTheme.typography.bodyLarge)
    }
}

private sealed interface EditorDialogState {
    data class ChoosingType(val parentId: NodeId?) : EditorDialogState

    data class EditingFields(
        val type: DraftNodeType,
        val parentId: NodeId?,
        val editingNodeId: NodeId?,
        val title: String = "",
        val instructionText: String = "",
        val rangeUnitLabel: String = "",
        val rangeStart: String = "",
        val rangeEnd: String = "",
        val repeatCount: String = "",
        val repeatLabel: String = ""
    ) : EditorDialogState {
        fun titleOrNull() = title.trim().ifEmpty { null }
        fun instructionTextOrNull() = instructionText.trim().ifEmpty { null }
        fun rangeUnitLabelOrNull() = rangeUnitLabel.trim().ifEmpty { null }
        fun repeatLabelOrNull() = repeatLabel.trim().ifEmpty { null }

        fun isValid(): Boolean = when (type) {
            DraftNodeType.SECTION -> titleOrNull() != null
            DraftNodeType.INSTRUCTION -> instructionTextOrNull() != null
            DraftNodeType.RANGE -> {
                val start = rangeStart.toIntOrNull()
                val end = rangeEnd.toIntOrNull()
                rangeUnitLabelOrNull() != null && start != null && end != null && start <= end
            }
            DraftNodeType.REPEAT -> (repeatCount.toIntOrNull() ?: 0) > 0
        }
    }

    companion object {
        fun creating(type: DraftNodeType, parentId: NodeId?) = EditingFields(
            type = type,
            parentId = parentId,
            editingNodeId = null
        )

        fun editing(node: DraftNode) = EditingFields(
            type = node.type,
            parentId = null,
            editingNodeId = node.id,
            title = node.title.orEmpty(),
            instructionText = node.instructionText.orEmpty(),
            rangeUnitLabel = node.rangeUnitLabel.orEmpty(),
            rangeStart = node.rangeStartInclusive?.toString().orEmpty(),
            rangeEnd = node.rangeEndInclusive?.toString().orEmpty(),
            repeatCount = node.repeatCount?.toString().orEmpty(),
            repeatLabel = node.repeatLabel.orEmpty()
        )
    }
}

private fun String.filterDigits() = filter(Char::isDigit)

@Composable
private fun DraftNodeType.label(): String = when (this) {
    DraftNodeType.SECTION -> stringResource(R.string.draft_node_type_section)
    DraftNodeType.RANGE -> stringResource(R.string.draft_node_type_range)
    DraftNodeType.REPEAT -> stringResource(R.string.draft_node_type_repeat)
    DraftNodeType.INSTRUCTION -> stringResource(R.string.draft_node_type_instruction)
}

@Composable
private fun DraftNodeType.hint(): String = when (this) {
    DraftNodeType.SECTION -> stringResource(R.string.draft_node_type_section_hint)
    DraftNodeType.RANGE -> stringResource(R.string.draft_node_type_range_hint)
    DraftNodeType.REPEAT -> stringResource(R.string.draft_node_type_repeat_hint)
    DraftNodeType.INSTRUCTION -> stringResource(R.string.draft_node_type_instruction_hint)
}

private fun DraftNode.summary(): String = when (type) {
    DraftNodeType.SECTION -> title.orEmpty()
    DraftNodeType.INSTRUCTION -> instructionText.orEmpty()
    DraftNodeType.RANGE -> "${rangeUnitLabel.orEmpty()} $rangeStartInclusive–$rangeEndInclusive"
    DraftNodeType.REPEAT -> if (repeatLabel.isNullOrBlank()) {
        "×$repeatCount"
    } else {
        "$repeatLabel: ×$repeatCount"
    }
}

@Preview(showBackground = true)
@Composable
private fun DraftEditorPreview() {
    StitchbookTheme {
        DraftEditorScreen(
            uiState = DraftEditorUiState.Content(
                guideName = "Everyday cardigan",
                rows = listOf(
                    DraftOutlineRow(
                        node = DraftNode(
                            id = NodeId("range"),
                            type = DraftNodeType.RANGE,
                            rangeUnitLabel = "row",
                            rangeStartInclusive = 1,
                            rangeEndInclusive = 2,
                            children = listOf(NodeId("instruction"))
                        ),
                        depth = 0,
                        canMoveUp = false,
                        canMoveDown = false
                    ),
                    DraftOutlineRow(
                        node = DraftNode(
                            id = NodeId("instruction"),
                            type = DraftNodeType.INSTRUCTION,
                            instructionText = "Cast on 40 stitches"
                        ),
                        depth = 1,
                        canMoveUp = false,
                        canMoveDown = false
                    )
                )
            ),
            onAddNode = { _, _, _, _, _, _, _, _, _ -> },
            onUpdateNode = { _, _, _, _, _, _, _, _ -> },
            onDeleteNode = {},
            onMoveUp = {},
            onMoveDown = {},
            onPublish = {},
            onDismissError = {},
            onDone = {},
            onStartOrContinue = {}
        )
    }
}
