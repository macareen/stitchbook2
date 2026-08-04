package com.macareen.stitchbook2.feature.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.ui.components.LabelPill
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SecondaryActionButton
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.cardTitle
import com.macareen.stitchbook2.ui.theme.textSecondary
import java.text.DateFormat
import java.util.Date

@Composable
fun ProjectDetailRoute(
    viewModel: ProjectDetailViewModel,
    onEditProject: (String) -> Unit,
    onProjectDeleted: () -> Unit,
    onOpenGuide: (String) -> Unit,
    onEditDraft: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.deletedEvents.collect {
            onProjectDeleted()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.guideCreatedEvents.collect { guideId ->
            onEditDraft(guideId)
        }
    }

    ProjectDetailScreen(
        uiState = uiState,
        onEditProject = onEditProject,
        onDeleteProject = viewModel::deleteProject,
        onOpenGuide = onOpenGuide,
        onEditDraft = onEditDraft,
        onCreateGuide = viewModel::createGuide
    )
}

@Composable
fun ProjectDetailScreen(
    uiState: ProjectDetailUiState,
    onEditProject: (String) -> Unit,
    onDeleteProject: () -> Unit,
    onOpenGuide: (String) -> Unit,
    onEditDraft: (String) -> Unit,
    onCreateGuide: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        ProjectDetailUiState.Loading -> {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        ProjectDetailUiState.NotFound -> {
            DetailMessage(
                title = stringResource(R.string.project_not_found_title),
                description = stringResource(R.string.project_not_found_description),
                modifier = modifier
            )
        }

        ProjectDetailUiState.LoadError -> {
            DetailMessage(
                title = stringResource(R.string.project_load_error_title),
                description = stringResource(R.string.project_load_error_description),
                modifier = modifier
            )
        }

        is ProjectDetailUiState.Content -> {
            ProjectDetailContent(
                state = uiState,
                onEditProject = onEditProject,
                onDeleteProject = onDeleteProject,
                onOpenGuide = onOpenGuide,
                onEditDraft = onEditDraft,
                onCreateGuide = onCreateGuide,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ProjectDetailContent(
    state: ProjectDetailUiState.Content,
    onEditProject: (String) -> Unit,
    onDeleteProject: () -> Unit,
    onOpenGuide: (String) -> Unit,
    onEditDraft: (String) -> Unit,
    onCreateGuide: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showAddGuideDialog by remember { mutableStateOf(false) }
    val project = state.project

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(StitchbookSpacing.large)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { onEditProject(project.id) },
                enabled = !state.isDeleting
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.edit_project),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = StitchbookSpacing.extraSmall)
                )
            }
            TextButton(
                onClick = { showDeleteConfirmation = true },
                enabled = !state.isDeleting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (state.isDeleting) {
                        stringResource(R.string.deleting_project)
                    } else {
                        stringResource(R.string.delete_project)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = StitchbookSpacing.extraSmall)
                )
            }
        }

        Spacer(modifier = Modifier.height(StitchbookSpacing.small))

        ProjectHeaderCard(project = project)

        if (state.deleteFailed) {
            Text(
                text = stringResource(R.string.project_delete_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = StitchbookSpacing.small)
            )
        }

        Spacer(modifier = Modifier.height(StitchbookSpacing.large))
        GuidesSection(
            guideEntries = state.guideEntries,
            isCreatingGuide = state.isCreatingGuide,
            createGuideFailed = state.createGuideFailed,
            onOpenGuide = onOpenGuide,
            onEditDraft = onEditDraft,
            onAddGuide = { showAddGuideDialog = true }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(text = stringResource(R.string.delete_project_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.delete_project_confirmation,
                        project.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteProject()
                    }
                ) {
                    Text(text = stringResource(R.string.delete_project))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAddGuideDialog) {
        AddGuideDialog(
            onConfirm = { name ->
                showAddGuideDialog = false
                onCreateGuide(name)
            },
            onDismiss = { showAddGuideDialog = false }
        )
    }
}

