package com.macareen.stitchbook2.feature.library

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
import com.macareen.stitchbook2.domain.model.LibraryItem
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
        onGetRavelryPdf = viewModel::getRavelryPdf
    )
}

/** A pattern, its original file, and its guides by size. */
@Composable
fun PatternGuidesScreen(
    uiState: PatternGuidesUiState,
    onOpenPdf: (String) -> Unit,
    onEditGuide: (String) -> Unit,
    onCreateGuide: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onStartProject: () -> Unit = {},
    onGetRavelryPdf: () -> Unit = {}
) {
    when (uiState) {
        PatternGuidesUiState.Loading -> Box(modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        PatternGuidesUiState.Missing -> Box(modifier.fillMaxSize().padding(StitchbookSpacing.large)) {
            QuietText(text = stringResource(R.string.pattern_guides_missing))
        }

        is PatternGuidesUiState.Content ->
            Content(uiState, onOpenPdf, onEditGuide, onCreateGuide, onStartProject, onGetRavelryPdf, modifier)
    }
}

@Composable
private fun Content(
    state: PatternGuidesUiState.Content,
    onOpenPdf: (String) -> Unit,
    onEditGuide: (String) -> Unit,
    onCreateGuide: (String, String, Boolean) -> Unit,
    onStartProject: () -> Unit,
    onGetRavelryPdf: () -> Unit,
    modifier: Modifier
) {
    var showNewGuide by rememberSaveable { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StitchbookSpacing.large, vertical = StitchbookSpacing.medium)
    ) {
        Text(text = state.pattern.title, style = MaterialTheme.typography.headlineMedium)
        listOfNotNull(state.pattern.author, state.pattern.sizes?.let { stringResource(R.string.pattern_guides_sizes, it) })
            .takeIf { it.isNotEmpty() }
            ?.let { QuietText(text = it.joinToString(" · ")) }
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
        PatternDetailsCard(state.pattern)

        Text(text = stringResource(R.string.pattern_guides_title), style = MaterialTheme.typography.titleMedium)
        if (state.guides.isEmpty()) {
            QuietText(text = stringResource(R.string.pattern_guides_empty))
        }
        state.guides.forEach { entry -> GuideRow(entry, onClick = { onEditGuide(entry.guide.id.value) }) }
        PrimaryActionButton(
            text = stringResource(R.string.pattern_guides_new),
            onClick = { showNewGuide = true },
            enabled = !state.isCreating,
            modifier = Modifier.fillMaxWidth()
        )
        val problem = when {
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

    if (showNewGuide) {
        NewGuideDialog(
            patternTitle = state.pattern.title,
            sizeChoices = state.sizeChoices,
            canFillFromPattern = state.pattern.pdfUri != null,
            onCreate = { size, name, fromPattern ->
                showNewGuide = false
                onCreateGuide(size, name, fromPattern)
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
        is DownloadRavelryPdf.Result.Failed -> Text(
            text = stringResource(ravelryProblemText(result.problem)),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
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
}

@Composable
private fun GuideRow(entry: PatternGuideEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
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
private fun NewGuideDialog(
    patternTitle: String,
    sizeChoices: List<String>,
    canFillFromPattern: Boolean,
    onCreate: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var size by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var fromPattern by rememberSaveable { mutableStateOf(canFillFromPattern) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pattern_guides_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.pattern_guides_name_field)) },
                    placeholder = { Text("$patternTitle · ${size.ifBlank { "M" }}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
            TextButton(onClick = { onCreate(size, name, canFillFromPattern && fromPattern) }, enabled = size.isNotBlank()) {
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
            onCreateGuide = { _, _, _ -> }
        )
    }
}

/** What the pattern says about itself: its description, gauge, tools and yardage, when known. */
@Composable
private fun PatternDetailsCard(pattern: LibraryItem) {
    val lines = listOfNotNull(
        pattern.gauge?.let { stringResource(R.string.pattern_details_gauge, it) },
        pattern.recommendedTools?.let { stringResource(R.string.pattern_details_tools, it) },
        pattern.yardageRequired?.let { stringResource(R.string.pattern_details_yardage, it.toInt()) }
    )
    if (pattern.notes == null && lines.isEmpty()) return
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(StitchbookSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
        ) {
            pattern.notes?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            lines.forEach { line -> QuietText(text = line) }
        }
    }
}
