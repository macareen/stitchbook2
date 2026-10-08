package com.macareen.stitchbook2.feature.projects

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.ui.graphics.vector.ImageVector
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.navigation.ProjectDestination

/**
 * A project's companion screens, each owning its own ViewModel so Project
 * detail stays a focused overview rather than one ever-growing screen.
 */
enum class ProjectSection(
    @get:StringRes val title: Int,
    @get:StringRes val description: Int,
    val icon: ImageVector
) {
    MATERIALS(
        title = R.string.materials_title,
        description = R.string.project_section_materials_description,
        icon = Icons.Outlined.Inventory2
    )
}

fun ProjectSection.route(projectId: String): String = when (this) {
    ProjectSection.MATERIALS -> ProjectDestination.materialsRoute(projectId)
}
