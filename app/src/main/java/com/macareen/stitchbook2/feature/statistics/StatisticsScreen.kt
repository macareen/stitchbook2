package com.macareen.stitchbook2.feature.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R

import com.macareen.stitchbook2.domain.statistics.StatisticProvenance
import com.macareen.stitchbook2.domain.statistics.StatisticsRange
import com.macareen.stitchbook2.domain.statistics.WeekTotal
import com.macareen.stitchbook2.feature.materials.formatAmount
import com.macareen.stitchbook2.feature.projects.labelResource
import com.macareen.stitchbook2.ui.components.ChoiceChipRow
import com.macareen.stitchbook2.ui.components.ContentCard
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SectionHeader
import com.macareen.stitchbook2.ui.components.formatDuration
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.textSecondary
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun StatisticsRoute(viewModel: StatisticsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatisticsScreen(uiState = uiState, onRangeSelected = viewModel::selectRange)
}

@Composable
fun StatisticsScreen(
    uiState: StatisticsUiState,
    onRangeSelected: (StatisticsRange) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        StatisticsUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        StatisticsUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.statistics_error))
        }
        is StatisticsUiState.Content -> StatisticsContent(uiState, onRangeSelected, modifier)
    }
}

@Composable
private fun StatisticsContent(
    state: StatisticsUiState.Content,
    onRangeSelected: (StatisticsRange) -> Unit,
    modifier: Modifier
) {
    val stats = state.statistics
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(StitchbookSpacing.large),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium)
    ) {
        item {
            Text(text = stringResource(R.string.statistics_title), style = MaterialTheme.typography.headlineMedium)
            QuietText(
                text = stats.window.start?.let {
                    stringResource(R.string.statistics_window, dateFormat.format(it), dateFormat.format(stats.window.end))
                } ?: stringResource(R.string.statistics_window_all, dateFormat.format(stats.window.end))
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            ChoiceChipRow(
                options = StatisticsRange.entries,
                selected = state.range,
                optionLabel = { stringResource(it.labelResource()) },
                onSelected = onRangeSelected
            )
        }

        item {
            SectionHeader(title = stringResource(R.string.statistics_time_section), icon = Icons.Outlined.Timer)
            StatLine(R.string.statistics_total_time, formatDuration(stats.totalWorkedMillis), StatisticProvenance.DERIVED)
            StatLine(R.string.statistics_sessions, stats.sessionCount.toString(), StatisticProvenance.RECORDED)
            StatLine(
                R.string.statistics_average_session,
                stats.averageSessionMillis?.let { formatDuration(it) } ?: dash(),
                StatisticProvenance.DERIVED
            )
            StatLine(
                R.string.statistics_current_streak,
                stringResource(R.string.statistics_days, stats.currentStreakDays),
                StatisticProvenance.DERIVED
            )
            StatLine(
                R.string.statistics_longest_streak,
                stringResource(R.string.statistics_days, stats.longestStreakDays),
                StatisticProvenance.DERIVED
            )
            QuietText(text = stringResource(R.string.statistics_streak_note))
        }

        item {
            ContentCard {
                Text(text = stringResource(R.string.statistics_weekly_trend), style = MaterialTheme.typography.titleMedium)
                QuietText(text = stringResource(R.string.statistics_weekly_trend_note))
                Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
                WeeklyBars(weeks = stats.weeklyTrend)
            }
        }

        if (stats.workedMillisByProject.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.statistics_by_project), icon = Icons.Outlined.ViewList)
                stats.workedMillisByProject.entries.sortedByDescending { it.value }.forEach { (projectId, millis) ->
                    PlainLine(
                        label = projectId?.let { state.projectNames[it] } ?: stringResource(R.string.sessions_unassigned),
                        value = formatDuration(millis)
                    )
                }
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                stats.workedMillisByCraft.entries.sortedByDescending { it.value }.forEach { (craft, millis) ->
                    PlainLine(
                        label = craft?.let { stringResource(it.labelResource()) } ?: stringResource(R.string.statistics_no_craft),
                        value = formatDuration(millis)
                    )
                }
                if (stats.workedMillisByMonth.size > 1) {
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    stats.workedMillisByMonth.forEach { (month, millis) ->
                        PlainLine(label = month.toString(), value = formatDuration(millis))
                    }
                }
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.statistics_progress_section), icon = Icons.Outlined.Insights)
            StatLine(R.string.statistics_rows, stats.rowsCompleted.toString(), StatisticProvenance.RECORDED)
            StatLine(
                R.string.statistics_time_per_row,
                stats.averageMillisPerRow?.let { formatPerRow(it) } ?: dash(),
                StatisticProvenance.DERIVED
            )
            StatLine(
                R.string.statistics_estimated_stitches,
                stats.estimatedStitches?.toString() ?: dash(),
                StatisticProvenance.ESTIMATED
            )
            StatLine(R.string.statistics_projects_started, stats.projectsStarted.toString(), StatisticProvenance.RECORDED)
            StatLine(R.string.statistics_projects_completed, stats.projectsCompleted.toString(), StatisticProvenance.RECORDED)
            StatLine(
                R.string.statistics_project_duration,
                stats.averageProjectDurationDays?.let { stringResource(R.string.statistics_days_decimal, formatAmount(it)) } ?: dash(),
                StatisticProvenance.DERIVED
            )
        }

        item {
            SectionHeader(title = stringResource(R.string.statistics_yarn_section), icon = Icons.Outlined.Inventory2)
            if (stats.yarnUsedByUnit.isEmpty()) {
                QuietText(text = stringResource(R.string.statistics_yarn_empty))
            }
            stats.yarnUsedByUnit.forEach { (unit, amount) ->
                StatLine(
                    label = stringResource(R.string.statistics_yarn_used, unit),
                    value = formatAmount(amount),
                    provenance = StatisticProvenance.RECORDED
                )
            }
            stats.estimatedYardsUsed?.let {
                StatLine(R.string.statistics_estimated_yards, formatAmount(it), StatisticProvenance.ESTIMATED)
            }
            QuietText(text = stringResource(R.string.statistics_yarn_note))
        }

        item { Methodology() }
    }
}

