package com.macareen.stitchbook2.feature.focus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.macareen.stitchbook2.domain.model.KnittingTime
import com.macareen.stitchbook2.domain.model.CraftingSession
import androidx.compose.runtime.produceState
import com.macareen.stitchbook2.domain.execution.ProgressBasis
import com.macareen.stitchbook2.domain.execution.OverviewStatus
import com.macareen.stitchbook2.domain.execution.OverviewEntry
import com.macareen.stitchbook2.domain.execution.GuideProgressSummary
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.data.notification.CounterFocusNotificationService
import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.ExecutionAddress
import com.macareen.stitchbook2.domain.execution.ExecutionId
import com.macareen.stitchbook2.domain.execution.NodeId
import com.macareen.stitchbook2.domain.model.Counter
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SecondaryActionButton
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.instruction
import com.macareen.stitchbook2.ui.theme.screenTitle
import com.macareen.stitchbook2.ui.theme.sectionLabel

@Composable
fun GuideFocusRoute(viewModel: GuideFocusViewModel, onOpenPattern: (PatternPage) -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        // Denial just means no notification is posted -- the on-screen
        // counters strip is the source of truth either way, so there's
        // nothing to react to here.
        onResult = {}
    )

    // Scoped to this Focus Mode session (PRODUCT_SPEC.md 6.3's "persistent
    // notifications"): starts while InProgress with counters to show, stops
    // otherwise or when this screen is left. No always-on background
    // tracking -- see CounterFocusNotificationService's KDoc.
    val inProgress = uiState as? GuideFocusUiState.InProgress
    LaunchedEffect(inProgress?.projectId, inProgress?.guideName, inProgress?.projectCounters?.isNotEmpty()) {
        if (inProgress != null && inProgress.projectCounters.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            CounterFocusNotificationService.start(context, inProgress.projectId, inProgress.guideName)
        } else {
            CounterFocusNotificationService.stop(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose { CounterFocusNotificationService.stop(context) }
    }

    GuideFocusScreen(
        uiState = uiState,
        onStart = viewModel::onStart,
        onComplete = viewModel::onComplete,
        onPrevious = viewModel::onPrevious,
        onJumpToFirstIncomplete = viewModel::onJumpToFirstIncomplete,
        onStartNext = viewModel::onStartNext,
        onIncrementCounter = viewModel::onIncrementCounter,
        onDecrementCounter = viewModel::onDecrementCounter,
        onJumpTo = viewModel::onJumpTo,
        onOpenPattern = onOpenPattern,
        onToggleTimer = viewModel::onToggleTimer
    )
}

@Composable
fun GuideFocusScreen(
    uiState: GuideFocusUiState,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onPrevious: () -> Unit,
    onJumpToFirstIncomplete: () -> Unit,
    onStartNext: () -> Unit,
    onIncrementCounter: (Counter) -> Unit,
    onDecrementCounter: (Counter) -> Unit,
    modifier: Modifier = Modifier,
    onJumpTo: (ExecutionAddress) -> Unit = {},
    onOpenPattern: (PatternPage) -> Unit = {},
    onToggleTimer: () -> Unit = {}
) {
    when (uiState) {
        GuideFocusUiState.Loading -> LoadingState(modifier)

        GuideFocusUiState.GuideNotFound -> MessageState(
            title = stringResource(R.string.guide_not_found_title),
            description = stringResource(R.string.guide_not_found_description),
            modifier = modifier
        )

        GuideFocusUiState.LoadError -> MessageState(
            title = stringResource(R.string.guide_load_error_title),
            description = stringResource(R.string.guide_load_error_description),
            modifier = modifier
        )

        GuideFocusUiState.NoPublishedRevision -> MessageState(
            title = stringResource(R.string.guide_no_revision_title),
            description = stringResource(R.string.guide_no_revision_description),
            modifier = modifier
        )

        is GuideFocusUiState.ReadyToStart -> ReadyToStartContent(
            state = uiState,
            onStart = onStart,
            modifier = modifier
        )

        is GuideFocusUiState.InProgress -> InProgressContent(
            state = uiState,
            onComplete = onComplete,
            onPrevious = onPrevious,
            onJumpToFirstIncomplete = onJumpToFirstIncomplete,
            onIncrementCounter = onIncrementCounter,
            onDecrementCounter = onDecrementCounter,
            onJumpTo = onJumpTo,
            onOpenPattern = onOpenPattern,
            onToggleTimer = onToggleTimer,
            modifier = modifier
        )

        is GuideFocusUiState.Completed -> CompletedContent(
            state = uiState,
            onStartNext = onStartNext,
            modifier = modifier
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageState(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(StitchbookSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.screenTitle,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        QuietText(
            text = description,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ReadyToStartContent(
    state: GuideFocusUiState.ReadyToStart,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(StitchbookSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = state.guideName,
            style = MaterialTheme.typography.screenTitle,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        QuietText(
            text = stringResource(R.string.focus_ready_title),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(StitchbookSpacing.large))
        PrimaryActionButton(
            text = if (state.isStarting) {
                stringResource(R.string.focus_starting_action)
            } else {
                stringResource(R.string.focus_start_action)
            },
            onClick = onStart,
            enabled = !state.isStarting
        )
        if (state.startFailed) {
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
            Text(
                text = stringResource(R.string.focus_start_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The core reading surface. Three fixed regions, top to bottom:
 * a quiet context header, a scrollable instruction body that dominates the
 * available space, and a pinned action row that stays reachable regardless
 * of instruction length or system font scale.
 */
@Composable
private fun InProgressContent(
    state: GuideFocusUiState.InProgress,
    onComplete: () -> Unit,
    onPrevious: () -> Unit,
    onJumpToFirstIncomplete: () -> Unit,
    onIncrementCounter: (Counter) -> Unit,
    onDecrementCounter: (Counter) -> Unit,
    onJumpTo: (ExecutionAddress) -> Unit,
    onOpenPattern: (PatternPage) -> Unit,
    onToggleTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOverview by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = StitchbookSpacing.large, vertical = StitchbookSpacing.small),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium)
    ) {
        // Where you are: guide, part, and how far through.
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = state.guideName, style = MaterialTheme.typography.titleLarge)
                    if (state.breadcrumbs.isNotEmpty()) {
                        QuietText(text = state.breadcrumbs.joinToString(separator = " › "))
                    }
                }
                state.progress?.let { progress ->
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = stringResource(R.string.home_percent, progress.percent),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            state.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress.fraction.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = MaterialTheme.colorScheme.inversePrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                QuietText(text = progressText(progress), style = MaterialTheme.typography.bodySmall)
            }
        }

        state.stepStartedAt?.let { stepStartedAt ->
            TimeTiles(
                sessions = state.timeSessions,
                stepStartedAt = stepStartedAt,
                timer = state.timer,
                timerEnabled = !state.isBusy,
                onToggleTimer = onToggleTimer
            )
        }

        // The step itself, as the screen's centrepiece. It scrolls on its own so
        // the counters and the Complete button below always stay reachable.
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 3.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(StitchbookSpacing.large)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium)
                ) {
                    if (state.positions.isNotEmpty()) {
                        Column(
                            modifier = Modifier.semantics(mergeDescendants = true) {},
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            state.positions.forEach { position -> PositionLine(position) }
                        }
                    }
                    Text(
                        text = state.instructionText,
                        style = MaterialTheme.typography.instruction,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    state.feedback?.let { feedback ->
                        QuietText(text = focusFeedbackText(feedback))
                    }
                }
                if (state.overview.isNotEmpty() || state.pattern != null) {
                    Row(
                        modifier = Modifier.padding(top = StitchbookSpacing.small),
                        horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
                    ) {
                        state.pattern?.let { pattern ->
                            SoftPill(
                                text = pattern.page?.let { stringResource(R.string.focus_open_pattern_page_action, it) }
                                    ?: stringResource(R.string.focus_open_pattern_action),
                                onClick = { onOpenPattern(pattern) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (state.overview.isNotEmpty()) {
                            SoftPill(
                                text = stringResource(R.string.focus_overview_action),
                                onClick = { showOverview = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (state.projectCounters.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
            ) {
                state.projectCounters.forEach { counter ->
                    FocusCounterChip(
                        counter = counter,
                        onIncrement = { onIncrementCounter(counter) },
                        onDecrement = { onDecrementCounter(counter) }
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onPrevious,
                    enabled = !state.isBusy,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier
                        .weight(0.4f)
                        .height(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.focus_previous_action),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.isBusy) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Surface(
                    onClick = onComplete,
                    enabled = !state.isBusy,
                    shape = CircleShape,
                    color = if (state.isBusy) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary,
                    shadowElevation = if (state.isBusy) 0.dp else 4.dp,
                    modifier = Modifier
                        .weight(0.6f)
                        .height(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.focus_complete_action),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            color = if (state.isBusy) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            if (state.jumpToFirstIncompleteTarget != null) {
                TextButton(
                    onClick = onJumpToFirstIncomplete,
                    enabled = !state.isBusy,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.focus_jump_to_incomplete_action))
                }
            }
        }
    }

    if (showOverview) {
        OverviewSheet(
            entries = state.overview,
            onJumpTo = { target ->
                showOverview = false
                onJumpTo(target)
            },
            onDismiss = { showOverview = false }
        )
    }
}

@Composable
private fun SoftPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Total and this-step worked time as two small tiles, ticking while this project's timer runs, plus the pause/resume button. */
@Composable
private fun TimeTiles(
    sessions: List<CraftingSession>,
    stepStartedAt: Long,
    timer: FocusTimer,
    timerEnabled: Boolean,
    onToggleTimer: () -> Unit
) {
    val now by produceState(System.currentTimeMillis(), timer, sessions) {
        while (timer == FocusTimer.RUNNING) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
        value = System.currentTimeMillis()
    }
    val time = KnittingTime.of(sessions, stepStartedAt, now)
    val step = workedText(time.stepMillis)
    Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall)) {
        Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            TimeTile(
                label = stringResource(R.string.focus_tile_total),
                value = workedText(time.totalMillis),
                modifier = Modifier.weight(1f)
            )
            TimeTile(
                label = stringResource(R.string.focus_tile_step),
                value = if (time.stepIsEstimated) stringResource(R.string.focus_tile_step_estimated, step) else step,
                modifier = Modifier.weight(1f)
            )
            if (timer != FocusTimer.OTHER_PROJECT) {
                Surface(
                    onClick = onToggleTimer,
                    enabled = timerEnabled,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.size(width = 56.dp, height = 60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (timer == FocusTimer.RUNNING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                when (timer) {
                                    FocusTimer.RUNNING -> R.string.focus_timer_pause
                                    FocusTimer.PAUSED -> R.string.focus_timer_resume
                                    else -> R.string.focus_timer_start
                                }
                            ),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        when (timer) {
            FocusTimer.PAUSED -> QuietText(text = stringResource(R.string.focus_timer_paused), style = MaterialTheme.typography.bodySmall)
            FocusTimer.OTHER_PROJECT -> QuietText(text = stringResource(R.string.focus_timer_other_project), style = MaterialTheme.typography.bodySmall)
            else -> Unit
        }
    }
}

@Composable
private fun TimeTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier
            .height(60.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(text = value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun workedText(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.focus_time_under_minute)
        minutes < 60 -> stringResource(R.string.focus_time_minutes, minutes)
        else -> stringResource(R.string.focus_time_hours, minutes / 60, minutes % 60)
    }
}

@Composable
private fun progressText(progress: GuideProgressSummary): String = when (progress.basis) {
    ProgressBasis.STITCHES -> stringResource(R.string.focus_progress_stitches, progress.percent)
    ProgressBasis.STITCHES_ESTIMATED -> stringResource(R.string.focus_progress_stitches_estimated, progress.percent)
    ProgressBasis.STEPS -> stringResource(R.string.focus_progress_steps, progress.percent, progress.completedSteps, progress.totalSteps)
}

/** The whole guide at a glance; tapping a line moves there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewSheet(entries: List<OverviewEntry>, onJumpTo: (ExecutionAddress) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.focus_overview_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = StitchbookSpacing.large, vertical = StitchbookSpacing.small)
        )
        LazyColumn(contentPadding = PaddingValues(bottom = StitchbookSpacing.large)) {
            items(entries, key = { it.nodeId.value }) { entry -> OverviewLine(entry, onClick = { onJumpTo(entry.jumpTarget) }) }
        }
    }
}

@Composable
private fun OverviewLine(entry: OverviewEntry, onClick: () -> Unit) {
    val statusLabel = stringResource(
        when (entry.status) {
            OverviewStatus.DONE -> R.string.focus_overview_done
            OverviewStatus.CURRENT -> R.string.focus_overview_current
            OverviewStatus.TO_DO -> R.string.focus_overview_to_do
        }
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(
                start = StitchbookSpacing.large + (entry.depth * 16).dp,
                end = StitchbookSpacing.large,
                top = StitchbookSpacing.extraSmall,
                bottom = StitchbookSpacing.extraSmall
            )
            .semantics(mergeDescendants = true) { stateDescription = statusLabel }
    ) {
        Text(
            text = when (entry.status) {
                OverviewStatus.DONE -> "✓"
                OverviewStatus.CURRENT -> "▶"
                OverviewStatus.TO_DO -> "○"
            },
            color = if (entry.status == OverviewStatus.CURRENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {}
        )
        Text(
            text = entry.text,
            style = if (entry.kind == OverviewEntry.Kind.SECTION) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = if (entry.status == OverviewStatus.DONE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (entry.totalSteps > 1) {
            QuietText(text = stringResource(R.string.focus_overview_count, entry.completedSteps, entry.totalSteps))
        }
    }
}

/** A compact name/value/+/- unit for one counter, sized for a horizontal strip rather than the full CounterCard. */
@Composable
private fun FocusCounterChip(
    counter: Counter,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = StitchbookSpacing.small, vertical = StitchbookSpacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = stringResource(R.string.counters_decrement_action)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = StitchbookSpacing.extraSmall)
            ) {
                QuietText(text = counter.name, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = counter.goal?.let {
                        stringResource(R.string.counters_value_with_goal_pill, counter.currentValue, it, counter.unitLabel)
                    } ?: stringResource(R.string.counters_value_pill, counter.currentValue, counter.unitLabel),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            IconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.counters_increment_action)
                )
            }
        }
    }
}

@Composable
private fun PositionLine(position: StructuralPosition) {
    val text = when (position) {
        is StructuralPosition.RangePosition -> if (position.startInclusive == 1) {
            stringResource(
                R.string.focus_range_position_from_one,
                position.unitLabel.replaceFirstChar { it.uppercase() },
                position.currentValue,
                position.endInclusive
            )
        } else {
            stringResource(
                R.string.focus_range_position,
                position.unitLabel.replaceFirstChar { it.uppercase() },
                position.currentValue,
                position.startInclusive,
                position.endInclusive
            )
        }

        is StructuralPosition.RepeatPosition -> {
            val label = position.label
            if (label.isNullOrBlank()) {
                stringResource(
                    R.string.focus_repeat_position,
                    position.currentIteration,
                    position.count
                )
            } else {
                stringResource(
                    R.string.focus_repeat_position_labeled,
                    label,
                    position.currentIteration,
                    position.count
                )
            }
        }
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun focusFeedbackText(feedback: FocusFeedback): String = when (feedback) {
    FocusFeedback.ALREADY_AT_FIRST_OCCURRENCE ->
        stringResource(R.string.focus_feedback_already_at_first_occurrence)

    FocusFeedback.ALREADY_AT_TARGET ->
        stringResource(R.string.focus_feedback_already_at_target)

    FocusFeedback.ALREADY_COMPLETE ->
        stringResource(R.string.focus_feedback_already_complete)

    FocusFeedback.STALE_EXECUTION_STATE ->
        stringResource(R.string.focus_feedback_stale_execution_state)

    FocusFeedback.INVALID_TRANSITION ->
        stringResource(R.string.focus_feedback_invalid_transition)

    FocusFeedback.UNKNOWN_ERROR ->
        stringResource(R.string.focus_feedback_unknown_error)
}

@Composable
private fun CompletedContent(
    state: GuideFocusUiState.Completed,
    onStartNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(StitchbookSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = state.guideName,
            style = MaterialTheme.typography.screenTitle,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        Text(
            text = stringResource(R.string.focus_completed_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(StitchbookSpacing.extraSmall))
        QuietText(
            text = stringResource(R.string.focus_completed_description),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(StitchbookSpacing.large))
        PrimaryActionButton(
            text = if (state.isStartingNext) {
                stringResource(R.string.focus_starting_action)
            } else {
                stringResource(R.string.focus_start_next_action)
            },
            onClick = onStartNext,
            enabled = !state.isStartingNext
        )
        if (state.startNextFailed) {
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
            Text(
                text = stringResource(R.string.focus_start_next_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReadyToStartPreview() {
    StitchbookTheme {
        GuideFocusScreen(
            uiState = GuideFocusUiState.ReadyToStart(guideName = "Everyday cardigan", projectId = "preview-project"),
            onStart = {},
            onComplete = {},
            onPrevious = {},
            onJumpToFirstIncomplete = {},
            onStartNext = {},
            onIncrementCounter = {},
            onDecrementCounter = {}
        )
    }
}

private val previewInProgressState = GuideFocusUiState.InProgress(
    guideName = "Everyday cardigan",
    projectId = "preview-project",
    executionId = ExecutionId("preview-execution"),
    version = 0,
    instructionText = "Knit all stitches",
    breadcrumbs = listOf("Body", "Textured band"),
    positions = listOf(
        StructuralPosition.RepeatPosition(label = "Band", currentIteration = 2, count = 3),
        StructuralPosition.RangePosition(
            unitLabel = "round",
            currentValue = 3,
            startInclusive = 1,
            endInclusive = 4
        )
    ),
    projectCounters = listOf(
        Counter(
            id = "preview-counter-1",
            projectId = "preview-project",
            name = "Row",
            unitLabel = "rows",
            currentValue = 12,
            goal = null,
            createdAt = 0,
            updatedAt = 0,
            linkedCounterId = null,
            linkIncrementInterval = null,
            linkIncrementAmount = null
        ),
        Counter(
            id = "preview-counter-2",
            projectId = "preview-project",
            name = "Sleeve repeats",
            unitLabel = "repeats",
            currentValue = 3,
            goal = 8,
            createdAt = 0,
            updatedAt = 0,
            linkedCounterId = null,
            linkIncrementInterval = null,
            linkIncrementAmount = null
        )
    ),
    jumpToFirstIncompleteTarget = null
)

@Preview(showBackground = true, name = "In progress — light")
@Composable
private fun InProgressPreview() {
    StitchbookTheme {
        GuideFocusScreen(
            uiState = previewInProgressState,
            onStart = {},
            onComplete = {},
            onPrevious = {},
            onJumpToFirstIncomplete = {},
            onStartNext = {},
            onIncrementCounter = {},
            onDecrementCounter = {}
        )
    }
}

@Preview(showBackground = true, name = "In progress — dark", uiMode = 0x20)
@Composable
private fun InProgressDarkPreview() {
    StitchbookTheme(darkTheme = true) {
        GuideFocusScreen(
            uiState = previewInProgressState,
            onStart = {},
            onComplete = {},
            onPrevious = {},
            onJumpToFirstIncomplete = {},
            onStartNext = {},
            onIncrementCounter = {},
            onDecrementCounter = {}
        )
    }
}

@Preview(showBackground = true, name = "In progress — long instruction")
@Composable
private fun InProgressLongInstructionPreview() {
    StitchbookTheme {
        GuideFocusScreen(
            uiState = previewInProgressState.copy(
                instructionText = "Yarn over, knit two together, knit to the last three " +
                    "stitches of the round, slip one, knit one, pass the slipped stitch " +
                    "over, then repeat the sequence from the beginning of the round for " +
                    "every remaining repeat before continuing to the border.",
                jumpToFirstIncompleteTarget = ExecutionAddress(
                    definitionRevisionId = DefinitionRevisionId("preview-revision"),
                    instructionNodeId = NodeId("preview-instruction")
                )
            ),
            onStart = {},
            onComplete = {},
            onPrevious = {},
            onJumpToFirstIncomplete = {},
            onStartNext = {},
            onIncrementCounter = {},
            onDecrementCounter = {}
        )
    }
}

@Preview(showBackground = true, name = "In progress — large font scale", fontScale = 1.8f)
@Composable
private fun InProgressLargeFontScalePreview() {
    StitchbookTheme {
        GuideFocusScreen(
            uiState = previewInProgressState,
            onStart = {},
            onComplete = {},
            onPrevious = {},
            onJumpToFirstIncomplete = {},
            onStartNext = {},
            onIncrementCounter = {},
            onDecrementCounter = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CompletedPreview() {
    StitchbookTheme {
        GuideFocusScreen(
            uiState = GuideFocusUiState.Completed(guideName = "Everyday cardigan", projectId = "preview-project"),
            onStart = {},
            onComplete = {},
            onPrevious = {},
            onJumpToFirstIncomplete = {},
            onStartNext = {},
            onIncrementCounter = {},
            onDecrementCounter = {}
        )
    }
}
