package com.macareen.stitchbook2.feature.journal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.data.photos.documentDisplayName
import com.macareen.stitchbook2.data.photos.persistReadAccess
import com.macareen.stitchbook2.domain.model.JournalEntry
import com.macareen.stitchbook2.domain.model.Milestone
import com.macareen.stitchbook2.domain.model.Photo
import com.macareen.stitchbook2.domain.model.PhotoRole
import com.macareen.stitchbook2.domain.model.PickedDocument
import com.macareen.stitchbook2.ui.components.ChoiceChipRow
import com.macareen.stitchbook2.ui.components.ContentCard
import com.macareen.stitchbook2.ui.components.DateField
import com.macareen.stitchbook2.ui.components.EmptyState
import com.macareen.stitchbook2.ui.components.PhotoThumbnail
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SectionHeader
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.cardTitle
import com.macareen.stitchbook2.ui.theme.textSecondary

@Composable
fun ProjectJournalRoute(viewModel: ProjectJournalViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProjectJournalScreen(
        uiState = uiState,
        actions = JournalActions(
            onAddPhotos = viewModel::addPhotos,
            onUpdatePhoto = viewModel::updatePhoto,
            onRelinkPhoto = viewModel::relinkPhoto,
            onRemovePhoto = viewModel::removePhoto,
            onSaveMilestone = viewModel::saveMilestone,
            onMoveMilestone = viewModel::moveMilestone,
            onDeleteMilestone = viewModel::deleteMilestone,
            onSaveEntry = viewModel::saveEntry,
            onDeleteEntry = viewModel::deleteEntry,
            onDismissFeedback = viewModel::dismissFeedback
        )
    )
}

/** Grouped so the screen signature stays readable; each maps 1:1 to a ViewModel function. */
data class JournalActions(
    val onAddPhotos: (List<PickedDocument>) -> Unit = {},
    val onUpdatePhoto: (Photo, String, String, String?, PhotoRole) -> Unit = { _, _, _, _, _ -> },
    val onRelinkPhoto: (Photo, PickedDocument) -> Unit = { _, _ -> },
    val onRemovePhoto: (Photo) -> Unit = {},
    val onSaveMilestone: (Milestone?, String, String, String) -> Unit = { _, _, _, _ -> },
    val onMoveMilestone: (String, Int) -> Unit = { _, _ -> },
    val onDeleteMilestone: (Milestone) -> Unit = {},
    val onSaveEntry: (JournalEntry?, String, String, String) -> Unit = { _, _, _, _ -> },
    val onDeleteEntry: (JournalEntry) -> Unit = {},
    val onDismissFeedback: () -> Unit = {}
)

private sealed interface JournalDialog {
    data class EditPhoto(val photo: Photo) : JournalDialog
    data class EditMilestone(val milestone: Milestone?) : JournalDialog
    data class EditEntry(val entry: JournalEntry?) : JournalDialog
    data class ConfirmRemovePhoto(val photo: Photo) : JournalDialog
    data class ConfirmDeleteMilestone(val milestone: Milestone) : JournalDialog
    data class ConfirmDeleteEntry(val entry: JournalEntry) : JournalDialog
}

