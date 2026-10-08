package com.macareen.stitchbook2.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.NeedleColumn
import com.macareen.stitchbook2.domain.model.NeedleGrid
import com.macareen.stitchbook2.domain.model.NeedleGridRow

/** A needle-case chart: sizes down the side, kinds across, a dot for each you own. */
@Composable
fun NeedleGridCard(grid: NeedleGrid, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.needle_grid_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.needle_grid_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                Text(
                    text = stringResource(R.string.needle_grid_size_heading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(SizeColumnWidth)
                )
                grid.columns.forEach { column ->
                    Text(
                        text = stringResource(column.labelResource()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            grid.rows.forEachIndexed { index, row ->
                GridRow(
                    row = row,
                    columns = grid.columns,
                    shaded = index % 2 == 1
                )
            }
        }
    }
}

@Composable
private fun GridRow(row: NeedleGridRow, columns: List<NeedleColumn>, shaded: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (shaded) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(SizeColumnWidth).padding(start = 4.dp)) {
            Text(text = formatMm(row.sizeMm), style = MaterialTheme.typography.labelLarge)
            row.usLabel?.let { us ->
                Text(
                    text = stringResource(R.string.needle_grid_us, us),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        columns.forEach { column ->
            val count = row.counts[column] ?: 0
            val description = stringResource(column.labelResource()) + " " + formatMm(row.sizeMm) + ": " +
                if (count > 0) stringResource(R.string.needle_grid_owned, count) else stringResource(R.string.needle_grid_missing)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = description }
            ) {
                if (count > 0) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(if (count > 1) 22.dp else 14.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.inversePrimary)
                    ) {
                        if (count > 1) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                }
            }
        }
    }
}

private val SizeColumnWidth = 84.dp

private fun formatMm(sizeMm: Double): String =
    if (sizeMm % 1.0 == 0.0) sizeMm.toInt().toString() else sizeMm.toString().trimEnd('0')

private fun NeedleColumn.labelResource(): Int = when (this) {
    NeedleColumn.STRAIGHT -> R.string.needle_grid_straight
    NeedleColumn.CIRCULAR -> R.string.needle_grid_circular
    NeedleColumn.DPN -> R.string.needle_grid_dpn
    NeedleColumn.INTERCHANGEABLE -> R.string.needle_grid_interchangeable
    NeedleColumn.HOOK -> R.string.needle_grid_hook
}