@Composable
private fun StatLine(labelRes: Int, value: String, provenance: StatisticProvenance) =
    StatLine(stringResource(labelRes), value, provenance)

@Composable
private fun StatLine(label: String, value: String, provenance: StatisticProvenance) {
    val provenanceText = stringResource(provenance.labelResource())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = StitchbookSpacing.extraSmall)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.padding(start = StitchbookSpacing.small))
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = when (provenance) {
                StatisticProvenance.RECORDED -> MaterialTheme.colorScheme.tertiaryContainer
                StatisticProvenance.DERIVED -> MaterialTheme.colorScheme.surfaceContainerHigh
                StatisticProvenance.ESTIMATED -> MaterialTheme.colorScheme.secondaryContainer
            }
        ) {
            Text(
                text = provenanceText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun PlainLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.textSecondary)
    }
}

/**
 * Single-series bars: one hue, thin marks with rounded data-ends anchored
 * to the baseline, a recessive baseline, and only the peak labelled. The
 * whole chart reads as one TalkBack description listing every week, which
 * doubles as its table view.
 */
@Composable
private fun WeeklyBars(weeks: List<WeekTotal>) {
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val max = weeks.maxOfOrNull { it.workedMillis }?.takeIf { it > 0 } ?: 1L
    val format = DateTimeFormatter.ofPattern("d MMM")
    val minutesLabel = stringResource(R.string.statistics_minutes_short)
    val description = weeks.joinToString("; ") { "${format.format(it.weekStart)}: ${it.workedMillis / 60_000} $minutesLabel" }
    val peak = weeks.maxByOrNull { it.workedMillis }?.takeIf { it.workedMillis > 0 }

    Column(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        peak?.let {
            QuietText(text = stringResource(R.string.statistics_peak_week, format.format(it.weekStart), formatDuration(it.workedMillis)))
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val slot = size.width / weeks.size.coerceAtLeast(1)
            val barWidth = (slot * 0.5f).coerceAtMost(28.dp.toPx())
            val radius = 4.dp.toPx()
            weeks.forEachIndexed { index, week ->
                val barHeight = size.height * week.workedMillis / max
                if (barHeight > 0f) {
                    val left = index * slot + (slot - barWidth) / 2f
                    // Round only the data-end: draw a rounded rect extending
                    // below the baseline and clip to it.
                    clipRect(bottom = size.height) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight + radius),
                            cornerRadius = CornerRadius(radius, radius)
                        )
                    }
                }
            }
            drawLine(baselineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Spacer(modifier = Modifier.height(StitchbookSpacing.extraSmall))
        Row(modifier = Modifier.fillMaxWidth()) {
            weeks.forEachIndexed { index, week ->
                Text(
                    text = if (index == 0 || index == weeks.lastIndex) format.format(week.weekStart) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Methodology() {
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.statistics_how_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = StitchbookSpacing.small)
            )
        }
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(StitchbookSpacing.small))
        listOf(
            R.string.statistics_how_recorded,
            R.string.statistics_how_derived,
            R.string.statistics_how_estimated,
            R.string.statistics_how_days
        ).forEach { Text(text = stringResource(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp)) }
    }
}

@Composable
private fun formatPerRow(millis: Long): String {
    val seconds = millis / 1000
    return if (seconds < 120) {
        stringResource(R.string.statistics_seconds_per_row, seconds)
    } else {
        stringResource(R.string.statistics_per_row, formatDuration(millis))
    }
}

@Composable
private fun dash(): String = stringResource(R.string.statistics_none)

private fun StatisticsRange.labelResource(): Int = when (this) {
    StatisticsRange.LAST_7_DAYS -> R.string.statistics_range_7
    StatisticsRange.LAST_30_DAYS -> R.string.statistics_range_30
    StatisticsRange.THIS_YEAR -> R.string.statistics_range_year
    StatisticsRange.ALL_TIME -> R.string.statistics_range_all
}

private fun StatisticProvenance.labelResource(): Int = when (this) {
    StatisticProvenance.RECORDED -> R.string.statistics_recorded
    StatisticProvenance.DERIVED -> R.string.statistics_derived
    StatisticProvenance.ESTIMATED -> R.string.statistics_estimated
}

