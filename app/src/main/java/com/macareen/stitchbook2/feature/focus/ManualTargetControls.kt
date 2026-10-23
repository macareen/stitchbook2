package com.macareen.stitchbook2.feature.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.execution.*

@Composable
internal fun ManualTargetControls(target: ManualTarget, work: TargetWork, occurrenceKey: String,
    busy: Boolean, onObserve: (Double) -> Unit, onConfirm: () -> Unit) {
    var value by rememberSaveable(occurrenceKey, work.cycle) { mutableStateOf("") }
    var confirming by rememberSaveable(occurrenceKey, work.cycle) { mutableStateOf(false) }
    val observed = work.observations.lastOrNull { it.cycle == work.cycle }?.value
    val parsed = value.toDoubleOrNull()?.takeIf(target::accepts)
    Column {
        Text(stringResource(R.string.manual_target_heading, target.description(), work.cycle), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.manual_target_unknown))
        OutlinedTextField(value, { value = it }, enabled = !busy,
            label = { Text(stringResource(R.string.manual_target_observation, target.unit.label)) },
            isError = value.isNotBlank() && parsed == null)
        TextButton(onClick = { parsed?.let(onObserve) }, enabled = !busy && parsed != null) { Text(stringResource(R.string.manual_target_record)) }
        observed?.let { Text(stringResource(R.string.manual_target_latest, formatTargetValue(it), target.unit.label)) }
        TextButton(onClick = { confirming = true }, enabled = !busy && observed != null && target.isReachedBy(observed)) {
            Text(stringResource(R.string.manual_target_reached))
        }
        if (work.observations.isNotEmpty()) {
            Text(stringResource(R.string.manual_target_history))
            // The complete record is portable; keep the working screen bounded.
            work.observations.takeLast(5).forEach { observation ->
                Text(stringResource(R.string.manual_target_history_item, observation.cycle, formatTargetValue(observation.value), target.unit.label))
            }
        }
    }
    if (confirming) AlertDialog(onDismissRequest = { if (!busy) confirming = false },
        title = { Text(stringResource(R.string.manual_target_reached)) },
        text = { Text(stringResource(R.string.manual_target_confirm, target.description())) },
        confirmButton = { TextButton(onClick = { confirming = false; onConfirm() }, enabled = !busy) { Text(stringResource(R.string.manual_target_reached)) } },
        dismissButton = { TextButton(onClick = { confirming = false }, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
}
