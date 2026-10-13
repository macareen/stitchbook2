package com.macareen.stitchbook2.feature.sessions

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.ui.components.ChoiceChipRow
import com.macareen.stitchbook2.ui.components.DateField

@Composable
fun ManualTimeDialog(state: ManualEntryState, onSave: (ManualTimeInput) -> Unit, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val defaults = state.defaults
    var date by rememberSaveable(defaults) { mutableStateOf(defaults.finishedDate) }
    var time by rememberSaveable(defaults) { mutableStateOf(defaults.finishedTime) }
    var minutes by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var offset by rememberSaveable { mutableStateOf<String?>(null) }
    val errorRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.error) {
        if (state.error != null) { withFrameNanos { }; errorRequester.bringIntoView() }
    }
    val busy = state.status == ManualEntryStatus.SAVING
    val overlap = state.error == ManualTimeError.POSSIBLE_OVERLAP
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.manual_time_add)) },
        text = {
            val keyboard = LocalSoftwareKeyboardController.current
            val keyboardActions = KeyboardActions(onDone = { keyboard?.hide() })
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.manual_time_help), style = MaterialTheme.typography.bodySmall)
                DateField(date, { date = it; offset = null; onChanged() }, stringResource(R.string.manual_time_date), enabled = !busy)
                OutlinedTextField(time, { time = it; offset = null; onChanged() }, enabled = !busy, singleLine = true,
                    label = { Text(stringResource(R.string.manual_time_clock)) }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = keyboardActions)
                OutlinedTextField(minutes, { minutes = it; onChanged() }, enabled = !busy, singleLine = true,
                    label = { Text(stringResource(R.string.manual_time_minutes)) }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = keyboardActions)
                Text(stringResource(R.string.manual_time_zone, defaults.zoneId), style = MaterialTheme.typography.bodySmall)
                if (state.offsets.isNotEmpty()) ChoiceChipRow(state.offsets, offset ?: "",
                    { value -> stringResource(if (state.offsets.indexOf(value) == 0) R.string.manual_time_earlier else R.string.manual_time_later, value) },
                    { offset = it }, enabled = !busy)
                OutlinedTextField(notes, { notes = it; onChanged() }, enabled = !busy, minLines = 2,
                    label = { Text(stringResource(R.string.manual_time_notes)) }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = keyboardActions)
                state.error?.let { Text(stringResource(it.labelResource()), color = MaterialTheme.colorScheme.error, modifier = Modifier.bringIntoViewRequester(errorRequester)) }
            }
        },
        confirmButton = { Column {
            TextButton(onClick = { onSave(ManualTimeInput(date, time, minutes, notes, offset, overlap)) }, enabled = !busy && minutes.isNotBlank()) {
                Text(stringResource(if (overlap) R.string.manual_time_add_anyway else R.string.journal_save))
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        } })
}

private fun ManualTimeError.labelResource(): Int = when (this) {
    ManualTimeError.INVALID_DATE -> R.string.manual_time_invalid_date
    ManualTimeError.INVALID_TIME -> R.string.manual_time_invalid_time
    ManualTimeError.INVALID_DURATION -> R.string.manual_time_invalid_duration
    ManualTimeError.FUTURE_TIME -> R.string.manual_time_future
    ManualTimeError.CLOCK_GAP -> R.string.manual_time_gap
    ManualTimeError.CHOOSE_OFFSET -> R.string.manual_time_offset
    ManualTimeError.POSSIBLE_OVERLAP -> R.string.manual_time_overlap
    ManualTimeError.SAVE_FAILED -> R.string.manual_time_save_failed
}
