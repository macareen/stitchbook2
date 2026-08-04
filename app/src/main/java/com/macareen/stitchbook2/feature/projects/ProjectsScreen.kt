package com.macareen.stitchbook2.feature.projects

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.ui.components.LabelPill
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.cardTitle
import com.macareen.stitchbook2.ui.theme.textSecondary
import java.text.DateFormat
import java.util.Date

@Composable
fun ProjectsRoute(
    viewModel: ProjectsViewModel,
    onAddProject: () -> Unit,
    onOpenProject: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProjectsScreen(
        uiState = uiState,
        onAddProject = onAddProject,
        onOpenProject = onOpenProject
    )
}

@Composable
fun ProjectsScreen(
    uiState: ProjectsUiState,
    onAddProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            ProjectsUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            ProjectsUiState.Empty -> {
                EmptyProjects(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                )
            }

            ProjectsUiState.Error -> {
                MessageState(
                    title = stringResource(R.string.projects_load_error_title),
                    description = stringResource(R.string.projects_load_error_description),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                )
            }

            is ProjectsUiState.Content -> {
                ProjectList(
                    projects = uiState.projects,
                    onOpenProject = onOpenProject
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddProject,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.add_project)
                )
            },
            text = { Text(text = stringResource(R.string.add_project)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun EmptyProjects(modifier: Modifier = Modifier) {
    MessageState(
        title = stringResource(R.string.projects_empty_title),
        description = stringResource(R.string.projects_empty_description),
        modifier = modifier
    )
}

@Composable
private fun MessageState(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Checklist,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProjectList(
    projects: List<Project>,
    onOpenProject: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 20.dp,
            end = 16.dp,
            bottom = 104.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.projects_list_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        items(
            items = projects,
            key = { it.id }
        ) { project ->
            ProjectListItem(
                project = project,
                onClick = { onOpenProject(project.id) }
            )
        }
    }
}

@Composable
private fun ProjectListItem(
    project: Project,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabelPill(
                    text = stringResource(project.craft.labelResource()),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.textSecondary
                )
                val (statusContainer, statusContent) = project.status.pillColors()
                LabelPill(
                    text = stringResource(project.status.labelResource()),
                    containerColor = statusContainer,
                    contentColor = statusContent
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = project.name,
                style = MaterialTheme.typography.cardTitle,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(project.projectType.labelResource()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.project_updated_label
                    ) + " " + formatUpdatedDate(project.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.textSecondary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.textSecondary
                )
            }
        }
    }
}

private fun formatUpdatedDate(timestamp: Long): String {
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
private fun EmptyProjectsPreview() {
    StitchbookTheme {
        ProjectsScreen(
            uiState = ProjectsUiState.Empty,
            onAddProject = {},
            onOpenProject = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProjectListPreview() {
    StitchbookTheme {
        ProjectsScreen(
            uiState = ProjectsUiState.Content(
                projects = listOf(
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
                )
            ),
            onAddProject = {},
            onOpenProject = {}
        )
    }
}
