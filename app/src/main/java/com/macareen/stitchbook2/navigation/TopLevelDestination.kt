package com.macareen.stitchbook2.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.macareen.stitchbook2.R

/** The five bottom-bar places. Tools live inside Stash; the Counters list opens from Home. */
enum class TopLevelDestination(
    val route: String,
    @get:StringRes val title: Int,
    @get:StringRes val iconContentDescription: Int,
    @get:DrawableRes val icon: Int
) {
    Home(
        route = "home",
        title = R.string.destination_home,
        iconContentDescription = R.string.home_icon_description,
        icon = R.drawable.ic_nav_home
    ),
    Projects(
        route = "projects",
        title = R.string.destination_projects,
        iconContentDescription = R.string.projects_icon_description,
        icon = R.drawable.ic_nav_projects
    ),
    Library(
        route = "library",
        title = R.string.destination_library,
        iconContentDescription = R.string.library_icon_description,
        icon = R.drawable.ic_nav_library
    ),
    Stash(
        route = "stash",
        title = R.string.destination_stash,
        iconContentDescription = R.string.stash_icon_description,
        icon = R.drawable.ic_nav_stash
    ),
    Settings(
        route = "settings",
        title = R.string.destination_settings,
        iconContentDescription = R.string.settings_icon_description,
        icon = R.drawable.ic_nav_settings
    )
}
