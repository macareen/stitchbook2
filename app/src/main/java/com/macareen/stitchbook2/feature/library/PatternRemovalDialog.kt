package com.macareen.stitchbook2.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing

@Composable
internal fun PatternRemovalDialog(state: PatternRemovalUiState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(state.problem) { if (state.problem != null) scroll.scrollTo(0) }
    AlertDialog(
        onDismissRequest = { if (!state.isRemoving) onDismiss() },
        title = { Text(stringResource(R.string.delete_library_item_title)) },
        text = {
            Column(Modifier.verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                state.problem?.let { problem -> Text(stringResource(if (problem == PatternRemovalProblem.CHANGED)
                    R.string.pattern_remove_changed else R.string.pattern_remove_failed),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                Text(stringResource(R.string.delete_library_item_confirmation, state.item.title))
                if (state.isLoading) CircularProgressIndicator()
                state.preview?.let { preview ->
                    Text(pluralStringResource(R.plurals.pattern_remove_guides, preview.guideIds.size, preview.guideIds.size))
                    Text(pluralStringResource(R.plurals.pattern_remove_projects, preview.projects.size, preview.projects.size))
                    preview.projects.forEach { project ->
                        val status = stringResource(when (project.status) {
                            ProjectStatus.PLANNED -> R.string.status_planned
                            ProjectStatus.ACTIVE -> R.string.status_active
                            ProjectStatus.PAUSED -> R.string.status_paused
                            ProjectStatus.COMPLETED -> R.string.status_completed
                            ProjectStatus.ABANDONED -> R.string.status_abandoned
                        })
                        Text(stringResource(R.string.pattern_remove_project, project.name, status))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.isLoading && !state.isRemoving) {
                Text(stringResource(when {
                    state.isLoading -> R.string.pattern_remove_loading
                    state.preview == null -> R.string.pattern_remove_retry
                    else -> R.string.pattern_remove_reference
                }))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isRemoving) { Text(stringResource(R.string.cancel)) }
        }
    )
}