@Composable
fun ProjectJournalScreen(
    uiState: ProjectJournalUiState,
    actions: JournalActions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<JournalDialog?>(null) }
    var relinkTarget by remember { mutableStateOf<Photo?>(null) }

    fun picked(uri: Uri): PickedDocument {
        persistReadAccess(context, uri)
        return PickedDocument(uri.toString(), documentDisplayName(context, uri))
    }

    val addPhotosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) actions.onAddPhotos(uris.map(::picked))
    }
    val relinkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = relinkTarget
        relinkTarget = null
        if (uri != null && target != null) actions.onRelinkPhoto(target, picked(uri))
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
            Text(text = stringResource(R.string.journal_title), style = MaterialTheme.typography.headlineMedium)
            QuietText(text = stringResource(R.string.journal_privacy_note))
        }

        uiState.feedback?.let { message ->
            item { FeedbackLine(message = message, onDismiss = actions.onDismissFeedback) }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.journal_photos_section),
                icon = Icons.Outlined.PhotoLibrary,
                actionLabel = stringResource(R.string.journal_add_photos),
                onAction = { addPhotosLauncher.launch(arrayOf("image/*")) }
            )
            if (uiState.photos.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.journal_photos_empty_title),
                    description = stringResource(R.string.journal_photos_empty_description),
                    icon = Icons.Outlined.PhotoLibrary
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                    items(uiState.photos, key = { it.id }) { photo ->
                        PhotoTile(photo = photo, onClick = { dialog = JournalDialog.EditPhoto(photo) })
                    }
                }
            }
        }

        uiState.beforeAfter?.let { (before, after) ->
            item { BeforeAfterCard(before = before, after = after) }
        }

        item {
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            SectionHeader(
                title = stringResource(R.string.journal_milestones_section),
                icon = Icons.Outlined.Flag,
                actionLabel = stringResource(R.string.journal_add),
                onAction = { dialog = JournalDialog.EditMilestone(null) }
            )
            if (uiState.milestones.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.journal_milestones_empty_title),
                    description = stringResource(R.string.journal_milestones_empty_description),
                    icon = Icons.Outlined.Flag
                )
            }
        }
        items(uiState.milestones, key = { it.id }) { milestone ->
            MilestoneRow(
                milestone = milestone,
                isFirst = milestone.id == uiState.milestones.first().id,
                isLast = milestone.id == uiState.milestones.last().id,
                onMove = { direction -> actions.onMoveMilestone(milestone.id, direction) },
                onEdit = { dialog = JournalDialog.EditMilestone(milestone) },
                onDelete = { dialog = JournalDialog.ConfirmDeleteMilestone(milestone) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            SectionHeader(
                title = stringResource(R.string.journal_entries_section),
                icon = Icons.Outlined.AutoStories,
                actionLabel = stringResource(R.string.journal_write),
                onAction = { dialog = JournalDialog.EditEntry(null) }
            )
            if (uiState.entries.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.journal_entries_empty_title),
                    description = stringResource(R.string.journal_entries_empty_description),
                    icon = Icons.Outlined.AutoStories
                )
            }
        }
        items(uiState.entries, key = { it.id }) { entry ->
            EntryCard(
                entry = entry,
                onEdit = { dialog = JournalDialog.EditEntry(entry) },
                onDelete = { dialog = JournalDialog.ConfirmDeleteEntry(entry) }
            )
        }
    }

    when (val current = dialog) {
        null -> Unit
        is JournalDialog.EditPhoto -> PhotoDialog(
            photo = current.photo,
            milestones = uiState.milestones,
            onSave = { caption, date, milestoneId, role ->
                dialog = null
                actions.onUpdatePhoto(current.photo, caption, date, milestoneId, role)
            },
            onRelink = {
                dialog = null
                relinkTarget = current.photo
                relinkLauncher.launch(arrayOf("image/*"))
            },
            onRemove = { dialog = JournalDialog.ConfirmRemovePhoto(current.photo) },
            onDismiss = { dialog = null }
        )
        is JournalDialog.EditMilestone -> MilestoneDialog(
            original = current.milestone,
            onSave = { title, date, notes ->
                dialog = null
                actions.onSaveMilestone(current.milestone, title, date, notes)
            },
            onDismiss = { dialog = null }
        )
        is JournalDialog.EditEntry -> EntryDialog(
            original = current.entry,
            onSave = { date, title, body ->
                dialog = null
                actions.onSaveEntry(current.entry, date, title, body)
            },
            onDismiss = { dialog = null }
        )
        is JournalDialog.ConfirmRemovePhoto -> ConfirmDialog(
            title = stringResource(R.string.journal_remove_photo_title),
            message = stringResource(
                R.string.journal_remove_photo_message,
                current.photo.displayName ?: stringResource(R.string.journal_photo_unnamed)
            ),
            confirmLabel = stringResource(R.string.journal_remove_photo_confirm),
            onConfirm = {
                dialog = null
                actions.onRemovePhoto(current.photo)
            },
            onDismiss = { dialog = null }
        )
        is JournalDialog.ConfirmDeleteMilestone -> ConfirmDialog(
            title = stringResource(R.string.journal_delete_milestone_title),
            message = stringResource(R.string.journal_delete_milestone_message, current.milestone.title),
            confirmLabel = stringResource(R.string.journal_delete),
            onConfirm = {
                dialog = null
                actions.onDeleteMilestone(current.milestone)
            },
            onDismiss = { dialog = null }
        )
        is JournalDialog.ConfirmDeleteEntry -> ConfirmDialog(
            title = stringResource(R.string.journal_delete_entry_title),
            message = stringResource(R.string.journal_delete_entry_message),
            confirmLabel = stringResource(R.string.journal_delete),
            onConfirm = {
                dialog = null
                actions.onDeleteEntry(current.entry)
            },
            onDismiss = { dialog = null }
        )
    }
}

