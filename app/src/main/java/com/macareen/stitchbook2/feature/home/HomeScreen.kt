package com.macareen.stitchbook2.feature.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.feature.projects.labelResource
import com.macareen.stitchbook2.ui.components.HeartYarnBall
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.components.SkeinArt
import com.macareen.stitchbook2.ui.theme.CozyPastels
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import java.text.DateFormat
import java.time.LocalTime
import java.util.Date

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenStash: () -> Unit,
    onResumeGuide: (ResumeGuide) -> Unit,
    onOpenStatistics: () -> Unit = {},
    onOpenCounters: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onNewProject = onNewProject,
        onOpenProject = onOpenProject,
        onOpenProjects = onOpenProjects,
        onOpenLibrary = onOpenLibrary,
        onOpenStash = onOpenStash,
        onResumeGuide = onResumeGuide,
        onOpenStatistics = onOpenStatistics,
        onOpenCounters = onOpenCounters
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenStash: () -> Unit,
    onResumeGuide: (ResumeGuide) -> Unit,
    modifier: Modifier = Modifier,
    onOpenStatistics: () -> Unit = {},
    onOpenCounters: () -> Unit = {}
) {
    when (uiState) {
        HomeUiState.Loading -> {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        HomeUiState.Error -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(StitchbookSpacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.home_load_error_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        is HomeUiState.Content -> {
            HomeContent(
                uiState = uiState,
                onNewProject = onNewProject,
                onOpenProject = onOpenProject,
                onOpenProjects = onOpenProjects,
                onResumeGuide = onResumeGuide,
                onOpenStatistics = onOpenStatistics,
                onOpenCounters = onOpenCounters,
                modifier = modifier
            )
        }
    }
}

/**
 * A cozy start screen: a greeting, the one thing you were in the middle of as
 * a large card, then your projects as a row of illustrated cards.
 */
@Composable
private fun HomeContent(
    uiState: HomeUiState.Content,
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenProjects: () -> Unit,
    onResumeGuide: (ResumeGuide) -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenCounters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sidePadding = Modifier.padding(horizontal = StitchbookSpacing.large)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = StitchbookSpacing.large,
            bottom = StitchbookSpacing.extraExtraLarge
        ),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.large)
    ) {
        item { Greeting(onNewProject = onNewProject, modifier = sidePadding) }

        uiState.resumeGuide?.let { resume ->
            item {
                ContinueCard(resume = resume, onClick = { onResumeGuide(resume) }, modifier = sidePadding)
            }
        }

        item { ActiveProjectsHeader(onViewAll = onOpenProjects, modifier = sidePadding) }

        item {
            if (uiState.activeProjects.isEmpty()) {
                EmptyActiveProjects(onNewProject = onNewProject, modifier = sidePadding)
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = StitchbookSpacing.large),
                    horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium)
                ) {
                    items(items = uiState.activeProjects, key = { it.id }) { project ->
                        HomeProjectCard(project = project, onClick = { onOpenProject(project.id) })
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small),
                modifier = sidePadding
            ) {
                SoftChip(text = stringResource(R.string.destination_counters), onClick = onOpenCounters)
                SoftChip(text = stringResource(R.string.home_quick_nav_statistics_title), onClick = onOpenStatistics)
            }
        }
    }
}

@Composable
private fun Greeting(onNewProject: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(greetingFor(LocalTime.now().hour)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(text = stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
        }
        HeartYarnBall(size = 48.dp)
        Spacer(Modifier.width(StitchbookSpacing.small))
        FilledIconButton(onClick = onNewProject) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.home_new_project_action))
        }
    }
}

/** The one thing you were in the middle of, as the screen's centrepiece. */
@Composable
private fun ContinueCard(resume: ResumeGuide, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pastel = CozyPastels.forKey(resume.projectId ?: resume.guideId)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(StitchbookSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(pastel.tile)
            ) {
                SkeinArt(pastel = pastel, width = 84.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_continue_label).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = resume.guideName, style = MaterialTheme.typography.titleLarge)
                    QuietText(text = resume.projectName)
                }
                resume.percentDone?.let { percent -> ProgressRing(percent = percent) }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = stringResource(R.string.home_keep_going),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(percent: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(58.dp)) {
        CircularProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.inversePrimary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeWidth = 7.dp,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp
        )
        Text(
            text = stringResource(R.string.home_percent, percent),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun ActiveProjectsHeader(onViewAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.home_active_projects_title),
            style = MaterialTheme.typography.titleLarge
        )
        TextButton(onClick = onViewAll) {
            Text(text = stringResource(R.string.home_active_projects_view_all))
        }
    }
}

@Composable
private fun EmptyActiveProjects(onNewProject: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onNewProject,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(StitchbookSpacing.large)
        ) {
            SkeinArt(pastel = CozyPastels.Blush, width = 48.dp)
            Spacer(Modifier.width(StitchbookSpacing.medium))
            Column {
                Text(
                    text = stringResource(R.string.home_active_projects_empty),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.home_active_projects_empty_cta),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun HomeProjectCard(
    project: Project,
    onClick: () -> Unit
) {
    val pastel = CozyPastels.forKey(project.id)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.width(156.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(pastel.tile)
            ) {
                SkeinArt(pastel = pastel, width = 44.dp)
            }
            Text(text = project.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            QuietText(
                text = stringResource(
                    R.string.home_project_meta,
                    stringResource(project.craft.labelResource()),
                    formatTimestamp(project.updatedAt)
                )
            )
        }
    }
}

@Composable
private fun SoftChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = StitchbookSpacing.medium, vertical = 10.dp)
        )
    }
}

/** Morning from five until noon, afternoon until six, evening otherwise. */
internal fun greetingFor(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.home_greeting_morning
    in 12..17 -> R.string.home_greeting_afternoon
    else -> R.string.home_greeting_evening
}

private fun formatTimestamp(timestamp: Long): String {
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    StitchbookTheme {
        HomeScreen(
            uiState = HomeUiState.Content(
                activeProjectCount = 2,
                totalProjectCount = 3,
                craftCount = 2,
                activeProjects = listOf(
                    Project(
                        id = "preview",
                        name = "Everyday cardigan",
                        craft = Craft.KNITTING,
                        projectType = ProjectType.CARDIGAN,
                        status = ProjectStatus.ACTIVE,
                        notes = null,
                        createdAt = 0,
                        updatedAt = 0
                    )
                ),
                resumeGuide = ResumeGuide(
                    guideId = "guide",
                    guideName = "Body & Textured Lace Panel",
                    projectName = "Everyday cardigan",
                    percentDone = 42
                )
            ),
            onNewProject = {},
            onOpenProject = {},
            onOpenProjects = {},
            onOpenLibrary = {},
            onOpenStash = {},
            onResumeGuide = {}
        )
    }
}
