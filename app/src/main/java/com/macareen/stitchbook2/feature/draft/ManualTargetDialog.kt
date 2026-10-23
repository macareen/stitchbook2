package com.macareen.stitchbook2.feature.draft

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.execution.*

@Composable
internal fun ManualTargetDialog(target: ManualTarget?, busy: Boolean, error: String?,
    onSave: (ManualTarget?) -> Unit, onDismiss: () -> Unit) {
    var unitName by rememberSaveable { mutableStateOf(target?.unit?.name ?: TargetUnit.CENTIMETRES.name) }
    var comparisonName by rememberSaveable { mutableStateOf(target?.comparison?.name ?: TargetComparison.AT_LEAST.name) }
    var value by rememberSaveable { mutableStateOf(target?.value?.let(::formatTargetValue).orEmpty()) }
    val unit = TargetUnit.valueOf(unitName)
    val parsed = value.toDoubleOrNull()?.let { runCatching { ManualTarget(unit, it, TargetComparison.valueOf(comparisonName)) }.getOrNull() }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.manual_target_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.manual_target_help))
            TargetComparison.entries.forEach { choice ->
                TextButton(onClick = { comparisonName = choice.name }, enabled = !busy) {
                    RadioButton(selected = comparisonName == choice.name, onClick = null)
                    Text(choice.label)
                }
            }
            TargetUnit.entries.forEach { choice ->
                TextButton(onClick = { unitName = choice.name }, enabled = !busy) {
                    RadioButton(selected = unit == choice, onClick = null)
                    Text(choice.label)
                }
            }
            OutlinedTextField(value, { value = it }, enabled = !busy,
                label = { Text(stringResource(R.string.manual_target_value, unit.label)) }, isError = value.isNotBlank() && parsed == null)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = !busy && parsed != null) { Text(stringResource(R.string.draft_action_save)) } },
        dismissButton = { Row {
            if (target != null) TextButton(onClick = { onSave(null) }, enabled = !busy) { Text(stringResource(R.string.manual_target_remove)) }
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        } })
}
