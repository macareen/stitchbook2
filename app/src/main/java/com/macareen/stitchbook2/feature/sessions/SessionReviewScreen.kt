package com.macareen.stitchbook2.feature.sessions

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.macareen.stitchbook2.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SessionReviewRoute(viewModel: SessionReviewViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, viewModel) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { viewModel.checkNow(); delay(15_000) }
        }
    }
    val expectedId = state.review?.session?.id.orEmpty()
    SessionReviewDialog(state, { viewModel.keepAll(expectedId) }, { viewModel.correctAndPause(expectedId, it) }, { viewModel.later(expectedId) })
}

@Composable
fun SessionReviewDialog(state: SessionReviewUiState, onKeepAll: () -> Unit, onCorrect: (String) -> Unit, onLater: () -> Unit) {
    val review = state.review ?: return
    var minutes by rememberSaveable(review.session.id, review.session.updatedAt) { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!state.busy) onLater() }, title = { Text(stringResource(R.string.timer_review_title)) },
        text = {
            val keyboard = LocalSoftwareKeyboardController.current
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                review.projectName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                Text(stringResource(R.string.timer_review_body, review.workedMinutes))
                OutlinedTextField(value = minutes, onValueChange = { minutes = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    enabled = !state.busy, label = { Text(stringResource(R.string.timer_review_actual_minutes)) },
                    modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.timer_review_correction_help), style = MaterialTheme.typography.bodySmall)
                state.error?.let {
                    Text(stringResource(when (it) {
                        SessionReviewError.INVALID_MINUTES -> R.string.timer_review_invalid_minutes
                        SessionReviewError.SAVE_FAILED -> R.string.timer_review_save_failed
                        SessionReviewError.TIMER_CHANGED -> R.string.timer_review_changed
                    }), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onCorrect(minutes) }, enabled = !state.busy && minutes.isNotBlank()) { Text(stringResource(R.string.timer_review_correct)) }
            TextButton(onClick = onKeepAll, enabled = !state.busy) { Text(stringResource(R.string.timer_review_keep)) }
            TextButton(onClick = onLater, enabled = !state.busy) { Text(stringResource(R.string.timer_review_later)) }
        } }
    )
}
