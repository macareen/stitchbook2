package com.macareen.stitchbook2

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.macareen.stitchbook2.navigation.StitchbookNavHost
import com.macareen.stitchbook2.navigation.TopLevelDestination
import com.macareen.stitchbook2.navigation.navigateToTopLevelDestination
import com.macareen.stitchbook2.ui.components.BottomBarItem
import com.macareen.stitchbook2.ui.components.StitchbookBottomBar

/**
 * Top-level destinations (Home/Projects/Library/Stash/Tools/Settings) each already
 * render their own in-content headline, so a redundant static app-name bar
 * above them would just duplicate that identity -- the webapp itself shows
 * no header chrome above its own bottom nav on mobile. Every other
 * (non-top-level) destination gets a minimal, title-less bar whose only job
 * is a visible back affordance, since some of those screens (Project detail
 * in particular) have no other way back besides the system back
 * gesture/button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StitchbookApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isTopLevelDestination = TopLevelDestination.entries.any { destination ->
        currentDestination
            ?.hierarchy
            ?.any { it.route == destination.route } == true
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (!isTopLevelDestination) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = navController::popBackStack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        bottomBar = {
            if (isTopLevelDestination) {
                val items = TopLevelDestination.entries.map { destination ->
                    BottomBarItem(
                        key = destination.route,
                        label = stringResource(destination.title),
                        icon = destination.icon
                    )
                }
                val selectedKey = TopLevelDestination.entries.firstOrNull { destination ->
                    currentDestination?.hierarchy?.any { it.route == destination.route } == true
                }?.route
                StitchbookBottomBar(
                    items = items,
                    selectedKey = selectedKey,
                    onSelect = { item ->
                        TopLevelDestination.entries
                            .first { it.route == item.key }
                            .let(navController::navigateToTopLevelDestination)
                    }
                )
            }
        }
    ) { innerPadding ->
        StitchbookNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
