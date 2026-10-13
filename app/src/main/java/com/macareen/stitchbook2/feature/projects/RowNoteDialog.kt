package com.macareen.stitchbook2.feature.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.JournalNoteTag
import com.macareen.stitchbook2.feature.journal.NoteTagChoices

enum class RowNoteSaveState { IDLE, SAVING, SAVED, BODY_REQUIRED, SAVE_FAILED }

@Composable
fun RowNoteDialog(initialContext: String, state: RowNoteSaveState,
    onSave: (String, JournalNoteTag, String) -> Unit, onDismiss: () -> Unit) {
    var body by rememberSaveable { mutableStateOf("") }
    var context by rememberSaveable(initialContext) { mutableStateOf(initialContext) }
    var tag by rememberSaveable { mutableStateOf(JournalNoteTag.GENERAL) }
    val busy = state == RowNoteSaveState.SAVING
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.row_note_add)) },
        text = {
            val keyboard = LocalSoftwareKeyboardController.current
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NoteTagChoices(tag, { tag = it }, !busy)
                OutlinedTextField(context, { context = it }, label = { Text(stringResource(R.string.row_note_context)) },
                    modifier = Modifier.fillMaxWidth(), enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
                Text(stringResource(R.string.row_note_context_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(body, { body = it }, label = { Text(stringResource(R.string.row_note_body)) }, minLines = 3,
                    modifier = Modifier.fillMaxWidth(), enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
                if (state == RowNoteSaveState.SAVE_FAILED || state == RowNoteSaveState.BODY_REQUIRED) Text(
                    stringResource(if (state == RowNoteSaveState.SAVE_FAILED) R.string.row_note_save_failed else R.string.row_note_body_required),
                    color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(body, tag, context) }, enabled = !busy && body.isNotBlank()) { Text(stringResource(R.string.journal_save)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
}
