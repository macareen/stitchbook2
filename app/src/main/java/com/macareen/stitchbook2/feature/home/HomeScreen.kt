package com.macareen.stitchbook2.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.feature.projects.labelResource
import com.macareen.stitchbook2.ui.components.PrimaryActionButton
import com.macareen.stitchbook2.ui.components.QuietText
import com.macareen.stitchbook2.ui.theme.StitchbookSpacing
import com.macareen.stitchbook2.ui.theme.StitchbookTheme
import com.macareen.stitchbook2.ui.theme.buttonLabel
import com.macareen.stitchbook2.ui.theme.textSecondary
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
    onResumeGuide: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onNewProject = onNewProject,
        onOpenProject = onOpenProject,
        onOpenProjects = onOpenProjects,
        onOpenLibrary = onOpenLibrary,
        onOpenStash = onOpenStash,
        onResumeGuide = onResumeGuide
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
    modifier: Modifier = Modifier
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
                modifier = modifier
            )
        }
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState.Content,
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenStash: () -> Unit,
    onResumeGuide: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = StitchbookSpacing.medium,
            top = StitchbookSpacing.medium,
            end = StitchbookSpacing.medium,
            bottom = StitchbookSpacing.extraExtraLarge
        ),
        verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.large)
    ) {
        item {
            HeroSection(
                resumeGuide = uiState.resumeGuide,
                onNewProject = onNewProject,
                onResumeGuide = onResumeGuide
            )
        }

        item {
            StatsGrid(uiState = uiState)
        }

        item {
            QuickNavRow(
                onOpenProjects = onOpenProjects,
                onOpenStash = onOpenStash,
                onOpenLibrary = onOpenLibrary
            )
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
    }
}

@Composable
private fun HeroSection(
    resumeGuide: ResumeGuide?,
    onNewProject: () -> Unit,
    onResumeGuide: (String) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.large)) {
            HeroBadge(text = stringResource(R.string.home_hero_badge))
            Spacer(modifier = Modifier.height(StitchbookSpacing.medium))
            Text(
                text = stringResource(R.string.home_hero_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            Text(
                text = stringResource(R.string.home_hero_subtitle),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.large))
            if (resumeGuide != null) {
                PrimaryActionButton(
                    text = stringResource(
                        R.string.home_resume_action,
                        resumeGuide.guideName
                    ),
                    onClick = { onResumeGuide(resumeGuide.guideId) },
                    icon = Icons.Filled.PlayArrow,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.small))
            }
            HeroSecondaryButton(
                text = stringResource(R.string.home_new_project_action),
                icon = Icons.Filled.Add,
                onClick = onNewProject,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HeroBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        contentColor = MaterialTheme.colorScheme.inversePrimary
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = StitchbookSpacing.medium,
                vertical = StitchbookSpacing.extraSmall
            ),
            horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * A filled, visually quiet secondary action for the hero's dark surface,
 * where [com.macareen.stitchbook2.ui.components.SecondaryActionButton]'s
 * outline-on-`onBackground` styling (designed for light surfaces) would be
 * nearly invisible against [MaterialTheme.colorScheme.inverseSurface].
 */
@Composable
private fun HeroSecondaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = text, style = MaterialTheme.typography.buttonLabel)
        }
    }
}

@Composable
private fun StatsGrid(uiState: HomeUiState.Content) {
    Column(verticalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            StatTile(
                label = stringResource(R.string.home_stat_active_projects),
                value = uiState.activeProjectCount.toString(),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.home_stat_total_projects),
                value = uiState.totalProjectCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
            StatTile(
                label = stringResource(R.string.home_stat_craft_types),
                value = uiState.craftCount.toString(),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.home_stat_data_mode),
                value = stringResource(R.string.home_stat_data_mode_value),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(modifier = Modifier.padding(StitchbookSpacing.medium)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textSecondary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(StitchbookSpacing.extraSmall))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class QuickNavItem(
    val icon: ImageVector,
    val titleRes: Int,
    val descriptionRes: Int,
    val ctaRes: Int,
    val containerColor: Color,
    val onContainerColor: Color,
    val onClick: () -> Unit
)

@Composable
private fun QuickNavRow(
    onOpenProjects: () -> Unit,
    onOpenStash: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    val items = listOf(
        QuickNavItem(
            icon = Icons.Outlined.Checklist,
            titleRes = R.string.home_quick_nav_projects_title,
            descriptionRes = R.string.home_quick_nav_projects_description,
            ctaRes = R.string.home_quick_nav_projects_cta,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onOpenProjects
        ),
        QuickNavItem(
            icon = Icons.Outlined.Inventory2,
            titleRes = R.string.home_quick_nav_stash_title,
            descriptionRes = R.string.home_quick_nav_stash_description,
            ctaRes = R.string.home_quick_nav_stash_cta,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            onContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onOpenStash
        ),
        QuickNavItem(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            titleRes = R.string.home_quick_nav_library_title,
            descriptionRes = R.string.home_quick_nav_library_description,
            ctaRes = R.string.home_quick_nav_library_cta,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            onContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onOpenLibrary
        )
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.small)) {
        items(items) { item ->
            Card(
                modifier = Modifier
                    .width(240.dp)
                    .clickable(onClick = item.onClick),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                Column(modifier = Modifier.padding(StitchbookSpacing.medium)) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = item.containerColor,
                        contentColor = item.onContainerColor
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(StitchbookSpacing.small)
                                .size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    Text(
                        text = stringResource(item.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(StitchbookSpacing.extraSmall))
                    QuietText(text = stringResource(item.descriptionRes))
                    Spacer(modifier = Modifier.height(StitchbookSpacing.small))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(StitchbookSpacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(item.ctaRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(StitchbookSpacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(project.craft.labelResource()).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.extraSmall))
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(StitchbookSpacing.extraSmall))
                QuietText(
                    text = stringResource(
                        R.string.home_project_updated,
                        formatTimestamp(project.updatedAt)
                    )
                )
            }
            Spacer(modifier = Modifier.width(StitchbookSpacing.small))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(StitchbookSpacing.small)
                        .size(18.dp)
                )
            }
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
