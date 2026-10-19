package com.macareen.stitchbook2.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.Craft

@Composable
internal fun PatternEditDialog(
    review: PatternEditReview,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
    onSave: (String, Craft, String, String, List<String>, String, PatternMetadataInput) -> Unit
) {
    LibraryItemDialog(review.expected, onDismiss,
        onSave = { title, craft, author, url, tags, notes, _, _, metadata ->
            onSave(title, craft, author, url, tags, notes, metadata)
        }, showPdfControls = false, isSaving = review.isSaving,
        canSave = review.problem != PatternEditProblem.CHANGED && review.problem != PatternEditProblem.MISSING,
        errorMessage = review.problem?.let { stringResource(when (it) {
            PatternEditProblem.FAILED -> R.string.pattern_edit_failed
            PatternEditProblem.CHANGED -> R.string.pattern_edit_changed
            PatternEditProblem.MISSING -> R.string.pattern_edit_missing
        }) },
        onReload = onReload.takeIf { review.problem == PatternEditProblem.CHANGED }
    )
}
