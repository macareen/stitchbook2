package com.macareen.stitchbook2.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing

@Composable
internal fun PatternPdfChangeDialog(review: PatternPdfReview, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val action = when {
        review.document == null -> R.string.pattern_pdf_unlink
        review.expected.pdfUri != null -> R.string.pattern_pdf_replace
        else -> R.string.pattern_pdf_link_review
    }
    val scroll = rememberScrollState()
    LaunchedEffect(review.problem, review.expected) { if (review.problem != null) scroll.scrollTo(0) }
    AlertDialog(
        onDismissRequest = { if (!review.isSaving) onDismiss() },
        title = { Text(stringResource(action)) },
        text = {
            Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium)) {
                review.problem?.let {
                    Text(stringResource(when (it) {
                        PdfChangeProblem.FAILED -> R.string.pattern_pdf_review_failed
                        PdfChangeProblem.CHANGED -> R.string.pattern_pdf_review_changed
                        PdfChangeProblem.MISSING -> R.string.pattern_pdf_review_missing
                    }), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Text(review.expected.title, style = MaterialTheme.typography.titleMedium)
                if (review.expected.pdfUri != null) Text(stringResource(R.string.pattern_pdf_current,
                    review.expected.pdfFileName ?: stringResource(R.string.pattern_pdf_unnamed)))
                review.document?.let { Text(stringResource(R.string.pattern_pdf_selected,
                    it.displayName ?: stringResource(R.string.pattern_pdf_unnamed))) }
                if (review.document == null) Text(stringResource(R.string.pattern_pdf_unlink_explanation))
                Text(stringResource(R.string.pattern_pdf_preserved))
                Text(stringResource(R.string.pattern_pdf_folder_refresh))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !review.isSaving && review.problem != PdfChangeProblem.MISSING) {
                Text(stringResource(if (review.isSaving) R.string.pattern_pdf_saving else action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !review.isSaving) { Text(stringResource(R.string.cancel)) } }
    )
}
