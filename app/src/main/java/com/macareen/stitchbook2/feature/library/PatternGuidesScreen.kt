package com.macareen.stitchbook2.feature.library

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.macareen.stitchbook2.domain.model.PickedDocument
import com.macareen.stitchbook2.domain.ravelry.RavelryBrowserUrls
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.feature.projects.labelResource
import com.macareen.stitchbook2.ui.theme.CozyPastels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.PatternNames
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.ravelry.RavelryRequestStage
import com.macareen.stitchbook2.domain.ravelry.DownloadRavelryPdf
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SecondaryActionButton
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme

@Composable
fun PatternGuidesRoute(
    viewModel: PatternGuidesViewModel,
    onOpenPdf: (String) -> Unit,
    onEditGuide: (String) -> Unit,
    onOpenProject: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val createdGuideId by viewModel.createdGuideId.collectAsStateWithLifecycle()
    val startedProjectId by viewModel.startedProjectId.collectAsStateWithLifecycle()
    val pdfReview by viewModel.pdfChangeReview.collectAsStateWithLifecycle()
    val editReview by viewModel.patternEditReview.collectAsStateWithLifecycle()
    LaunchedEffect(createdGuideId) {
        createdGuideId?.let {
            viewModel.consumeCreatedGuide()
            onEditGuide(it)
        }
    }
    LaunchedEffect(startedProjectId) {
        startedProjectId?.let {
            viewModel.consumeStartedProject()
            onOpenProject(it)
        }
    }
    PatternGuidesScreen(
        uiState,
        onOpenPdf,
        onEditGuide,
        viewModel::createGuide,
        onStartProject = viewModel::startProject,
        onGetRavelryPdf = viewModel::getRavelryPdf,
        onAttachPdf = viewModel::attachPdf,
        onCopyGuide = viewModel::copyGuideToSize,
        onRemoveGuide = viewModel::removeUnusedGuide,
        onArchiveGuide = viewModel::setGuideArchived,
        onUnlinkPdf = { viewModel.requestPdfChange(null) },
        onEditPattern = viewModel::editPattern
    )
    editReview?.let { review ->
        androidx.compose.runtime.key(review.requestId) {
            PatternEditDialog(review, viewModel::dismissPatternEdit, { viewModel.reloadPatternEdit(review) },
                { title, craft, author, url, tags, notes, metadata ->
                    viewModel.savePatternEdit(review, title, craft, author, url, tags, notes, metadata)
                })
        }
    }
    pdfReview?.let { review ->
        PatternPdfChangeDialog(review, viewModel::dismissPdfChange, { viewModel.confirmPdfChange(review) })
    }
}

