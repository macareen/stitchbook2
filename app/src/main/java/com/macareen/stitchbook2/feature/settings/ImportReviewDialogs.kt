package com.macareen.stitchbook2.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.backup.BackupIssue
import com.macareen.stitchbook2.domain.backup.BackupPreview
import com.macareen.stitchbook2.domain.backup.BackupRecordType
import com.macareen.stitchbook2.domain.backup.RestoreMode
import com.macareen.stitchbook2.domain.backup.TypeComparison
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing

/** Shows at most this many validation issues; the rest are summarised as a count. */
private const val MAX_ISSUES_SHOWN = 20

/**
 * The restore review: per-type counts first, then a choice of Merge or
 * Replace. Replace always asks a second time, naming what it removes, since
 * it is the only path that deletes local records.
 */
@Composable
fun ImportReviewDialog(
    review: ImportReview,
    onConfirm: (RestoreMode) -> Unit,
    onDismiss: () -> Unit
) {
    when (val preview = review.preview) {
        is BackupPreview.Ready -> ReadyReviewDialog(preview, onConfirm, onDismiss)
        is BackupPreview.Invalid -> InvalidBackupDialog(preview.issues, onDismiss)
        // The ViewModel reports an unreadable file as feedback, never as a review.
        BackupPreview.Unreadable -> Unit
    }
}

@Composable
private fun ReadyReviewDialog(
    preview: BackupPreview.Ready,
    onConfirm: (RestoreMode) -> Unit,
    onDismiss: () -> Unit
) {
    var confirmingReplace by remember { mutableStateOf(false) }

    if (confirmingReplace) {
        ReplaceConfirmationDialog(
            comparison = preview.comparison,
            onConfirm = { onConfirm(RestoreMode.REPLACE) },
            onBack = { confirmingReplace = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_import_review_title)) },
        text = {
            ScrollableDialogBody {
                QuietText(text = stringResource(R.string.settings_import_review_format, preview.formatVersion))
                val types = preview.comparison.entries.filter { it.value.incoming > 0 || it.value.onlyLocal > 0 }
                if (types.isEmpty()) {
                    Text(text = stringResource(R.string.settings_import_review_empty))
                }
                types.forEach { (type, counts) -> ComparisonRow(type, counts) }
                Text(
                    text = stringResource(R.string.settings_import_review_merge_note),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.settings_import_review_replace_note),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onConfirm(RestoreMode.MERGE) }) {
                    Text(text = stringResource(R.string.settings_import_merge_action))
                }
                TextButton(
                    onClick = { confirmingReplace = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = stringResource(R.string.settings_import_replace_action))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun ComparisonRow(type: BackupRecordType, counts: TypeComparison) {
    Column {
        Text(
            text = stringResource(type.labelResource()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(
                R.string.settings_import_review_type_line,
                counts.new,
                counts.identical,
                counts.conflicting,
                counts.onlyLocal
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ReplaceConfirmationDialog(
    comparison: Map<BackupRecordType, TypeComparison>,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val removals = comparison.filterValues { it.onlyLocal > 0 }
    val overwrites = comparison.values.sumOf { it.conflicting }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text(text = stringResource(R.string.settings_import_replace_confirm_title)) },
        text = {
            ScrollableDialogBody {
                if (removals.isEmpty()) {
                    Text(text = stringResource(R.string.settings_import_replace_confirm_nothing_removed))
                } else {
                    Text(text = stringResource(R.string.settings_import_replace_confirm_removes))
                    removals.forEach { (type, counts) ->
                        Text(
                            text = stringResource(R.string.settings_import_count_line, stringResource(type.labelResource()), counts.onlyLocal),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (overwrites > 0) {
                    Text(text = pluralStringResource(R.plurals.settings_import_replace_confirm_overwrites, overwrites, overwrites))
                }
                QuietText(text = stringResource(R.string.settings_import_replace_confirm_untouched))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(text = stringResource(R.string.settings_import_replace_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun InvalidBackupDialog(issues: List<BackupIssue>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_import_invalid_title)) },
        text = {
            ScrollableDialogBody {
                Text(text = stringResource(R.string.settings_import_invalid_description))
                IssueList(issues)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_dismiss))
            }
        }
    )
}

@Composable
fun IssueList(issues: List<BackupIssue>) {
    issues.take(MAX_ISSUES_SHOWN).forEach { issue ->
        Text(
            text = stringResource(
                R.string.settings_import_issue_line,
                issue.type?.let { stringResource(it.labelResource()) }.orEmpty(),
                issue.recordKey?.let { "\"$it\"" }.orEmpty(),
                issue.detail
            ).trim(),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (issues.size > MAX_ISSUES_SHOWN) {
        Text(
            text = stringResource(R.string.settings_import_invalid_more, issues.size - MAX_ISSUES_SHOWN),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ScrollableDialogBody(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
    ) {
        content()
    }
}

fun BackupRecordType.labelResource(): Int = when (this) {
    BackupRecordType.PROJECTS -> R.string.backup_type_projects
    BackupRecordType.LIBRARY_ITEMS -> R.string.backup_type_library_items
    BackupRecordType.STASH_ITEMS -> R.string.backup_type_stash_items
    BackupRecordType.TOOL_SETS -> R.string.backup_type_tool_sets
    BackupRecordType.TOOL_ITEMS -> R.string.backup_type_tool_items
    BackupRecordType.TOOL_TEMPLATES -> R.string.backup_type_tool_templates
    BackupRecordType.TOOL_ASSIGNMENTS -> R.string.backup_type_tool_assignments
    BackupRecordType.COUNTERS -> R.string.backup_type_counters
    BackupRecordType.COUNTER_NOTES -> R.string.backup_type_counter_notes
    BackupRecordType.YARN_ALLOCATIONS -> R.string.backup_type_yarn_allocations
    BackupRecordType.PATTERN_LINKS -> R.string.backup_type_pattern_links
    BackupRecordType.MILESTONES -> R.string.backup_type_milestones
    BackupRecordType.PHOTOS -> R.string.backup_type_photos
    BackupRecordType.JOURNAL_ENTRIES -> R.string.backup_type_journal_entries
    BackupRecordType.SESSIONS -> R.string.backup_type_sessions
}
