package com.macareen.stitchbook2.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.macareen.stitchbook2.StitchbookApplication
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.feature.counters.CountersRoute
import com.macareen.stitchbook2.feature.counters.CountersViewModel
import com.macareen.stitchbook2.feature.draft.DraftEditorRoute
import com.macareen.stitchbook2.feature.draft.DraftEditorViewModel
import com.macareen.stitchbook2.feature.focus.GuideFocusRoute
import com.macareen.stitchbook2.feature.focus.GuideFocusViewModel
import com.macareen.stitchbook2.feature.home.HomeRoute
import com.macareen.stitchbook2.feature.home.HomeViewModel
import com.macareen.stitchbook2.feature.library.LibraryRoute
import com.macareen.stitchbook2.feature.library.PdfViewerRoute
import com.macareen.stitchbook2.feature.library.PdfViewerViewModel
import com.macareen.stitchbook2.feature.library.LibraryViewModel
import com.macareen.stitchbook2.feature.projects.ProjectDetailRoute
import com.macareen.stitchbook2.feature.projects.ProjectDetailViewModel
import com.macareen.stitchbook2.feature.projects.ProjectFormRoute
import com.macareen.stitchbook2.feature.projects.ProjectFormViewModel
import com.macareen.stitchbook2.feature.projects.ProjectsRoute
import com.macareen.stitchbook2.feature.projects.ProjectsViewModel
import com.macareen.stitchbook2.feature.settings.SettingsRoute
import com.macareen.stitchbook2.feature.settings.SettingsViewModel
import com.macareen.stitchbook2.feature.stash.StashRoute
import com.macareen.stitchbook2.feature.stash.StashViewModel
import com.macareen.stitchbook2.feature.tools.BulkToolCreationRoute
import com.macareen.stitchbook2.feature.tools.BulkToolCreationViewModel
import com.macareen.stitchbook2.feature.tools.ToolsRoute
import com.macareen.stitchbook2.feature.tools.ToolsViewModel

