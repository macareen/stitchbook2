package com.macareen.stitchbook2.feature.projects

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.GuideStatus
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectNode
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.model.ToolItem
import com.macareen.stitchbook2.domain.model.nextStepFor
import com.macareen.stitchbook2.ui.components.DetailLine
import com.macareen.stitchbook2.ui.components.LabelPill
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.ProjectNodeMap
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SecondaryActionButton
import com.macareen.stitchbook2.ui.components.SectionHeader
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.cardTitle
import com.macareen.stitchbook2.ui.theme.textSecondary
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProjectDetailRoute(
    viewModel: ProjectDetailViewModel,
    onEditProject: (String) -> Unit,
    onProjectDeleted: () -> Unit,
    onOpenGuide: (String) -> Unit,
    onEditDraft: (String) -> Unit,
    onOpenSection: (ProjectSection) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportFeedback by viewModel.exportFeedback.collectAsStateWithLifecycle()
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val projectCounters by viewModel.projectCounters.collectAsStateWithLifecycle()
    val toolbox by viewModel.toolbox.collectAsStateWithLifecycle()

    // Coming back from Focus Mode or the Draft editor can change a guide's Start/Continue state.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshGuideEntries()
    }

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
        onCreateGuide = viewModel::createGuide,
        onCreateGuideFromPdf = viewModel::createGuideFromPdf,
        onUnassignTool = viewModel::unassignTool,
        onOpenSection = onOpenSection,
        exportFeedback = exportFeedback,
        onExportProject = viewModel::exportProject,
        onDismissExportFeedback = viewModel::dismissExportFeedback,
        hubState = ProjectHubState(connections = connections, counters = projectCounters, toolbox = toolbox),
        hubActions = ProjectHubActions(
            onIncrementCounter = viewModel::incrementCounter,
            onDecrementCounter = viewModel::decrementCounter,
            onAddCounter = viewModel::addCounter,
            onAssignTool = viewModel::assignTool,
            onAddNewTool = viewModel::addNewTool
        )
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
    onCreateGuideFromPdf: (String, ByteArray) -> Unit,
    onUnassignTool: (ToolItem) -> Unit,
    onOpenSection: (ProjectSection) -> Unit,
    modifier: Modifier = Modifier,
    exportFeedback: ProjectExportFeedback? = null,
    onExportProject: (ProjectExportFormat, suspend (String) -> Unit) -> Unit = { _, _ -> },
    onDismissExportFeedback: () -> Unit = {},
    hubState: ProjectHubState = ProjectHubState(),
    hubActions: ProjectHubActions = ProjectHubActions()
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
                onCreateGuideFromPdf = onCreateGuideFromPdf,
                onUnassignTool = onUnassignTool,
                onOpenSection = onOpenSection,
                exportFeedback = exportFeedback,
                onExportProject = onExportProject,
                onDismissExportFeedback = onDismissExportFeedback,
                hubState = hubState,
                hubActions = hubActions,
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
    onCreateGuideFromPdf: (String, ByteArray) -> Unit,
    onUnassignTool: (ToolItem) -> Unit,
    onOpenSection: (ProjectSection) -> Unit,
    exportFeedback: ProjectExportFeedback?,
    onExportProject: (ProjectExportFormat, suspend (String) -> Unit) -> Unit,
    onDismissExportFeedback: () -> Unit,
    hubState: ProjectHubState,
    hubActions: ProjectHubActions,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var openSheet by remember { mutableStateOf<HubSheet?>(null) }
    var showAddGuideDialog by remember { mutableStateOf(false) }
    var showCreateFromPdfDialog by remember { mutableStateOf(false) }
    var pendingPdfGuideName by remember { mutableStateOf<String?>(null) }
    val project = state.project

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val name = pendingPdfGuideName
        pendingPdfGuideName = null
        if (uri != null && name != null) {
            coroutineScope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes != null) {
                    onCreateGuideFromPdf(name, bytes)
                }
            }
        }
    }

    // One launcher per format, since CreateDocument fixes its MIME type up front.
    fun exportTo(format: ProjectExportFormat): (Uri?) -> Unit = { uri ->
        if (uri != null) {
            onExportProject(format) { content ->
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Could not open the chosen document for writing.")
                    stream.use { OutputStreamWriter(it, Charsets.UTF_8).use { writer -> writer.write(content) } }
                }
            }
        }
    }
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ProjectExportFormat.JSON.mimeType),
        onResult = exportTo(ProjectExportFormat.JSON)
    )
    val exportMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ProjectExportFormat.MARKDOWN.mimeType),
        onResult = exportTo(ProjectExportFormat.MARKDOWN)
    )

    val nextStep = nextStepFor(
        state.guideEntries.map { entry ->
            GuideStatus(
                guideId = entry.guide.id.value,
                name = entry.guide.name,
                inProgress = entry.action == GuideEntryAction.CONTINUE,
                published = entry.action != GuideEntryAction.NOT_EXECUTABLE
            )
        }
    )
    val nodes = hubNodes(hubState.connections) { node ->
        when (node) {
            ProjectNode.GUIDES -> openSheet = HubSheet.GUIDES
            ProjectNode.TOOLS -> openSheet = HubSheet.TOOLS
            ProjectNode.COUNTERS -> openSheet = HubSheet.COUNTERS
            ProjectNode.PATTERNS, ProjectNode.YARN -> onOpenSection(ProjectSection.MATERIALS)
            ProjectNode.JOURNAL -> onOpenSection(ProjectSection.JOURNAL)
            ProjectNode.TIME -> onOpenSection(ProjectSection.SESSIONS)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StitchbookSpacing.large, vertical = StitchbookSpacing.medium)
    ) {
        ProjectHubHeader(
            project = project,
            isDeleting = state.isDeleting,
            onEdit = { onEditProject(project.id) },
            onShareCard = { onOpenSection(ProjectSection.CARD) },
            onExportJson = { exportJsonLauncher.launch(projectExportFileName(project.name, ProjectExportFormat.JSON)) },
            onExportMarkdown = { exportMarkdownLauncher.launch(projectExportFileName(project.name, ProjectExportFormat.MARKDOWN)) },
            onDelete = { showDeleteConfirmation = true }
        )

        NextStepRow(
            step = nextStep,
            onOpenGuide = onOpenGuide,
            onEditDraft = onEditDraft,
            onAddGuide = { showAddGuideDialog = true }
        )

        ProjectNodeMap(centreLabel = project.typeDisplayLabel(), nodes = nodes)

        if (ProjectNode.entries.all { hubState.connections.count(it) == 0 }) {
            EmptyHubHint()
        }

        ProjectDetailsLine(project)

        if (state.deleteFailed) {
            Text(
                text = stringResource(R.string.project_delete_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        exportFeedback?.let { feedback ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (feedback == ProjectExportFeedback.SAVED) R.string.project_export_saved else R.string.project_export_failed
                    ),
                    color = if (feedback == ProjectExportFeedback.FAILED) MaterialTheme.colorScheme.error else Color.Unspecified,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismissExportFeedback) { Text(text = stringResource(R.string.settings_dismiss)) }
            }
        }
    }

    when (openSheet) {
        HubSheet.GUIDES -> HubBottomSheet(onDismiss = { openSheet = null }) {
            GuidesSection(
                guideEntries = state.guideEntries,
                isCreatingGuide = state.isCreatingGuide,
                createGuideFailed = state.createGuideFailed,
                isImportingPdf = state.isImportingPdf,
                pdfImportError = state.pdfImportError,
                onOpenGuide = { openSheet = null; onOpenGuide(it) },
                onEditDraft = { openSheet = null; onEditDraft(it) },
                onAddGuide = { showAddGuideDialog = true },
                onCreateGuideFromPdf = { showCreateFromPdfDialog = true }
            )
        }
        HubSheet.TOOLS -> HubBottomSheet(onDismiss = { openSheet = null }) {
            ToolsSheetContent(
                craft = project.craft,
                assigned = state.assignedTools,
                toolbox = hubState.toolbox,
                onUnassign = onUnassignTool,
                onAssign = hubActions.onAssignTool,
                onAddNew = hubActions.onAddNewTool
            )
        }
        HubSheet.COUNTERS -> HubBottomSheet(onDismiss = { openSheet = null }) {
            CountersSheetContent(
                counters = hubState.counters,
                onIncrement = hubActions.onIncrementCounter,
                onDecrement = hubActions.onDecrementCounter,
                onAdd = hubActions.onAddCounter
            )
        }
        null -> Unit
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

    if (showCreateFromPdfDialog) {
        CreateGuideFromPdfDialog(
            onConfirm = { name ->
                showCreateFromPdfDialog = false
                pendingPdfGuideName = name
                pickPdfLauncher.launch(arrayOf("application/pdf"))
            },
            onDismiss = { showCreateFromPdfDialog = false }
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
private fun CreateGuideFromPdfDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.create_guide_from_pdf_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.create_guide_from_pdf_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = StitchbookSpacing.small)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.add_guide_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text(text = stringResource(R.string.create_guide_from_pdf_confirm_action))
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
    isImportingPdf: Boolean,
    pdfImportError: PdfImportError?,
    onOpenGuide: (String) -> Unit,
    onEditDraft: (String) -> Unit,
    onAddGuide: () -> Unit,
    onCreateGuideFromPdf: () -> Unit
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

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = onCreateGuideFromPdf,
            enabled = !isImportingPdf && !isCreatingGuide
        ) {
            Text(
                text = if (isImportingPdf) {
                    stringResource(R.string.create_guide_from_pdf_importing)
                } else {
                    stringResource(R.string.create_guide_from_pdf_action)
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }

    if (pdfImportError != null) {
        Text(
            text = stringResource(
                if (pdfImportError == PdfImportError.NO_EXTRACTABLE_TEXT) {
                    R.string.create_guide_from_pdf_no_text_error
                } else {
                    R.string.create_guide_from_pdf_extraction_error
                }
            ),
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

/** Construction and dates in one quiet line, then private notes if any. */
@Composable
private fun ProjectDetailsLine(project: Project) {
    val parts = listOfNotNull(
        project.constructionMethod?.takeIf { it.isNotBlank() },
        project.startDate?.let { stringResource(R.string.project_details_started, it) },
        project.targetDate?.let { stringResource(R.string.project_details_target, it) },
        project.completedDate?.let { stringResource(R.string.project_details_completed, it) }
    )
    if (parts.isNotEmpty()) QuietText(text = parts.joinToString(" · "))
    project.notes?.takeIf { it.isNotBlank() }?.let { notes ->
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Text(text = notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(StitchbookSpacing.medium))
        }
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
            onCreateGuide = {},
            onCreateGuideFromPdf = { _, _ -> },
            onUnassignTool = {},
            onOpenSection = {}
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
