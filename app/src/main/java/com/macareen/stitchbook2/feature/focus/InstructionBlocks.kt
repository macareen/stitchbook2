package com.macareen.stitchbook2.feature.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing

/** Inline stitch groups are visually bounded; completing the containing guide step still happens once. */
@Composable
internal fun InstructionBlocks(text: String, style: TextStyle, modifier: Modifier = Modifier, glossary: List<com.macareen.stitchbook2.domain.guide.GlossaryEntry> = emptyList()) {
    BlockList(FocusInstructionLayout.blocks(text), style, modifier, glossary)
}

@Composable
private fun BlockList(blocks: List<FocusInstructionLayout.Block>, style: TextStyle, modifier: Modifier = Modifier, glossary: List<com.macareen.stitchbook2.domain.guide.GlossaryEntry>) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
        blocks.forEach { block ->
            if (block.children.isEmpty()) {
                GlossaryText(block.text, style, glossary)
            } else {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, if (block.text.endsWith("*")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Column(Modifier.padding(StitchbookSpacing.small), verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                        GlossaryText(block.text, style, glossary)
                        BlockList(block.children, style, Modifier.padding(start = StitchbookSpacing.small), glossary)
                        block.suffix?.let { GlossaryText(it, style, glossary) }
                    }
                }
            }
        }
    }
}
