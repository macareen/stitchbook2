package com.macareen.stitchbook2.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.macareen.stitchbook2.ui.theme.metadata
import com.macareen.stitchbook2.ui.theme.textSecondary

/**
 * Quiet, secondary-weight text: guide/section context, structural position,
 * and other supporting information that must stay visible without
 * competing with the dominant content on screen.
 */
@Composable
fun QuietText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.metadata
) {
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.textSecondary,
        modifier = modifier
    )
}