@Composable
private fun PhotoTile(photo: Photo, onClick: () -> Unit) {
    Column(modifier = Modifier.size(width = 120.dp, height = 156.dp)) {
        PhotoThumbnail(
            uri = photo.uri,
            contentDescription = photo.caption ?: photo.displayName,
            modifier = Modifier
                .size(120.dp)
                .clickable(onClickLabel = stringResource(R.string.journal_edit_photo), onClick = onClick)
        )
        Text(
            text = when (photo.role) {
                PhotoRole.BEFORE -> stringResource(R.string.journal_role_before)
                PhotoRole.AFTER -> stringResource(R.string.journal_role_after)
                PhotoRole.NONE -> photo.caption ?: photo.takenDate.orEmpty()
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(top = StitchbookSpacing.extraSmall)
        )
    }
}

@Composable
private fun BeforeAfterCard(before: Photo, after: Photo) {
    ContentCard {
        Text(text = stringResource(R.string.journal_before_after), style = MaterialTheme.typography.cardTitle)
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            listOf(before to R.string.journal_role_before, after to R.string.journal_role_after).forEach { (photo, label) ->
                Column(modifier = Modifier.weight(1f)) {
                    PhotoThumbnail(
                        uri = photo.uri,
                        contentDescription = stringResource(label),
                        maxSizePx = 800,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.8f)
                    )
                    QuietText(
                        text = listOfNotNull(stringResource(label), photo.takenDate).joinToString(" · "),
                        modifier = Modifier.padding(top = StitchbookSpacing.extraSmall)
                    )
                }
            }
        }
    }
}

@Composable
private fun MilestoneRow(
    milestone: Milestone,
    isFirst: Boolean,
    isLast: Boolean,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {}
            ) {
                Text(text = milestone.title, style = MaterialTheme.typography.titleMedium)
                QuietText(
                    text = milestone.reachedDate ?: stringResource(R.string.journal_milestone_not_reached)
                )
                milestone.notes?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            }
            IconButton(onClick = { onMove(-1) }, enabled = !isFirst) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.journal_move_up))
            }
            IconButton(onClick = { onMove(1) }, enabled = !isLast) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.journal_move_down))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDelete) { Text(text = stringResource(R.string.journal_delete)) }
            TextButton(onClick = onEdit) { Text(text = stringResource(R.string.journal_edit)) }
        }
    }
}

@Composable
private fun EntryCard(entry: JournalEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    ContentCard {
        QuietText(text = entry.entryDate)
        entry.title?.let { Text(text = it, style = MaterialTheme.typography.cardTitle) }
        Spacer(modifier = Modifier.height(StitchbookSpacing.extraSmall))
        Text(text = entry.body, style = MaterialTheme.typography.bodyLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.journal_delete))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.journal_edit))
            }
        }
    }
}

