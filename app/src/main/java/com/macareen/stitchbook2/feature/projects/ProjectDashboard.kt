package com.macareen.stitchbook2.feature.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.*
import com.macareen.stitchbook2.domain.execution.ProgressBasis
import com.macareen.stitchbook2.ui.components.*
import com.macareen.stitchbook2.ui.theme.textSecondary

data class ProjectYarn(val item: StashItem, val allocation: YarnAllocation)
data class ProjectDashboardState(
    val photos: List<Photo> = emptyList(),
    val notes: List<JournalEntry> = emptyList(),
    val patterns: List<LibraryItem> = emptyList(),
    val yarn: List<ProjectYarn> = emptyList(),
    val sessionCount: Int = 0,
    val workedMillis: Long = 0
)

private val ProjectHeaderShape = GenericShape { size, _ ->
    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - 18f)
    cubicTo(size.width * .7f, size.height + 12f, size.width * .3f, size.height - 42f, 0f, size.height - 12f)
    close()
}

@Composable
internal fun ProjectPageHero(project: Project, actions: @Composable () -> Unit) {
    Surface(shape = ProjectHeaderShape, color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(listOf(stringResource(project.craft.labelResource()), stringResource(project.status.labelResource())).joinToString(" \u00b7 "),
                style = MaterialTheme.typography.labelLarge)
            actions()
            project.startDate?.let { QuietText(stringResource(R.string.project_page_started, it)) }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ProjectPageSection(title: String, action: String? = null, onAction: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun ProjectDashboardBeforeGuides(state: ProjectDashboardState, entries: List<GuideListEntry>, onSection: (ProjectSection) -> Unit, coverPhotoId: String?, onSetCover: (Photo) -> Unit, onOpenPattern: (String) -> Unit, onOpenPdf: (String) -> Unit, coverSaveFailed: Boolean) {
    ProjectPageSection(stringResource(R.string.project_page_pattern), if (state.patterns.isEmpty()) stringResource(R.string.project_page_manage_patterns) else null, { onSection(ProjectSection.MATERIALS) }) {
        if (state.patterns.isEmpty()) QuietText(stringResource(R.string.project_page_no_pattern))
        state.patterns.forEach { pattern ->
            val metadata: @Composable () -> Unit = {
                Text(pattern.title, style = MaterialTheme.typography.titleMedium)
                pattern.author?.let { QuietText(it) }
            }
            val open: @Composable () -> Unit = {
                TextButton(onClick = { onOpenPattern(pattern.id) }) { Text(stringResource(R.string.project_page_view_pattern)) }
            }
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.4f) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { metadata(); open() }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { metadata() }; open()
            }
            if (pattern.pdfUri != null) TextButton(onClick = { onOpenPdf(pattern.id) }) { Text(stringResource(R.string.project_page_view_pdf)) }
        }
    }
    ProjectPageSection(stringResource(R.string.project_page_stats), stringResource(R.string.sessions_title), { onSection(ProjectSection.SESSIONS) }) {
        val entry = entries.firstOrNull { it.action == GuideEntryAction.CONTINUE } ?: entries.firstOrNull { it.progress != null }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val time: @Composable () -> Unit = {
                QuietText(stringResource(R.string.project_page_time_recorded))
                Text(formatDuration(state.workedMillis), style = MaterialTheme.typography.titleLarge)
                QuietText(stringResource(R.string.project_page_sessions, state.sessionCount))
            }
            val progress: @Composable () -> Unit = {
                QuietText(stringResource(R.string.project_page_guide_progress))
                entry?.progress?.let {
                    Text("${it.percent}%", style = MaterialTheme.typography.titleLarge)
                    if (it.basis == ProgressBasis.STITCHES_ESTIMATED) QuietText(stringResource(R.string.project_page_estimated))
                    entry.sections.firstOrNull { section -> section.status == com.macareen.stitchbook2.domain.execution.OverviewStatus.CURRENT }
                        ?.let { section -> QuietText(section.text) }
                } ?: QuietText(stringResource(R.string.project_page_no_progress))
            }
            if (maxWidth < 280.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.4f) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { time(); HorizontalDivider(); progress() }
            } else Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { time() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { progress() }
            }
        }
    }
    ProjectPageSection(stringResource(R.string.project_page_photos), stringResource(R.string.project_page_manage_photos), { onSection(ProjectSection.JOURNAL) }) {
        if (coverSaveFailed) Text(stringResource(R.string.project_page_cover_error), color = MaterialTheme.colorScheme.error)
        if (state.photos.isEmpty()) QuietText(stringResource(R.string.project_page_no_photos))
        else {
            var selectedId by rememberSaveable(state.photos.first().projectId) { mutableStateOf(coverPhotoId ?: state.photos.first().id) }
            val index = state.photos.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
            val photo = state.photos[index]
            PhotoThumbnail(photo.uri, photo.caption ?: photo.displayName, Modifier.fillMaxWidth().height(240.dp), maxSizePx = 1024)
            TextButton(onClick = { onSetCover(photo) }, enabled = photo.id != coverPhotoId) {
                Text(stringResource(if (photo.id == coverPhotoId) R.string.project_page_cover else R.string.project_page_set_cover))
            }
            photo.caption?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            QuietText(stringResource(R.string.project_page_photo_position, index + 1, state.photos.size))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { selectedId = state.photos[index - 1].id }, enabled = index > 0) { Text(stringResource(R.string.project_page_previous)) }
                TextButton(onClick = { selectedId = state.photos[index + 1].id }, enabled = index < state.photos.lastIndex) { Text(stringResource(R.string.project_page_next)) }
            }
        }
    }
    ProjectPageSection(stringResource(R.string.project_page_yarn), stringResource(R.string.project_page_manage_yarn), { onSection(ProjectSection.MATERIALS) }) {
        if (state.yarn.isEmpty()) QuietText(stringResource(R.string.project_page_no_yarn))
        state.yarn.forEach { yarn ->
            Text(yarn.item.name, style = MaterialTheme.typography.titleMedium)
            yarn.item.colorway?.let { QuietText(it) }
            QuietText(stringResource(R.string.project_page_yarn_reserved, yarn.allocation.quantityReserved.toString(), yarn.item.unitLabel))
            QuietText(stringResource(R.string.project_page_yarn_used, yarn.allocation.quantityUsed.toString(), yarn.item.unitLabel))
            yarn.item.estimatedRemainingYards()?.let { QuietText(stringResource(R.string.project_page_remaining_yarn, it.toString())) }
            yarn.allocation.notes?.let { Text(it) }
        }
    }
}

