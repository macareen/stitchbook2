package com.macareen.stitchbook2.feature.journal

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.JournalEntry
import com.macareen.stitchbook2.domain.model.JournalNoteTag
import com.macareen.stitchbook2.ui.components.ChoiceChipRow
import com.macareen.stitchbook2.ui.components.LabelPill
import com.macareen.stitchbook2.ui.components.QuietText

fun JournalNoteTag.labelResource(): Int = when (this) {
    JournalNoteTag.GENERAL -> R.string.row_note_general
    JournalNoteTag.ADJUSTMENT -> R.string.row_note_adjustment
    JournalNoteTag.MISTAKE -> R.string.row_note_mistake
    JournalNoteTag.REMINDER -> R.string.row_note_reminder
    JournalNoteTag.TECHNIQUE -> R.string.row_note_technique
}

@Composable
fun NoteTagChoices(selected: JournalNoteTag, onSelected: (JournalNoteTag) -> Unit, enabled: Boolean = true) {
    ChoiceChipRow(JournalNoteTag.entries, selected, { stringResource(it.labelResource()) }, onSelected, enabled = enabled)
}

@Composable
fun WorkingNoteMetadata(note: JournalEntry) {
    Column {
        if (note.tag != JournalNoteTag.GENERAL) LabelPill(stringResource(note.tag.labelResource()), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        note.rowContext?.takeIf { it.isNotBlank() }?.let { QuietText(it) }
    }
}