@Composable
private fun PhotoDialog(
    photo: Photo,
    milestones: List<Milestone>,
    onSave: (String, String, String?, PhotoRole) -> Unit,
    onRelink: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var caption by remember { mutableStateOf(photo.caption.orEmpty()) }
    var takenDate by remember { mutableStateOf(photo.takenDate.orEmpty()) }
    var role by remember { mutableStateOf(photo.role) }
    var milestoneId by remember { mutableStateOf(photo.milestoneId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = photo.displayName ?: stringResource(R.string.journal_photo_unnamed)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
            ) {
                PhotoThumbnail(
                    uri = photo.uri,
                    contentDescription = null,
                    maxSizePx = 800,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.25f)
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text(text = stringResource(R.string.journal_caption)) },
                    modifier = Modifier.fillMaxWidth()
                )
                DateField(
                    value = takenDate,
                    onValueChange = { takenDate = it },
                    label = stringResource(R.string.journal_photo_date)
                )
                QuietText(text = stringResource(R.string.journal_comparison_role))
                ChoiceChipRow(
                    options = PhotoRole.entries,
                    selected = role,
                    optionLabel = {
                        when (it) {
                            PhotoRole.NONE -> stringResource(R.string.journal_role_none)
                            PhotoRole.BEFORE -> stringResource(R.string.journal_role_before)
                            PhotoRole.AFTER -> stringResource(R.string.journal_role_after)
                        }
                    },
                    onSelected = { role = it }
                )
                if (milestones.isNotEmpty()) {
                    QuietText(text = stringResource(R.string.journal_photo_milestone))
                    ChoiceChipRow(
                        options = listOf<Milestone?>(null) + milestones,
                        selected = milestones.firstOrNull { it.id == milestoneId },
                        optionLabel = { it?.title ?: stringResource(R.string.journal_milestone_none) },
                        onSelected = { milestoneId = it?.id }
                    )
                }
                Row {
                    TextButton(onClick = onRelink) { Text(text = stringResource(R.string.journal_relink)) }
                    TextButton(onClick = onRemove) {
                        Text(text = stringResource(R.string.journal_remove_photo_confirm), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(caption, takenDate, milestoneId, role) }) {
                Text(text = stringResource(R.string.journal_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun MilestoneDialog(
    original: Milestone?,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var date by remember { mutableStateOf(original?.reachedDate.orEmpty()) }
    var notes by remember { mutableStateOf(original?.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (original == null) R.string.journal_new_milestone else R.string.journal_edit_milestone
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.journal_milestone_title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                DateField(
                    value = date,
                    onValueChange = { date = it },
                    label = stringResource(R.string.journal_milestone_date)
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
            TextButton(onClick = { onSave(title, date, notes) }, enabled = title.isNotBlank()) {
                Text(text = stringResource(R.string.journal_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun EntryDialog(
    original: JournalEntry?,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var date by remember { mutableStateOf(original?.entryDate ?: java.time.LocalDate.now().toString()) }
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var body by remember { mutableStateOf(original?.body.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(if (original == null) R.string.journal_new_entry else R.string.journal_edit_entry))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
            ) {
                DateField(value = date, onValueChange = { date = it }, label = stringResource(R.string.journal_entry_date))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.journal_entry_title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    minLines = 5,
                    label = { Text(text = stringResource(R.string.journal_entry_body)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(date, title, body) }, enabled = body.isNotBlank()) {
                Text(text = stringResource(R.string.journal_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun FeedbackLine(message: JournalFeedback, onDismiss: () -> Unit) {
    val text = when (message) {
        JournalFeedback.INVALID_DATE -> stringResource(R.string.project_date_invalid)
        JournalFeedback.TITLE_REQUIRED -> stringResource(R.string.journal_error_title_required)
        JournalFeedback.BODY_REQUIRED -> stringResource(R.string.journal_error_body_required)
        JournalFeedback.SAVE_FAILED -> stringResource(R.string.materials_error_save_failed)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.dismiss)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProjectJournalEmptyPreview() {
    StitchbookTheme {
        ProjectJournalScreen(
            uiState = ProjectJournalUiState(isLoading = false, projectName = "Granny-square blanket"),
            actions = JournalActions()
        )
    }
}
