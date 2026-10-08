package com.macareen.stitchbook2.feature.ravelry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.ravelry.RavelryImportPlan
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme

@Composable
fun RavelryRoute(viewModel: RavelryViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RavelryCard(
        uiState = uiState,
        onSaveKey = viewModel::saveKey,
        onForgetKey = viewModel::forgetKey,
        onPreview = viewModel::preview,
        onApply = viewModel::apply,
        onDismissPlan = viewModel::dismissPlan
    )
}

/**
 * Settings card for the optional Ravelry pull. The key is typed here, kept
 * encrypted on this device, and only used for read requests.
 */
@Composable
fun RavelryCard(
    uiState: RavelryUiState,
    onSaveKey: (String, String) -> Unit,
    onForgetKey: () -> Unit,
    onPreview: () -> Unit,
    onApply: (Boolean) -> Unit,
    onDismissPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small),
            modifier = Modifier.padding(StitchbookSpacing.large)
        ) {
            Text(text = stringResource(R.string.ravelry_title), style = MaterialTheme.typography.titleMedium)
            if (uiState.hasKey) {
                ConnectedContent(uiState, onForgetKey, onPreview)
            } else {
                KeyForm(isWorking = uiState.isWorking, onSaveKey = onSaveKey)
            }
            uiState.problem?.let { problem ->
                Text(
                    text = stringResource(
                        when (problem) {
                            RavelryProblem.KEY_REJECTED -> R.string.ravelry_problem_key
                            RavelryProblem.UNREACHABLE -> R.string.ravelry_problem_network
                            RavelryProblem.SAVE_FAILED -> R.string.ravelry_problem_save
                        }
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            uiState.savedCount?.let { QuietText(text = stringResource(R.string.ravelry_saved, it)) }
        }
    }

    uiState.plan?.let { plan ->
        PlanDialog(plan = plan, isWorking = uiState.isWorking, onApply = onApply, onDismiss = onDismissPlan)
    }
}

@Composable
private fun KeyForm(isWorking: Boolean, onSaveKey: (String, String) -> Unit) {
    var accessKey by rememberSaveable { mutableStateOf("") }
    var personalKey by rememberSaveable { mutableStateOf("") }
    QuietText(text = stringResource(R.string.ravelry_explainer))
    OutlinedTextField(
        value = accessKey,
        onValueChange = { accessKey = it },
        label = { Text(stringResource(R.string.ravelry_access_key)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = personalKey,
        onValueChange = { personalKey = it },
        label = { Text(stringResource(R.string.ravelry_personal_key)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth()
    )
    PrimaryActionButton(
        text = stringResource(R.string.ravelry_connect),
        onClick = {
            onSaveKey(accessKey, personalKey)
            personalKey = ""
        },
        enabled = !isWorking && accessKey.isNotBlank() && personalKey.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ConnectedContent(uiState: RavelryUiState, onForgetKey: () -> Unit, onPreview: () -> Unit) {
    QuietText(
        text = uiState.connectedAs?.let { stringResource(R.string.ravelry_connected_as, it) }
            ?: stringResource(R.string.ravelry_key_saved)
    )
    QuietText(text = stringResource(R.string.ravelry_pull_explainer))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
        TextButton(onClick = onPreview, enabled = !uiState.isWorking) {
            Text(stringResource(if (uiState.isWorking) R.string.ravelry_working else R.string.ravelry_preview))
        }
        TextButton(onClick = onForgetKey, enabled = !uiState.isWorking) {
            Text(stringResource(R.string.ravelry_forget))
        }
    }
}

@Composable
private fun PlanDialog(plan: RavelryImportPlan, isWorking: Boolean, onApply: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val changes = plan.changeCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ravelry_review_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                if (plan.hasNothingToDo) {
                    Text(stringResource(R.string.ravelry_review_up_to_date))
                } else {
                    PlanLine(R.string.ravelry_review_yarn, plan.newStash.size, plan.changedStash.size)
                    PlanLine(R.string.ravelry_review_tools, plan.newTools.size, plan.changedTools.size)
                    PlanLine(R.string.ravelry_review_projects, plan.projects.new.size, plan.projects.changed.size)
                    PlanLine(R.string.ravelry_review_patterns, plan.patterns.new.size, plan.patterns.changed.size)
                    QuietText(text = stringResource(R.string.ravelry_review_note))
                }
            }
        },
        confirmButton = {
            Column {
                if (plan.hasNew) {
                    TextButton(onClick = { onApply(false) }, enabled = !isWorking) {
                        Text(stringResource(R.string.ravelry_add_new))
                    }
                }
                if (changes > 0) {
                    TextButton(onClick = { onApply(true) }, enabled = !isWorking) {
                        Text(stringResource(R.string.ravelry_add_and_update))
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun PlanLine(label: Int, new: Int, changed: Int) {
    if (new == 0 && changed == 0) return
    val parts = listOfNotNull(
        new.takeIf { it > 0 }?.let { stringResource(R.string.ravelry_review_new, it) },
        changed.takeIf { it > 0 }?.let { stringResource(R.string.ravelry_review_changed, it) }
    )
    Text(text = stringResource(label) + ": " + parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
}

@Preview(showBackground = true)
@Composable
private fun RavelryCardPreview() {
    StitchbookTheme {
        RavelryCard(
            uiState = RavelryUiState(),
            onSaveKey = { _, _ -> },
            onForgetKey = {},
            onPreview = {},
            onApply = {},
            onDismissPlan = {}
        )
    }
}
