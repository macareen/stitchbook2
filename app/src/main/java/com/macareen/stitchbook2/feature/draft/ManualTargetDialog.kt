package com.macareen.stitchbook2.feature.draft

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.execution.*

@Composable
internal fun ManualTargetDialog(target: ManualTarget?, busy: Boolean, error: String?,
    onSave: (ManualTarget?) -> Unit, onDismiss: () -> Unit,
    suggestion: ManualTarget? = null, sourceText: String? = null, bodyReview: Boolean = false) {
    val initial = target ?: suggestion
    var reviewed by rememberSaveable { mutableStateOf(false) }
    var unitName by rememberSaveable { mutableStateOf(initial?.unit?.name ?: TargetUnit.CENTIMETRES.name) }
    var comparisonName by rememberSaveable { mutableStateOf(initial?.comparison?.name ?: TargetComparison.AT_LEAST.name) }
    var value by rememberSaveable { mutableStateOf(initial?.value?.let(::formatTargetValue).orEmpty()) }
    val unit = TargetUnit.valueOf(unitName)
    val parsed = value.toDoubleOrNull()?.let { runCatching { ManualTarget(unit, it, TargetComparison.valueOf(comparisonName)) }.getOrNull() }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.manual_target_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(if (bodyReview) R.string.manual_target_body_help else R.string.manual_target_help))
            if (suggestion != null || bodyReview) {
                Text(sourceText.orEmpty())
                if (suggestion != null) Text(stringResource(R.string.pdf_target_suggestion_help))
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = reviewed,
                    enabled = !busy, role = Role.Checkbox, onValueChange = { reviewed = it }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = reviewed, onCheckedChange = null)
                    Text(stringResource(R.string.pdf_target_review_checked))
                }
            }
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
        confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = !busy && parsed != null && (!(suggestion != null || bodyReview) || reviewed)) { Text(stringResource(R.string.draft_action_save)) } },
        dismissButton = { Row {
            if (target != null) TextButton(onClick = { onSave(null) }, enabled = !busy) { Text(stringResource(R.string.manual_target_remove)) }
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        } })
}
