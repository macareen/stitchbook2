package com.macareen.stitchbook2.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.domain.model.hubNodePositions
import com.macareen.stitchbook2.ui.theme.textSecondary

/** One node around the hub: what it is, how many things it holds, and what tapping it does. */
data class HubNode(
    val label: String,
    val icon: ImageVector,
    /** Shown under the label; null shows nothing (for nodes without a meaningful count). */
    val detail: String?,
    /** Spoken by TalkBack, e.g. "Yarn, 4 linked". */
    val accessibilityLabel: String,
    val isEmpty: Boolean,
    val onClick: () -> Unit
)

private val NodeWidth: Dp = 92.dp
private val NodeHeight: Dp = 68.dp

/**
 * The project as a small mind map: [centreLabel] in the middle and each
 * [nodes] entry on a ring around it, joined by hairlines. Empty nodes stay
 * visible but quieter, so the map always shows what *could* connect.
 * Positions come from the pure [hubNodePositions] so every screen size
 * gets the same arrangement.
 */
@Composable
fun ProjectNodeMap(
    centreLabel: String,
    nodes: List<HubNode>,
    modifier: Modifier = Modifier,
    height: Dp = 320.dp
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val positions = hubNodePositions(nodes.size, radiusX = 0.36f, radiusY = 0.38f)

        // Decorative only: the nodes carry all meaning and semantics.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            positions.forEach { position ->
                drawLine(
                    color = lineColor,
                    start = centre,
                    end = Offset(position.x * size.width, position.y * size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 150.dp)
        ) {
            Text(
                text = centreLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        nodes.forEachIndexed { index, node ->
            val position = positions[index]
            val x = maxWidth * position.x - NodeWidth / 2
            val y = maxHeight * position.y - NodeHeight / 2
            NodeChip(node = node, modifier = Modifier.offset(x = x, y = y))
        }
    }
}

@Composable
private fun NodeChip(node: HubNode, modifier: Modifier = Modifier) {
    val contentColor = if (node.isEmpty) MaterialTheme.colorScheme.textSecondary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = node.onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .size(width = NodeWidth, height = NodeHeight)
            .semantics(mergeDescendants = true) { contentDescription = node.accessibilityLabel }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = node.icon,
                contentDescription = null,
                tint = if (node.isEmpty) contentColor else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = node.label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            node.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
