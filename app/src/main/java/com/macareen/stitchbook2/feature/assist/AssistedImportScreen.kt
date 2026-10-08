package com.macareen.stitchbook2.feature.assist

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.parsing.StructuredGuide
import com.macareen.stitchbook2.domain.parsing.StructuredGuideProblem
import com.macareen.stitchbook2.domain.parsing.StructuredStep
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SecondaryActionButton
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AssistedImportRoute(viewModel: AssistedImportViewModel, onDraftCreated: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val createdGuideId by viewModel.createdGuideId.collectAsStateWithLifecycle()
    LaunchedEffect(createdGuideId) { createdGuideId?.let(onDraftCreated) }

    AssistedImportScreen(
        uiState = uiState,
        onGuideNameChange = viewModel::updateGuideName,
        onPatternTextChange = viewModel::updatePatternText,
        onPdfRead = viewModel::readPdf,
        onReplyChange = viewModel::updateReply,
        onCheckReply = viewModel::checkReply,
        onCreateDraft = viewModel::createDraft
    )
}

/**
 * Three calm steps: bring the pattern, ask an assistant, paste the reply.
 * Nothing leaves the device unless the person copies or shares it.
 */
@Composable
fun AssistedImportScreen(
    uiState: AssistedImportUiState,
    onGuideNameChange: (String) -> Unit,
    onPatternTextChange: (String) -> Unit,
    onPdfRead: (ByteArray) -> Unit,
    onReplyChange: (String) -> Unit,
    onCheckReply: () -> Unit,
    onCreateDraft: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
                if (bytes != null) onPdfRead(bytes)
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StitchbookSpacing.large, vertical = StitchbookSpacing.medium)
    ) {
        Text(text = stringResource(R.string.assist_title), style = MaterialTheme.typography.headlineSmall)
        QuietText(text = stringResource(R.string.assist_privacy_note))

        StepTitle(number = 1, text = stringResource(R.string.assist_step_pattern))
        OutlinedTextField(
            value = uiState.guideName,
            onValueChange = onGuideNameChange,
            label = { Text(stringResource(R.string.assist_guide_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SecondaryActionButton(
            text = stringResource(if (uiState.isReadingPdf) R.string.assist_reading_pdf else R.string.assist_choose_pdf),
            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
            enabled = !uiState.isReadingPdf
        )
        uiState.patternTextError?.let { error ->
            ErrorText(
                stringResource(
                    if (error == PatternTextError.NO_TEXT) R.string.create_guide_from_pdf_no_text_error else R.string.create_guide_from_pdf_extraction_error
                )
            )
        }
        OutlinedTextField(
            value = uiState.patternText,
            onValueChange = onPatternTextChange,
            label = { Text(stringResource(R.string.assist_pattern_text)) },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )

        StepTitle(number = 2, text = stringResource(R.string.assist_step_ask))
        QuietText(text = stringResource(R.string.assist_ask_hint))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            TextButton(onClick = { copyToClipboard(context, uiState.prompt) }, enabled = uiState.hasPatternText) {
                Text(stringResource(R.string.assist_copy_request))
            }
            TextButton(onClick = { shareText(context, uiState.prompt) }, enabled = uiState.hasPatternText) {
                Text(stringResource(R.string.assist_share_request))
            }
            TextButton(onClick = { openClaude(context) }) {
                Text(stringResource(R.string.assist_open_claude))
            }
        }

        StepTitle(number = 3, text = stringResource(R.string.assist_step_reply))
        OutlinedTextField(
            value = uiState.reply,
            onValueChange = onReplyChange,
            label = { Text(stringResource(R.string.assist_reply)) },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            TextButton(onClick = { readClipboard(context)?.let(onReplyChange) }) {
                Text(stringResource(R.string.assist_paste_reply))
            }
            TextButton(onClick = onCheckReply, enabled = uiState.reply.isNotBlank()) {
                Text(stringResource(R.string.assist_check_reply))
            }
        }

        when (val check = uiState.check) {
            is ReplyCheck.Ready -> ReadySummary(
                guide = check.guide,
                isCreating = uiState.isCreating,
                createFailed = uiState.createFailed,
                onCreateDraft = onCreateDraft
            )

            is ReplyCheck.Problems -> ProblemList(check.problems)
            null -> Unit
        }
    }
}

@Composable
private fun StepTitle(number: Int, text: String) {
    Text(
        text = stringResource(R.string.assist_step_format, number, text),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = StitchbookSpacing.small)
    )
}

@Composable
private fun ReadySummary(guide: StructuredGuide, isCreating: Boolean, createFailed: Boolean, onCreateDraft: () -> Unit) {
    val sections = guide.steps.count { it is StructuredStep.Section }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small),
            modifier = Modifier.padding(StitchbookSpacing.medium)
        ) {
            Text(text = guide.name ?: stringResource(R.string.assist_ready_title), style = MaterialTheme.typography.titleMedium)
            QuietText(
                text = listOfNotNull(
                    pluralStringResource(R.plurals.assist_step_count, guide.stepCount, guide.stepCount),
                    sections.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.assist_section_count, it, it) },
                    guide.review.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.assist_review_count, it, it) }
                ).joinToString(" · ")
            )
            guide.review.forEach { item -> Text(text = "• $item", style = MaterialTheme.typography.bodyMedium) }
            QuietText(text = stringResource(R.string.assist_ready_hint))
            PrimaryActionButton(
                text = stringResource(R.string.assist_create_draft),
                onClick = onCreateDraft,
                enabled = !isCreating,
                modifier = Modifier.fillMaxWidth()
            )
            if (createFailed) ErrorText(stringResource(R.string.assist_create_failed))
        }
    }
}

@Composable
private fun ProblemList(problems: List<StructuredGuideProblem>) {
    Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall)) {
        ErrorText(stringResource(R.string.assist_problems_title))
        problems.forEach { problem ->
            Text(
                text = if (problem.path.isEmpty()) problem.message else "${problem.path}: ${problem.message}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        QuietText(text = stringResource(R.string.assist_problems_hint))
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

private fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.assist_clip_label), text))
}

private fun readClipboard(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)
        ?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.assist_share_request)))
}

private fun openClaude(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, CLAUDE_URL.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser or app can open links; the copy and share actions still work.
    }
}

private const val CLAUDE_URL = "https://claude.ai/new"

@Preview(showBackground = true)
@Composable
private fun AssistedImportScreenPreview() {
    StitchbookTheme {
        AssistedImportScreen(
            uiState = AssistedImportUiState(guideName = "Granny square", patternText = "Round 1: ch 4, join."),
            onGuideNameChange = {},
            onPatternTextChange = {},
            onPdfRead = {},
            onReplyChange = {},
            onCheckReply = {},
            onCreateDraft = {}
        )
    }
}
