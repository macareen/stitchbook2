package com.macareen.stitchbook2.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.feature.projects.labelResource
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.cardTitle
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenStash: () -> Unit,
    onResumeGuide: (String) -> Unit,
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
    onResumeGuide: (String) -> Unit,
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
                onOpenLibrary = onOpenLibrary,
                onOpenStash = onOpenStash,
                onResumeGuide = onResumeGuide,
                onOpenStatistics = onOpenStatistics,
                onOpenCounters = onOpenCounters,
                modifier = modifier
            )
        }
    }
}

/**
 * A calm start screen: what you were doing, then what's in progress.
 * Counts and feature tours were removed; the project hub carries detail.
 */
@Composable
private fun HomeContent(
    uiState: HomeUiState.Content,
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenStash: () -> Unit,
    onResumeGuide: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenStatistics: () -> Unit = {},
    onOpenCounters: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = StitchbookSpacing.large,
            top = StitchbookSpacing.large,
            end = StitchbookSpacing.large,
            bottom = StitchbookSpacing.extraExtraLarge
        ),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.medium)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNewProject) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.home_new_project_action))
                }
            }
        }

        uiState.resumeGuide?.let { resume ->
            item { ContinueCard(resume = resume, onClick = { onResumeGuide(resume.guideId) }) }
        }

        item {
            ActiveProjectsHeader(
                count = uiState.activeProjects.size,
                onViewAll = onOpenProjects
            )
        }

        if (uiState.activeProjects.isEmpty()) {
            item {
                EmptyActiveProjects(onNewProject = onNewProject)
            }
        } else {
            items(
                items = uiState.activeProjects,
                key = { it.id }
            ) { project ->
                HomeProjectCard(
                    project = project,
                    onClick = { onOpenProject(project.id) }
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
                TextButton(onClick = onOpenCounters) { Text(text = stringResource(R.string.destination_counters)) }
                TextButton(onClick = onOpenStatistics) { Text(text = stringResource(R.string.home_quick_nav_statistics_title)) }
            }
        }
    }
}

/** The one thing you were in the middle of. */
@Composable
private fun ContinueCard(resume: ResumeGuide, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(StitchbookSpacing.large)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.home_continue_label), style = MaterialTheme.typography.labelLarge)
                Text(text = resume.guideName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(text = resume.projectName, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
        }
    }
}

@Composable
private fun ActiveProjectsHeader(count: Int, onViewAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.home_active_projects_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.home_active_projects_view_all, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onViewAll)
        )
    }
}

@Composable
private fun EmptyActiveProjects(onNewProject: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.large)) {
            Text(
                text = stringResource(R.string.home_active_projects_empty),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Text(
                text = stringResource(R.string.home_active_projects_empty_cta),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onNewProject)
            )
        }
    }
}

@Composable
private fun HomeProjectCard(
    project: Project,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.medium)) {
            Text(text = project.name, style = MaterialTheme.typography.cardTitle)
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
                    projectName = "Everyday cardigan"
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
