package com.macareen.stitchbook2.feature.sessions

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.CraftingSession
import com.macareen.stitchbook2.domain.model.safeZone
import com.macareen.stitchbook2.ui.components.ContentCard
import com.macareen.stitchbook2.ui.components.DateField
import com.macareen.stitchbook2.ui.components.EmptyState
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SecondaryActionButton
import com.macareen.stitchbook2.ui.components.SectionHeader
import com.macareen.stitchbook2.ui.components.formatDuration
import com.macareen.stitchbook2.ui.components.formatStopwatch
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

@Composable
fun ProjectSessionsRoute(viewModel: ProjectSessionsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProjectSessionsScreen(
        uiState = uiState,
        onStart = viewModel::start,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onStop = viewModel::stop,
        onSaveCorrection = viewModel::saveCorrection,
        onDelete = viewModel::delete,
        onDismissFeedback = viewModel::dismissFeedback
    )
}

@Composable
fun ProjectSessionsScreen(
    uiState: ProjectSessionsUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSaveCorrection: (CraftingSession, SessionCorrectionInput) -> Unit,
    onDelete: (CraftingSession) -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<CraftingSession?>(null) }
    var deleting by remember { mutableStateOf<CraftingSession?>(null) }

    // A UI-only clock tick for the live stopwatch; nothing is persisted per tick.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val active = uiState.activeSession
    LaunchedEffect(active?.isRunning) {
        while (active?.isRunning == true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }

    if (uiState.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(StitchbookSpacing.large),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium)
    ) {
        item {
            QuietText(text = uiState.projectName)
            Text(text = stringResource(R.string.sessions_title), style = MaterialTheme.typography.headlineMedium)
        }

        uiState.feedback?.let { message ->
            item { FeedbackLine(message = message, onDismiss = onDismissFeedback) }
        }

        item {
            TimerCard(
                uiState = uiState,
                now = now,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop
            )
        }

        item {
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            SectionHeader(title = stringResource(R.string.sessions_history), icon = Icons.Outlined.Timer)
            if (uiState.sessions.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.sessions_empty_title),
                    description = stringResource(R.string.sessions_empty_description),
                    icon = Icons.Outlined.Timer
                )
            }
        }

        items(uiState.sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                now = now,
                onEdit = { editing = session },
                onDelete = { deleting = session }
            )
        }
    }

    editing?.let { session ->
        CorrectionDialog(
            session = session,
            now = now,
            onSave = { input ->
                editing = null
                onSaveCorrection(session, input)
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(text = stringResource(R.string.sessions_delete_title)) },
            text = { Text(text = stringResource(R.string.sessions_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        onDelete(session)
                    }
                ) { Text(text = stringResource(R.string.journal_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(text = stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun TimerCard(
    uiState: ProjectSessionsUiState,
    now: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val active = uiState.activeSession
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(StitchbookSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val thisActive = active != null && uiState.activeIsThisProject
            Text(
                text = when {
                    thisActive && active!!.isPaused -> stringResource(R.string.sessions_paused)
                    thisActive -> stringResource(R.string.sessions_running)
                    else -> stringResource(R.string.sessions_ready)
                },
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = formatStopwatch(if (thisActive) active!!.workedMillis(now) else 0L),
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            if (active != null && !thisActive) {
                QuietText(
                    text = stringResource(
                        R.string.sessions_other_running,
                        uiState.activeProjectName ?: stringResource(R.string.sessions_unassigned)
                    )
                )
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
            Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                when {
                    !thisActive -> PrimaryActionButton(
                        text = stringResource(R.string.sessions_start),
                        onClick = onStart,
                        icon = Icons.Outlined.PlayArrow
                    )
                    active!!.isPaused -> {
                        SecondaryActionButton(
                            text = stringResource(R.string.sessions_stop),
                            onClick = onStop,
                            icon = Icons.Outlined.Stop
                        )
                        PrimaryActionButton(
                            text = stringResource(R.string.sessions_resume),
                            onClick = onResume,
                            icon = Icons.Outlined.PlayArrow
                        )
                    }
                    else -> {
                        SecondaryActionButton(
                            text = stringResource(R.string.sessions_pause),
                            onClick = onPause,
                            icon = Icons.Outlined.Pause
                        )
                        PrimaryActionButton(
                            text = stringResource(R.string.sessions_stop),
                            onClick = onStop,
                            icon = Icons.Outlined.Stop
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            QuietText(text = stringResource(R.string.sessions_timer_note))
        }
    }
}

@Composable
private fun SessionCard(
    session: CraftingSession,
    now: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
    ContentCard {
        Text(
            text = formatter.format(Instant.ofEpochMilli(session.startedAt).atZone(safeZone(session.zoneId))),
            style = MaterialTheme.typography.titleMedium
        )
        val parts = listOfNotNull(
            formatDuration(session.workedMillis(now)),
            session.rowsCompleted?.let { stringResource(R.string.sessions_rows, it) },
            if (session.isActive) stringResource(R.string.sessions_in_progress) else null
        )
        QuietText(text = parts.joinToString(" · "))
        session.notes?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDelete) { Text(text = stringResource(R.string.journal_delete)) }
            TextButton(onClick = onEdit) { Text(text = stringResource(R.string.sessions_correct)) }
        }
    }
}

@Composable
private fun CorrectionDialog(
    session: CraftingSession,
    now: Long,
    onSave: (SessionCorrectionInput) -> Unit,
    onDismiss: () -> Unit
) {
    val start = remember(session.id) { Instant.ofEpochMilli(session.startedAt).atZone(safeZone(session.zoneId)) }
    var date by remember { mutableStateOf(start.toLocalDate().toString()) }
    var time by remember { mutableStateOf("%02d:%02d".format(java.util.Locale.ROOT, start.hour, start.minute)) }
    var minutes by remember { mutableStateOf((session.workedMillis(now) / 60_000L).toString()) }
    var rows by remember { mutableStateOf(session.rowsCompleted?.toString().orEmpty()) }
    var stitches by remember { mutableStateOf(session.stitchesPerRow?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(session.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sessions_correct_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
            ) {
                DateField(value = date, onValueChange = { date = it }, label = stringResource(R.string.journal_entry_date))
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.sessions_start_time)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it },
                    singleLine = true,
                    enabled = !session.isActive,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(text = stringResource(R.string.sessions_worked_minutes)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rows,
                    onValueChange = { rows = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(text = stringResource(R.string.sessions_rows_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stitches,
                    onValueChange = { stitches = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(text = stringResource(R.string.sessions_stitches_label)) },
                    supportingText = { Text(text = stringResource(R.string.sessions_stitches_help)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(text = stringResource(R.string.journal_notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(SessionCorrectionInput(date, time, minutes, rows, stitches, notes)) }) {
                Text(text = stringResource(R.string.journal_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun FeedbackLine(message: SessionFeedback, onDismiss: () -> Unit) {
    val text = when (message) {
        SessionFeedback.INVALID_DATE -> stringResource(R.string.project_date_invalid)
        SessionFeedback.INVALID_TIME -> stringResource(R.string.sessions_error_time)
        SessionFeedback.INVALID_DURATION -> stringResource(R.string.sessions_error_duration)
        SessionFeedback.INVALID_NUMBER -> stringResource(R.string.sessions_error_number)
        SessionFeedback.STOPPED_OTHER_PROJECT -> stringResource(R.string.sessions_stopped_other)
        SessionFeedback.SAVE_FAILED -> stringResource(R.string.materials_error_save_failed)
    }
    val isError = message != SessionFeedback.STOPPED_OTHER_PROJECT
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.dismiss)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProjectSessionsPreview() {
    StitchbookTheme {
        ProjectSessionsScreen(
            uiState = ProjectSessionsUiState(isLoading = false, projectName = "Fair Isle hat"),
            onStart = {},
            onPause = {},
            onResume = {},
            onStop = {},
            onSaveCorrection = { _, _ -> },
            onDelete = {},
            onDismissFeedback = {}
        )
    }
}