/** A pattern, its original file, and its guides by size. */
@Composable
fun PatternGuidesScreen(
    uiState: PatternGuidesUiState,
    onOpenPdf: (String) -> Unit,
    onEditGuide: (String) -> Unit,
    onCreateGuide: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onStartProject: () -> Unit = {},
    onGetRavelryPdf: () -> Unit = {},
    onAttachPdf: (PickedDocument) -> Unit = {},
    onCopyGuide: (String, String) -> Unit = { _, _ -> },
    onRemoveGuide: (String) -> Unit = {},
    onArchiveGuide: (String, Boolean) -> Unit = { _, _ -> },
    onUnlinkPdf: () -> Unit = {},
    onEditPattern: () -> Unit = {}
) {
    when (uiState) {
        PatternGuidesUiState.Loading -> Box(modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        PatternGuidesUiState.Missing -> Box(modifier.fillMaxSize().padding(StitchbookSpacing.large)) {
            QuietText(text = stringResource(R.string.pattern_guides_missing))
        }

        is PatternGuidesUiState.Content ->
            Content(uiState, onOpenPdf, onEditGuide, onCreateGuide, onStartProject, onGetRavelryPdf, onAttachPdf, onCopyGuide, onRemoveGuide, onArchiveGuide, onUnlinkPdf, onEditPattern, modifier)
    }
}

@Composable
private fun Content(
    state: PatternGuidesUiState.Content,
    onOpenPdf: (String) -> Unit,
    onEditGuide: (String) -> Unit,
    onCreateGuide: (String, Boolean) -> Unit,
    onStartProject: () -> Unit,
    onGetRavelryPdf: () -> Unit,
    onAttachPdf: (PickedDocument) -> Unit,
    onCopyGuide: (String, String) -> Unit,
    onRemoveGuide: (String) -> Unit,
    onArchiveGuide: (String, Boolean) -> Unit,
    onUnlinkPdf: () -> Unit,
    onEditPattern: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var removingGuideId by rememberSaveable { mutableStateOf<String?>(null) }
    var copyingGuideId by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfMenuOpen by remember { mutableStateOf(false) }
    var attachFailed by rememberSaveable { mutableStateOf(false) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val document = withContext(Dispatchers.IO) {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { index -> index >= 0 }?.let(it::getString) else null
                    }
                    PickedDocument(uri.toString(), name)
                }
                attachFailed = false
                onAttachPdf(document)
            } catch (error: kotlinx.coroutines.CancellationException) { throw error
            } catch (_: Exception) { attachFailed = true }
        }
    }
    var showNewGuide by rememberSaveable { mutableStateOf(false) }
    var requestedSize by rememberSaveable { mutableStateOf("") }
    Column(
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StitchbookSpacing.large, vertical = StitchbookSpacing.medium)
    ) {
        PatternPageHeader(state.pattern, state.completedProjects)
        TextButton(onClick = onEditPattern, enabled = !state.isCreating && !state.ravelryPdf.isDownloading) {
            Text(stringResource(R.string.pattern_edit_action))
        }
        PatternOverview(state.pattern)
        PrimaryActionButton(
            text = stringResource(R.string.pattern_guides_start_project),
            onClick = onStartProject,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.pattern.pdfUri != null) {
            SecondaryActionButton(
                text = stringResource(R.string.pattern_guides_open_pattern),
                onClick = { onOpenPdf(state.pattern.id) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.pattern.pdfUri != null) {
            Box {
                TextButton(onClick = { pdfMenuOpen = true }, enabled = !state.isCreating && !state.ravelryPdf.isDownloading) {
                    Text(stringResource(R.string.pattern_pdf_actions))
                }
                DropdownMenu(expanded = pdfMenuOpen, onDismissRequest = { pdfMenuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.pattern_pdf_replace)) }, onClick = {
                        pdfMenuOpen = false
                        pickPdf.launch(arrayOf("application/pdf"))
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.pattern_pdf_unlink)) }, onClick = {
                        pdfMenuOpen = false
                        onUnlinkPdf()
                    })
                }
            }
        }
        if (state.canGetRavelryPdf) {
            SecondaryActionButton(
                text = stringResource(
                    if (state.ravelryPdf.isDownloading) R.string.ravelry_pdf_downloading else R.string.ravelry_pdf_get
                ),
                onClick = onGetRavelryPdf,
                enabled = !state.ravelryPdf.isDownloading,
                modifier = Modifier.fillMaxWidth()
            )
        }
        RavelryPdfMessage(state.ravelryPdf.result)
        if (state.pattern.pdfUri == null) {
            if (state.pattern.ravelryPatternId != null || state.canGetRavelryPdf) {
                SecondaryActionButton(text = stringResource(R.string.pattern_pdf_browser), onClick = {
                    val result = state.ravelryPdf.result as? DownloadRavelryPdf.Result.Failed
                    uriHandler.openUri(RavelryBrowserUrls.validated(result?.browserUrl) ?: RavelryBrowserUrls.LIBRARY)
                }, modifier = Modifier.fillMaxWidth())
            }
            SecondaryActionButton(text = stringResource(R.string.pattern_pdf_attach),
                onClick = { pickPdf.launch(arrayOf("application/pdf")) },
                enabled = !state.isCreating && !state.ravelryPdf.isDownloading, modifier = Modifier.fillMaxWidth())
        }


        Text(text = stringResource(R.string.pattern_guides_title), style = MaterialTheme.typography.titleMedium)
        if (state.guides.isEmpty() && state.archivedGuides.isEmpty() && state.sizeChoices.isEmpty()) {
            QuietText(text = stringResource(R.string.pattern_guides_empty))
        }
        SecondaryActionButton(
            text = stringResource(R.string.pattern_guides_new),
            onClick = { requestedSize = ""; showNewGuide = true },
            enabled = !state.isCreating,
            modifier = Modifier.fillMaxWidth()
        )
        state.sizeChoices.forEachIndexed { index, size ->
            val entries = state.guides.filter {
                com.macareen.stitchbook2.domain.parsing.PatternSizes.indexOf(it.guide.sizeLabel.orEmpty(), state.sizeChoices) == index
            }
            if (entries.isEmpty()) {
                SizeGuideRow(size, enabled = !state.isCreating, onBuild = {
                    requestedSize = size
                    showNewGuide = true
                })
            } else entries.forEach { entry -> GuideWithCopy(entry, !state.isCreating, { onEditGuide(entry.guide.id.value) }, { copyingGuideId = entry.guide.id.value }, { removingGuideId = entry.guide.id.value }, { onArchiveGuide(entry.guide.id.value, !entry.guide.isArchived) }) }
        }
        state.guides.filter {
            com.macareen.stitchbook2.domain.parsing.PatternSizes.indexOf(it.guide.sizeLabel.orEmpty(), state.sizeChoices) == null
        }.forEach { entry -> GuideWithCopy(entry, !state.isCreating, { onEditGuide(entry.guide.id.value) }, { copyingGuideId = entry.guide.id.value }, { removingGuideId = entry.guide.id.value }, { onArchiveGuide(entry.guide.id.value, !entry.guide.isArchived) }) }
        if (state.archivedGuides.isNotEmpty()) {
            TextButton(onClick = { showArchived = !showArchived }) {
                Text(if (showArchived) stringResource(R.string.guide_archived_hide) else stringResource(R.string.guide_archived_count, state.archivedGuides.size))
            }
            if (showArchived) state.archivedGuides.forEach { entry ->
                QuietText(stringResource(R.string.guide_archived_status))
                GuideWithCopy(entry, !state.isCreating, { onEditGuide(entry.guide.id.value) }, {},
                    { removingGuideId = entry.guide.id.value }, { onArchiveGuide(entry.guide.id.value, false) })
            }
        }
        if (state.guideArchiveFailed) Text(stringResource(R.string.guide_archive_failed), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium)
        if (state.guideRemovalFailed) Text(stringResource(R.string.guide_remove_failed),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        PatternDetailsCard(state.pattern)
        val problem = when {
            attachFailed || state.pdfProblem == PatternPdfProblem.ATTACH_FAILED -> R.string.pattern_pdf_attach_failed
            state.pdfProblem == PatternPdfProblem.NO_TEXT -> R.string.pattern_guides_pdf_no_text
            state.pdfProblem == PatternPdfProblem.UNREADABLE -> R.string.pattern_guides_pdf_unreadable
            state.createFailed -> R.string.pattern_guides_create_failed
            else -> null
        }
        if (problem != null) {
            Text(
                text = stringResource(problem),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    (state.guides + state.archivedGuides).firstOrNull { it.guide.id.value == removingGuideId }?.let { entry ->
        AlertDialog(onDismissRequest = { removingGuideId = null },
            title = { Text(stringResource(R.string.guide_remove_action)) },
            text = { Text(stringResource(R.string.guide_remove_confirmation, entry.guide.name)) },
            confirmButton = { TextButton(enabled = !state.isCreating, onClick = {
                onRemoveGuide(entry.guide.id.value); removingGuideId = null
            }) { Text(stringResource(R.string.guide_remove_action)) } },
            dismissButton = { TextButton(onClick = { removingGuideId = null }) { Text(stringResource(R.string.cancel)) } })
    }

    state.guides.firstOrNull { it.guide.id.value == copyingGuideId }?.let { source ->
        CopyGuideDialog(source.guide.sizeLabel.orEmpty(), state.sizeChoices,
            state.guides.mapNotNull { it.guide.sizeLabel }, state.isCreating, state.createFailed,
            { target -> onCopyGuide(source.guide.id.value, target) }, { if (!state.isCreating) copyingGuideId = null })
    }

    if (showNewGuide) {
        NewGuideDialog(
            initialSize = requestedSize,
            patternTitle = PatternNames.title(state.pattern.title, state.pattern.pdfFileName),
            sizeChoices = state.sizeChoices,
            canFillFromPattern = state.pattern.pdfUri != null,
            onCreate = { size, fromPattern ->
                showNewGuide = false
                onCreateGuide(size, fromPattern)
            },
            onDismiss = { showNewGuide = false }
        )
    }
}

@Composable
private fun RavelryPdfMessage(result: DownloadRavelryPdf.Result?) {
    when (result) {
        null -> Unit
        is DownloadRavelryPdf.Result.Saved -> {
            QuietText(
                text = stringResource(
                    if (result.alreadyInFolder) R.string.ravelry_pdf_linked_existing else R.string.ravelry_pdf_saved,
                    result.fileName
                )
            )
            if (result.otherPdfs > 0) {
                QuietText(text = pluralStringResource(R.plurals.ravelry_pdf_more_files, result.otherPdfs, result.otherPdfs))
            }
        }
        is DownloadRavelryPdf.Result.Failed -> Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall)) {
            Text(text = stringResource(ravelryProblemText(result.problem)), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
            result.httpStatus?.let { status ->
                QuietText(text = stringResource(R.string.ravelry_pdf_http_status, status,
                    result.stage?.let { stringResource(ravelryStageText(it)) }.orEmpty()))
            }
        }
    }
}

private fun ravelryProblemText(problem: DownloadRavelryPdf.Problem): Int = when (problem) {
    DownloadRavelryPdf.Problem.NOT_FROM_RAVELRY -> R.string.ravelry_pdf_not_from_ravelry
    DownloadRavelryPdf.Problem.NO_KEY -> R.string.ravelry_pdf_no_key
    DownloadRavelryPdf.Problem.NO_FOLDER -> R.string.ravelry_pdf_no_folder
    DownloadRavelryPdf.Problem.FOLDER_READ_ONLY -> R.string.ravelry_pdf_folder_read_only
    DownloadRavelryPdf.Problem.KEY_REFUSED -> R.string.ravelry_pdf_key_refused
    DownloadRavelryPdf.Problem.OFFLINE -> R.string.ravelry_pdf_offline
    DownloadRavelryPdf.Problem.NOT_IN_LIBRARY -> R.string.ravelry_pdf_not_in_library
    DownloadRavelryPdf.Problem.NO_PDF -> R.string.ravelry_pdf_no_pdf
    DownloadRavelryPdf.Problem.NOT_A_PDF -> R.string.ravelry_pdf_not_a_pdf
    DownloadRavelryPdf.Problem.UNREADABLE_ANSWER -> R.string.ravelry_pdf_unreadable_answer
    DownloadRavelryPdf.Problem.SAVE_FAILED -> R.string.ravelry_pdf_save_failed
    DownloadRavelryPdf.Problem.NO_DIRECT_DOWNLOAD -> R.string.ravelry_pdf_no_direct_download
    DownloadRavelryPdf.Problem.PERMISSION_DENIED -> R.string.ravelry_pdf_permission_denied
    DownloadRavelryPdf.Problem.RATE_LIMITED -> R.string.ravelry_pdf_rate_limited
    DownloadRavelryPdf.Problem.SERVER_UNAVAILABLE -> R.string.ravelry_pdf_server_unavailable
    DownloadRavelryPdf.Problem.LINK_EXPIRED -> R.string.ravelry_pdf_link_expired
}

private fun ravelryStageText(stage: RavelryRequestStage): Int = when (stage) {
    RavelryRequestStage.ACCOUNT -> R.string.ravelry_stage_account
    RavelryRequestStage.VOLUME_FILES -> R.string.ravelry_stage_files
    RavelryRequestStage.PDF_LINK -> R.string.ravelry_stage_link
    RavelryRequestStage.PDF_FILE -> R.string.ravelry_stage_download
    else -> R.string.ravelry_stage_library
}

@Composable
private fun GuideRow(entry: PatternGuideEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().testTag("pattern-guide-${entry.guide.id.value}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(StitchbookSpacing.medium)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.guide.sizeLabel?.let { stringResource(R.string.pattern_guides_size, it) } ?: entry.guide.name,
                    style = MaterialTheme.typography.titleMedium
                )
                QuietText(text = entry.guide.name)
            }
            QuietText(text = stringResource(if (entry.isPublished) R.string.pattern_guides_ready else R.string.pattern_guides_draft))
        }
    }
}

