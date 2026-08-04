package com.macareen.stitchbook2.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.macareen.stitchbook2.R

enum class TopLevelDestination(
    val route: String,
    @get:StringRes val title: Int,
    @get:StringRes val iconContentDescription: Int,
    val icon: ImageVector
) {
    Home(
        route = "home",
        title = R.string.destination_home,
        iconContentDescription = R.string.home_icon_description,
        icon = Icons.Outlined.Home
    ),
    Projects(
        route = "projects",
        title = R.string.destination_projects,
        iconContentDescription = R.string.projects_icon_description,
        icon = Icons.Outlined.Checklist
    ),
    Library(
        route = "library",
        title = R.string.destination_library,
        iconContentDescription = R.string.library_icon_description,
        icon = Icons.AutoMirrored.Outlined.MenuBook
    ),
    Stash(
        route = "stash",
        title = R.string.destination_stash,
        iconContentDescription = R.string.stash_icon_description,
        icon = Icons.Outlined.Inventory2
    ),
    Settings(
        route = "settings",
        title = R.string.destination_settings,
        iconContentDescription = R.string.settings_icon_description,
        icon = Icons.Outlined.Settings
    )
}
