package com.macareen.stitchbook2

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.macareen.stitchbook2.navigation.StitchbookNavHost
import com.macareen.stitchbook2.navigation.TopLevelDestination
import com.macareen.stitchbook2.navigation.navigateToTopLevelDestination

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
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) }
            )
        },
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == destination.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigateToTopLevelDestination(destination)
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(
                                        destination.iconContentDescription
                                    )
                                )
                            },
                            label = {
                                Text(text = stringResource(destination.title))
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        StitchbookNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
