package com.macareen.stitchbook2.feature.focus

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
fun GuideFocusRoute(viewModel: GuideFocusViewModel) {
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
        onDecrementCounter = viewModel::onDecrementCounter
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
    modifier: Modifier = Modifier
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = StitchbookSpacing.large,
                    vertical = StitchbookSpacing.medium
                )
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall)
        ) {
            QuietText(
                text = state.guideName,
                style = MaterialTheme.typography.sectionLabel
            )
            if (state.breadcrumbs.isNotEmpty()) {
                QuietText(text = state.breadcrumbs.joinToString(separator = " › "))
            }
        }

        // Fixed (not scrollable) so counters stay reachable regardless of
        // instruction length, the same reachability rationale as the pinned
        // action row below -- PRODUCT_SPEC.md 6.3's "active crafting screen":
        // tracking counters without leaving Focus Mode.
        if (state.projectCounters.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = StitchbookSpacing.large),
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
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = StitchbookSpacing.large),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = state.instructionText,
                style = MaterialTheme.typography.instruction,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (state.positions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
                Column(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall)
                ) {
                    state.positions.forEach { position -> PositionLine(position) }
                }
            }

            state.feedback?.let { feedback ->
                Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
                QuietText(text = focusFeedbackText(feedback))
            }

            Spacer(modifier = Modifier.height(StitchbookSpacing.large))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(StitchbookSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
            ) {
                SecondaryActionButton(
                    text = stringResource(R.string.focus_previous_action),
                    onClick = onPrevious,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f)
                )
                PrimaryActionButton(
                    text = stringResource(R.string.focus_complete_action),
                    onClick = onComplete,
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f)
                )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
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
        is StructuralPosition.RangePosition -> stringResource(
            R.string.focus_range_position,
            position.unitLabel.replaceFirstChar { it.uppercase() },
            position.currentValue,
            position.startInclusive,
            position.endInclusive
        )

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
    QuietText(text = text)
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