@Composable
private fun GuideWithCopy(entry: PatternGuideEntry, enabled: Boolean, onEdit: () -> Unit, onCopy: () -> Unit,
    onRemove: () -> Unit, onArchive: () -> Unit) {
    Column {
        GuideRow(entry, onEdit)
        if (entry.guide.projectId == null) FlowRow {
            TextButton(onClick = onArchive, enabled = enabled) {
                Text(stringResource(if (entry.guide.isArchived) R.string.guide_restore_action else R.string.guide_archive_action))
            }
            TextButton(onClick = onRemove, enabled = enabled) { Text(stringResource(R.string.guide_remove_action)) }
            if (entry.isPublished && !entry.guide.isArchived) TextButton(onClick = onCopy, enabled = enabled) {
                Text(stringResource(R.string.guide_copy_action))
            }
        }
    }
}

@Composable
internal fun CopyGuideDialog(sourceSize: String, choices: List<String>, existing: List<String>, busy: Boolean,
    failed: Boolean, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    var size by rememberSaveable { mutableStateOf("") }
    var reviewed by rememberSaveable { mutableStateOf(false) }
    val errorRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(failed) {
        if (failed) { withFrameNanos { }; errorRequester.bringIntoView() }
    }
    val available = size.isNotBlank() && com.macareen.stitchbook2.domain.parsing.PatternSizes.indexOf(size, existing) == null
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.guide_copy_action)) },
        text = {
            val keyboard = LocalSoftwareKeyboardController.current
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.guide_copy_help, sourceSize), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.filter { com.macareen.stitchbook2.domain.parsing.PatternSizes.indexOf(it, existing) == null }.forEach { label ->
                    FilterChip(selected = size == label, onClick = { size = label }, enabled = !busy, label = { Text(label) })
                }
            }
            OutlinedTextField(size, { size = it }, enabled = !busy, label = { Text(stringResource(R.string.guide_copy_size)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
            if (size.isNotBlank() && !available) Text(stringResource(R.string.guide_copy_exists), color = MaterialTheme.colorScheme.error)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(reviewed, { reviewed = it }, enabled = !busy)
                Text(stringResource(R.string.guide_copy_review), style = MaterialTheme.typography.bodySmall)
            }
            if (failed) Text(stringResource(R.string.guide_copy_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.bringIntoViewRequester(errorRequester))
        } }, confirmButton = { Column {
            TextButton(onClick = { onCopy(size.trim()) }, enabled = available && reviewed && !busy) { Text(stringResource(R.string.guide_copy_create)) }
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        } })
}

