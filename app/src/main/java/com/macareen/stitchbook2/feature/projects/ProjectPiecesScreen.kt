package com.macareen.stitchbook2.feature.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.ProjectPiece

@Composable
fun ProjectPiecesRoute(viewModel: ProjectPiecesViewModel, onOpen: (ProjectPiece) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProjectPiecesScreen(state, viewModel::add, viewModel::edit, viewModel::archive, onOpen, onBack,
        viewModel::name, viewModel::guide, viewModel::save, viewModel::dismiss, viewModel::refresh)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectPiecesScreen(state: ProjectPiecesState, onAdd: () -> Unit, onEdit: (ProjectPiece) -> Unit,
    onArchive: (ProjectPiece) -> Unit, onOpen: (ProjectPiece) -> Unit, onBack: () -> Unit,
    onName: (String) -> Unit, onGuide: (String) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit,
    onRetry: () -> Unit) {
    var showArchived by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize().testTag("pieces_list"), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.pieces_back)) } }
            item { Text(stringResource(R.string.pieces_title), style = MaterialTheme.typography.headlineLarge) }
            item { Text(stringResource(R.string.pieces_intro), style = MaterialTheme.typography.bodyLarge) }
            if (state.loading) item { CircularProgressIndicator() }
            if (state.failed) item {
                Text(stringResource(R.string.pieces_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.pieces_retry)) }
            }
            item { Button(onClick = onAdd, enabled = !state.loading && state.choices.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pieces_add)) } }
            if (!state.loading && state.choices.isEmpty()) item { Text(stringResource(R.string.pieces_need_guide)) }
            item { FilterChip(selected = showArchived, onClick = { showArchived = !showArchived }, label = { Text(stringResource(R.string.pieces_show_archived)) }) }
            if (!state.loading && state.entries.none { showArchived || !it.piece.isArchived }) item { Text(stringResource(R.string.pieces_empty)) }
            items(state.entries.filter { showArchived || !it.piece.isArchived }, key = { it.piece.id }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(entry.piece.name, style = MaterialTheme.typography.titleLarge)
                        Text(entry.guideName, style = MaterialTheme.typography.bodyLarge)
                        when {
                            entry.piece.isArchived -> Text(stringResource(R.string.pieces_archived))
                            entry.isComplete -> Text(stringResource(R.string.pieces_finished))
                            entry.isStarted -> Text(pluralStringResource(R.plurals.pieces_completed_steps, entry.completedSteps, entry.completedSteps))
                            else -> Text(stringResource(R.string.pieces_not_started))
                        }
                        entry.revisionNumber?.let { Text(stringResource(R.string.pieces_revision, it)) }
                        if (!entry.piece.isArchived) Button(onClick = { onOpen(entry.piece) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (entry.isStarted) R.string.pieces_open else R.string.pieces_start))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { onEdit(entry.piece) }, enabled = state.busyPieceId == null) { Text(stringResource(R.string.pieces_rename)) }
                            TextButton(onClick = { onArchive(entry.piece) }, enabled = state.busyPieceId == null) {
                                Text(stringResource(if (entry.piece.isArchived) R.string.pieces_restore else R.string.pieces_archive))
                            }
                        }
                    }
                }
            }
        }
        state.editor?.let { editor ->
            val scroll = rememberScrollState()
            LaunchedEffect(editor.error) { if (editor.error) scroll.animateScrollTo(0) }
            AlertDialog(onDismissRequest = { if (!editor.busy) onDismiss() },
                title = { Text(stringResource(if (editor.expected == null) R.string.pieces_add else R.string.pieces_rename)) },
                text = {
                    val keyboard = LocalSoftwareKeyboardController.current
                    val focusManager = LocalFocusManager.current
                    Column(Modifier.heightIn(max = 400.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (editor.error) Text(stringResource(R.string.pieces_save_error), color = MaterialTheme.colorScheme.error)
                        OutlinedTextField(value = editor.name, onValueChange = onName, enabled = !editor.busy, singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() }),
                            label = { Text(stringResource(R.string.pieces_name)) }, modifier = Modifier.fillMaxWidth())
                        if (editor.expected == null) {
                            Text(stringResource(R.string.pieces_choose_guide))
                            state.choices.forEach { choice ->
                                Row(Modifier.fillMaxWidth().selectable(selected = editor.guideId == choice.id, enabled = !editor.busy, onClick = { onGuide(choice.id) }).padding(vertical = 8.dp)) {
                                    RadioButton(selected = editor.guideId == choice.id, onClick = null)
                                    Text(choice.name, Modifier.weight(1f).padding(start = 8.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onSave, enabled = editor.canSave) { Text(stringResource(R.string.pieces_save)) } },
                dismissButton = { TextButton(onClick = onDismiss, enabled = !editor.busy) { Text(stringResource(R.string.pieces_cancel)) } })
        }
    }
}