@Composable
fun StitchbookNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as StitchbookApplication
    val projectRepository = application.container.projectRepository
    val guideRepository = application.container.guideRepository
    val executionRepository = application.container.executionRepository
    val libraryRepository = application.container.libraryRepository
    val stashRepository = application.container.stashRepository
    val toolRepository = application.container.toolRepository
    val counterRepository = application.container.counterRepository
    val counterNoteRepository = application.container.counterNoteRepository
    val backupService = application.container.backupService
    val createGuideFromPdfUseCase = application.container.createGuideFromPdfUseCase

    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Home.route,
        modifier = modifier
    ) {
        composable(TopLevelDestination.Home.route) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(
                    projectRepository = projectRepository,
                    guideRepository = guideRepository,
                    executionRepository = executionRepository
                )
            )
            HomeRoute(
                viewModel = viewModel,
                onNewProject = {
                    navController.navigate(ProjectDestination.CREATE_ROUTE)
                },
                onOpenProject = { projectId ->
                    navController.navigate(ProjectDestination.detailRoute(projectId))
                },
                onOpenProjects = {
                    navController.navigateToTopLevelDestination(TopLevelDestination.Projects)
                },
                onOpenLibrary = {
                    navController.navigateToTopLevelDestination(TopLevelDestination.Library)
                },
                onOpenStash = {
                    navController.navigateToTopLevelDestination(TopLevelDestination.Stash)
                },
                onResumeGuide = { guideId ->
                    navController.navigate(GuideFocusDestination.route(guideId))
                }
            )
        }
        composable(TopLevelDestination.Projects.route) {
            val viewModel: ProjectsViewModel = viewModel(
                factory = ProjectsViewModel.factory(projectRepository)
            )
            ProjectsRoute(
                viewModel = viewModel,
                onAddProject = {
                    navController.navigate(ProjectDestination.CREATE_ROUTE)
                },
                onOpenProject = { projectId ->
                    navController.navigate(ProjectDestination.detailRoute(projectId))
                }
            )
        }
        composable(TopLevelDestination.Library.route) {
            val viewModel: LibraryViewModel = viewModel(
                factory = LibraryViewModel.factory(libraryRepository)
            )
            LibraryRoute(
                viewModel = viewModel,
                onOpenPdf = { libraryItemId ->
                    navController.navigate(PdfViewerDestination.route(libraryItemId))
                }
            )
        }
        composable(
            route = PdfViewerDestination.ROUTE,
            arguments = listOf(
                navArgument(PdfViewerDestination.LIBRARY_ITEM_ID_ARGUMENT) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val libraryItemId = backStackEntry.arguments?.getString(
                PdfViewerDestination.LIBRARY_ITEM_ID_ARGUMENT
            )
                .orEmpty()
            val viewModel: PdfViewerViewModel = viewModel(
                factory = PdfViewerViewModel.factory(
                    libraryItemId = libraryItemId,
                    repository = libraryRepository
                )
            )
            PdfViewerRoute(viewModel = viewModel)
        }
        composable(TopLevelDestination.Stash.route) {
            val viewModel: StashViewModel = viewModel(
                factory = StashViewModel.factory(stashRepository)
            )
            StashRoute(viewModel = viewModel)
        }
        composable(TopLevelDestination.Tools.route) {
            val viewModel: ToolsViewModel = viewModel(
                factory = ToolsViewModel.factory(toolRepository)
            )
            ToolsRoute(
                viewModel = viewModel,
                onBulkCreate = {
                    navController.navigate(BulkToolCreationDestination.ROUTE)
                }
            )
        }
        composable(BulkToolCreationDestination.ROUTE) {
            val viewModel: BulkToolCreationViewModel = viewModel(
                factory = BulkToolCreationViewModel.factory(toolRepository)
            )
            BulkToolCreationRoute(
                viewModel = viewModel,
                onDone = navController::popBackStack
            )
        }
        composable(TopLevelDestination.Counters.route) {
            val viewModel: CountersViewModel = viewModel(
                factory = CountersViewModel.factory(
                    counterRepository,
                    projectRepository,
                    counterNoteRepository
                )
            )
            CountersRoute(viewModel = viewModel)
        }
        composable(TopLevelDestination.Settings.route) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(backupService)
            )
            SettingsRoute(viewModel = viewModel)
        }
        composable(ProjectDestination.CREATE_ROUTE) {
            val viewModel: ProjectFormViewModel = viewModel(
                factory = ProjectFormViewModel.factory(
                    projectId = null,
                    repository = projectRepository
                )
            )
            ProjectFormRoute(
                viewModel = viewModel,
                onSaved = navController::popBackStack,
                onCancel = navController::popBackStack
            )
        }
        composable(
            route = ProjectDestination.DETAIL_ROUTE,
            arguments = listOf(
                navArgument(ProjectDestination.PROJECT_ID_ARGUMENT) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString(
                ProjectDestination.PROJECT_ID_ARGUMENT
            )
                .orEmpty()
            val viewModel: ProjectDetailViewModel = viewModel(
                factory = ProjectDetailViewModel.factory(
                    projectId = projectId,
                    repository = projectRepository,
                    guideRepository = guideRepository,
                    executionRepository = executionRepository,
                    createGuideFromPdfUseCase = createGuideFromPdfUseCase
                )
            )
            ProjectDetailRoute(
                viewModel = viewModel,
                onEditProject = { id ->
                    navController.navigate(ProjectDestination.editRoute(id))
                },
                onProjectDeleted = {
                    navController.popBackStack(
                        route = TopLevelDestination.Projects.route,
                        inclusive = false
                    )
                },
                onOpenGuide = { guideId ->
                    navController.navigate(GuideFocusDestination.route(guideId))
                },
                onEditDraft = { guideId ->
                    navController.navigate(DraftEditorDestination.route(guideId))
                }
            )
        }
        composable(
            route = DraftEditorDestination.ROUTE,
            arguments = listOf(
                navArgument(DraftEditorDestination.GUIDE_ID_ARGUMENT) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val guideId = backStackEntry.arguments?.getString(
                DraftEditorDestination.GUIDE_ID_ARGUMENT
            )
                .orEmpty()
            val viewModel: DraftEditorViewModel = viewModel(
                factory = DraftEditorViewModel.factory(
                    guideId = GuideId(guideId),
                    guideRepository = guideRepository,
                    executionRepository = executionRepository
                )
            )
            DraftEditorRoute(
                viewModel = viewModel,
                onDone = navController::popBackStack,
                onStartOrContinue = {
                    navController.navigate(GuideFocusDestination.route(guideId))
                }
            )
        }
        composable(
            route = GuideFocusDestination.ROUTE,
            arguments = listOf(
                navArgument(GuideFocusDestination.GUIDE_ID_ARGUMENT) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val guideId = backStackEntry.arguments?.getString(
                GuideFocusDestination.GUIDE_ID_ARGUMENT
            )
                .orEmpty()
            val viewModel: GuideFocusViewModel = viewModel(
                factory = GuideFocusViewModel.factory(
                    guideId = GuideId(guideId),
                    guideRepository = guideRepository,
                    executionRepository = executionRepository
                )
            )
            GuideFocusRoute(viewModel = viewModel)
        }
        composable(
            route = ProjectDestination.EDIT_ROUTE,
            arguments = listOf(
                navArgument(ProjectDestination.PROJECT_ID_ARGUMENT) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString(
                ProjectDestination.PROJECT_ID_ARGUMENT
            )
                .orEmpty()
            val viewModel: ProjectFormViewModel = viewModel(
                factory = ProjectFormViewModel.factory(
                    projectId = projectId,
                    repository = projectRepository
                )
            )
            ProjectFormRoute(
                viewModel = viewModel,
                onSaved = navController::popBackStack,
                onCancel = navController::popBackStack
            )
        }
    }
}

fun NavHostController.navigateToTopLevelDestination(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