@Composable
private fun NewGuideDialog(
    patternTitle: String,
    sizeChoices: List<String>,
    canFillFromPattern: Boolean,
    onCreate: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    initialSize: String = ""
) {
    var size by rememberSaveable { mutableStateOf(initialSize) }
    var fromPattern by rememberSaveable { mutableStateOf(canFillFromPattern) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pattern_guides_new)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                if (sizeChoices.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                        sizeChoices.forEach { choice ->
                            FilterChip(
                                selected = size.trim().equals(choice, ignoreCase = true),
                                onClick = { size = choice },
                                label = { Text(choice) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text(stringResource(R.string.pattern_guides_size_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (size.isNotBlank()) {
                    QuietText(text = PatternNames.guide(patternTitle, size))
                }
                if (canFillFromPattern) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = fromPattern, onCheckedChange = { fromPattern = it })
                        Column {
                            Text(stringResource(R.string.pattern_guides_fill_from_pdf), style = MaterialTheme.typography.bodyLarge)
                            QuietText(text = stringResource(R.string.pattern_guides_fill_from_pdf_hint))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(size, canFillFromPattern && fromPattern) }, enabled = size.isNotBlank()) {
                Text(stringResource(R.string.pattern_guides_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Preview(showBackground = true)
@Composable
private fun PatternGuidesPreview() {
    val pattern = LibraryItem(
        id = "p",
        title = "Seaside Cardigan",
        craft = Craft.KNITTING,
        author = "A. Designer",
        sourceUrl = null,
        tags = emptyList(),
        notes = null,
        bookmarked = false,
        createdAt = 0,
        updatedAt = 0,
        pdfUri = "content://p",
        sizes = "S, M, L"
    )
    val guide = Guide(GuideId("g"), null, "Seaside Cardigan · M", null, 0, 0, libraryItemId = "p", sizeLabel = "M")
    StitchbookTheme {
        PatternGuidesScreen(
            uiState = PatternGuidesUiState.Content(pattern, listOf(PatternGuideEntry(guide, true)), false, false),
            onOpenPdf = {},
            onEditGuide = {},
            onCreateGuide = { _, _ -> }
        )
    }
}

/** Rounded cozy header with a shallow wave; its height grows with the title. */
private val PatternHeaderShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height - 18f)
    cubicTo(size.width * .7f, size.height + 12f, size.width * .3f, size.height - 42f, 0f, size.height - 12f)
    close()
}

@Composable
private fun PatternPageHeader(pattern: LibraryItem, completedProjects: Int) {
    Surface(shape = PatternHeaderShape, color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            Text(pattern.title, style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            pattern.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer) }
            if (completedProjects > 0) Text(
                pluralStringResource(R.plurals.pattern_page_completed_projects, completedProjects, completedProjects),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (pattern.craft != Craft.OTHER) Text(stringResource(pattern.craft.labelResource()),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun PatternOverview(pattern: LibraryItem) {
    var expanded by rememberSaveable(pattern.id) { mutableStateOf(false) }
    val notes = pattern.notes?.takeIf(String::isNotBlank)
    var notesOverflow by rememberSaveable(pattern.id, notes) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.padding(StitchbookSpacing.medium)) {
            val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.4f
            val cover: @Composable () -> Unit = {
                PatternCover(pattern.pdfUri, CozyPastels.forKey(pattern.id),
                    Modifier.size(if (stacked) 126.dp else 96.dp, if (stacked) 166.dp else 128.dp)
                        .clip(RoundedCornerShape(16.dp)))
            }
            val details: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                    Text(stringResource(R.string.pattern_page_notes), style = MaterialTheme.typography.titleMedium)
                    if (notes != null) {
                        Text(notes, style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (!expanded) notesOverflow = it.hasVisualOverflow })
                        if (notesOverflow || expanded) TextButton(onClick = { expanded = !expanded }) {
                            Text(stringResource(if (expanded) R.string.pattern_page_read_less else R.string.pattern_page_read_more))
                        }
                    } else QuietText(stringResource(R.string.pattern_page_no_notes))
                }
            }
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { cover() }
                details()
            } else Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium)) {
                cover()
                Box(Modifier.weight(1f)) { details() }
            }
        }
    }
}

@Composable
private fun SizeGuideRow(size: String, enabled: Boolean, onBuild: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()) {
        FlowRow(Modifier.padding(StitchbookSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pattern_guides_size, size), style = MaterialTheme.typography.titleMedium)
                QuietText(stringResource(R.string.pattern_page_no_guide))
            }
            TextButton(onClick = onBuild, enabled = enabled, modifier = Modifier.testTag("build-guide-$size")) { Text(stringResource(R.string.pattern_page_build_guide)) }
        }
    }
}

/** Keep the pattern's actual wording in separate, readable detail cards. */
@Composable
private fun PatternDetailsCard(pattern: LibraryItem) {
    val details = listOfNotNull(
        pattern.gauge?.let { stringResource(R.string.pattern_page_gauge) to it },
        pattern.recommendedTools?.let { stringResource(R.string.pattern_page_tools) to it },
        pattern.yardageRequired?.let { stringResource(R.string.pattern_page_yarn) to stringResource(R.string.pattern_details_yardage, it.toInt()) }
    )
    details.forEach { (label, value) ->
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(StitchbookSpacing.medium), verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