@Composable
private fun AddGuideDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.add_guide_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(R.string.add_guide_name_label)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text(text = stringResource(R.string.add_guide_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun GuidesSection(
    guideEntries: List<GuideListEntry>,
    isCreatingGuide: Boolean,
    createGuideFailed: Boolean,
    onOpenGuide: (String) -> Unit,
    onEditDraft: (String) -> Unit,
    onAddGuide: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.guides_section_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        SecondaryActionButton(
            text = stringResource(R.string.add_guide_action),
            onClick = onAddGuide,
            enabled = !isCreatingGuide
        )
    }
    Spacer(modifier = Modifier.height(4.dp))

    if (createGuideFailed) {
        Text(
            text = stringResource(R.string.add_guide_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    if (guideEntries.isEmpty()) {
        Text(
            text = stringResource(R.string.guides_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            guideEntries.forEach { entry ->
                GuideListItem(
                    entry = entry,
                    onOpenGuide = { onOpenGuide(entry.guide.id.value) },
                    onEditDraft = { onEditDraft(entry.guide.id.value) }
                )
            }
        }
    }
}

/**
 * Renders exactly the entry action [GuideEntryAction] already resolved from
 * persisted state -- never decides Continue/Start/unavailable itself. A
 * Draft-only Guide (no published Revision yet) opens the Draft editor
 * instead of Focus Mode, since Focus Mode has nothing to execute yet.
 */
@Composable
private fun GuideListItem(
    entry: GuideListEntry,
    onOpenGuide: () -> Unit,
    onEditDraft: () -> Unit
) {
    val onClick = if (entry.action == GuideEntryAction.NOT_EXECUTABLE) onEditDraft else onOpenGuide

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(StitchbookSpacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.guide.name,
                    style = MaterialTheme.typography.cardTitle
                )
                if (entry.action == GuideEntryAction.NOT_EXECUTABLE) {
                    QuietText(text = stringResource(R.string.guide_draft_status_label))
                }
            }
            Spacer(modifier = Modifier.width(StitchbookSpacing.small))
            when (entry.action) {
                GuideEntryAction.CONTINUE -> PrimaryActionButton(
                    text = stringResource(R.string.guide_continue_action),
                    onClick = onClick
                )

                GuideEntryAction.START -> PrimaryActionButton(
                    text = stringResource(R.string.focus_start_action),
                    onClick = onClick
                )

                GuideEntryAction.NOT_EXECUTABLE -> SecondaryActionButton(
                    text = stringResource(R.string.guide_edit_draft_action),
                    onClick = onClick
                )
            }
        }
    }
}

@Composable
private fun ProjectHeaderCard(project: Project) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.large)) {
            Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall)) {
                LabelPill(
                    text = stringResource(project.craft.labelResource()),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.textSecondary
                )
                val (statusContainer, statusContent) = project.status.pillColors()
                LabelPill(
                    text = stringResource(project.status.labelResource()),
                    containerColor = statusContainer,
                    contentColor = statusContent
                )
            }
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Text(
                text = project.name,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(project.projectType.labelResource()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.textSecondary,
                modifier = Modifier.padding(top = StitchbookSpacing.extraSmall)
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetaLine(
                    label = stringResource(R.string.project_created_label),
                    value = formatTimestamp(project.createdAt)
                )
                MetaLine(
                    label = stringResource(R.string.project_updated_label),
                    value = formatTimestamp(project.updatedAt)
                )
            }

            if (!project.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(modifier = Modifier.padding(StitchbookSpacing.medium)) {
                        Text(
                            text = stringResource(R.string.project_notes_label).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textSecondary,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(StitchbookSpacing.extraSmall))
                        Text(
                            text = project.notes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun DetailMessage(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT
    ).format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
private fun ProjectDetailPreview() {
    StitchbookTheme {
        ProjectDetailScreen(
            uiState = ProjectDetailUiState.Content(
                Project(
                    id = "preview",
                    name = "Everyday cardigan",
                    craft = Craft.KNITTING,
                    projectType = ProjectType.CARDIGAN,
                    status = ProjectStatus.ACTIVE,
                    notes = "Adjusted the sleeve length.",
                    createdAt = 1_700_000_000_000,
                    updatedAt = 1_700_100_000_000
                ),
                guideEntries = listOf(
                    GuideListEntry(
                        guide = previewGuide(id = "in-progress", name = "Body"),
                        action = GuideEntryAction.CONTINUE
                    ),
                    GuideListEntry(
                        guide = previewGuide(id = "not-started", name = "Sleeves"),
                        action = GuideEntryAction.START
                    ),
                    GuideListEntry(
                        guide = previewGuide(id = "draft-only", name = "Collar"),
                        action = GuideEntryAction.NOT_EXECUTABLE
                    )
                )
            ),
            onEditProject = {},
            onDeleteProject = {},
            onOpenGuide = {},
            onEditDraft = {},
            onCreateGuide = {}
        )
    }
}

private fun previewGuide(id: String, name: String) = Guide(
    id = GuideId(id),
    projectId = "preview",
    name = name,
    notes = null,
    createdAt = 1_700_000_000_000,
    updatedAt = 1_700_100_000_000
)