@Composable
internal fun ProjectDashboardTools(tools: List<ToolItem>, onTools: () -> Unit) {
    ProjectPageSection(stringResource(R.string.project_page_tools), stringResource(R.string.project_page_manage_tools), onTools) {
        if (tools.isEmpty()) QuietText(stringResource(R.string.project_page_no_tools))
        tools.forEach { Text(listOfNotNull(it.name, it.sizeLabel, it.sizeMetricMm?.let { mm -> "$mm mm" }).joinToString(" \u00b7 ")) }
    }
 }

@Composable
internal fun ProjectDashboardAfterGuides(state: ProjectDashboardState, onSection: (ProjectSection) -> Unit) {
    ProjectPageSection(stringResource(R.string.project_page_notes), stringResource(R.string.project_page_manage_notes), { onSection(ProjectSection.JOURNAL) }) {
        if (state.notes.isEmpty()) QuietText(stringResource(R.string.project_page_no_notes))
        state.notes.take(5).forEach { note ->
            QuietText(note.entryDate)
            note.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            Text(note.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun ProjectGuideProgress(entry: GuideListEntry) {
    entry.progress?.let { progress ->
        QuietText(stringResource(R.string.project_page_steps, progress.completedSteps, progress.totalSteps))
        entry.sections.forEach { section ->
            Text(section.text, style = MaterialTheme.typography.titleSmall)
            val fraction = if (section.totalSteps > 0) section.completedSteps.toFloat() / section.totalSteps else 0f
            QuietText("${(fraction * 100).toInt()}%")
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
        entry.nextInstruction?.let { Text(stringResource(R.string.project_page_next_instruction, it), style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
internal fun ProjectQuickCounter(counters: List<Counter>, actions: ProjectHubActions, onManage: () -> Unit) {
    val counter = counters.firstOrNull()
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        TextButton(onClick = onManage, modifier = Modifier.weight(1f)) { Text(counter?.name ?: stringResource(R.string.counters_header_title)) }
        if (counter != null) {
            TextButton(onClick = { actions.onDecrementCounter(counter) }, enabled = counter.currentValue > 0) { Text("\u2212") }
            Text(counter.currentValue.toString(), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { actions.onIncrementCounter(counter) }) { Text("+") }
        }
    }
}
