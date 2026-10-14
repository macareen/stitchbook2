package com.macareen.stitchbook2.feature.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.execution.*

@Composable
fun GuideRevisionReviewDialog(state: GuideRevisionReviewState, onSelect: (ExecutionAddress) -> Unit,
    onSelectAll: (Boolean) -> Unit, onApply: () -> Unit, onResume: (SavedGuidePlace) -> Unit, onClose: () -> Unit,
    onChoosePublication: (DefinitionRevisionId) -> Unit = {}) {
    val scroll = rememberLazyListState()
    var choosing by remember { mutableStateOf(false) }
    LaunchedEffect(state.failed) { if (state.failed) scroll.animateScrollToItem(0) }
    AlertDialog(onDismissRequest = { if (!state.busy) onClose() },
        title = { Text(stringResource(R.string.revision_review_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 430.dp).testTag("revision_review_list"), state = scroll, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loading) item { CircularProgressIndicator() }
                if (state.failed) item { Text(stringResource(if (state.stale) R.string.revision_review_stale else R.string.revision_review_failed), color = MaterialTheme.colorScheme.error) }
                state.panel?.let { panel ->
                    item { Text(stringResource(R.string.revision_review_current, panel.revision.revisionNumber)) }
                    item { Text(stringResource(R.string.revision_review_kept)) }
                    if (panel.publications.isNotEmpty()) item {
                        Box {
                            TextButton(onClick = { choosing = true }, enabled = !state.busy && !state.stale) {
                                Text(stringResource(R.string.revision_review_choose))
                            }
                            DropdownMenu(expanded = choosing && !state.busy, onDismissRequest = { choosing = false }) {
                                panel.publications.forEach { publication ->
                                    DropdownMenuItem(text = { Text(stringResource(R.string.revision_review_publication, publication.revisionNumber)) },
                                        onClick = { choosing = false; onChoosePublication(publication.id) })
                                }
                            }
                        }
                    }
                    val change = panel.change
                    if (change == null) item { Text(stringResource(R.string.revision_review_latest)) }
                    else {
                        item { Text(stringResource(R.string.revision_review_target, change.targetRevisionNumber), style = MaterialTheme.typography.titleMedium) }
                        item { Text(stringResource(R.string.revision_review_summary, state.selected.size, panel.matches.size, panel.unmatched.size)) }
                        item { Row {
                            TextButton(onClick = { onSelectAll(true) }, enabled = !state.busy) { Text(stringResource(R.string.revision_review_all)) }
                            TextButton(onClick = { onSelectAll(false) }, enabled = !state.busy) { Text(stringResource(R.string.revision_review_none)) }
                        } }
                        itemsIndexed(panel.matches, key = { i, _ -> "match-$i" }) { index, match ->
                            Card(Modifier.fillMaxWidth().testTag("revision_match_$index").toggleable(value = match.candidate.target in state.selected,
                                enabled = !state.busy, onValueChange = { onSelect(match.candidate.target) })) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row { Checkbox(checked = match.candidate.target in state.selected, onCheckedChange = null)
                                        Text(match.context, Modifier.weight(1f)) }
                                    InstructionBlocks(match.candidate.instruction, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (panel.unmatched.isNotEmpty()) item { Text(stringResource(R.string.revision_review_not_carried), style = MaterialTheme.typography.titleMedium) }
                        itemsIndexed(panel.unmatched, key = { i, _ -> "unmatched-$i" }) { _, unmatched ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(unmatched.context, style = MaterialTheme.typography.labelLarge)
                                Text(stringResource(when (unmatched.occurrence.reason) {
                                    UnmatchedProgressReason.REMOVED_OCCURRENCE -> R.string.revision_review_removed
                                    UnmatchedProgressReason.CHANGED_INSTRUCTION -> R.string.revision_review_text_changed
                                    UnmatchedProgressReason.CHANGED_CONTEXT -> R.string.revision_review_scope_changed
                                }))
                                InstructionBlocks(unmatched.occurrence.instruction, style = MaterialTheme.typography.bodyMedium)
                                unmatched.targetText?.takeIf { it != unmatched.occurrence.instruction }?.let {
                                    Text(stringResource(R.string.revision_review_new_text)); InstructionBlocks(it, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    if (panel.saved.isNotEmpty()) item { Text(stringResource(R.string.revision_review_saved_title), style = MaterialTheme.typography.titleMedium) }
                    items(panel.saved, key = { it.work.state.executionId.value }) { saved ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.revision_review_saved_summary, saved.revision.revisionNumber, saved.work.state.completedAddresses.size))
                                Text(saved.context)
                                saved.currentInstruction?.let { InstructionBlocks(it, style = MaterialTheme.typography.bodyMedium) }
                                if (saved.work.state.status == ExecutionStatus.ACTIVE) TextButton(onClick = { onResume(saved) }, enabled = !state.busy && !state.stale) { Text(stringResource(R.string.revision_review_resume)) }
                                else Text(stringResource(R.string.revision_review_finished))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { state.panel?.change?.let { change -> TextButton(onClick = onApply, enabled = !state.busy && !state.stale) { Text(stringResource(R.string.revision_review_apply, change.targetRevisionNumber)) } } },
        dismissButton = { TextButton(onClick = onClose, enabled = !state.busy) { Text(stringResource(R.string.revision_review_close)) } })
}
