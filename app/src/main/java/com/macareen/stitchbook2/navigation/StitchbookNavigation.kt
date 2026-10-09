package com.macareen.stitchbook2.navigation

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.net.Uri
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
import com.macareen.stitchbook2.feature.cards.ShareCardRoute
import com.macareen.stitchbook2.feature.cards.ShareCardViewModel
import com.macareen.stitchbook2.feature.assist.AssistedImportRoute
import com.macareen.stitchbook2.feature.assist.AssistedImportViewModel
import com.macareen.stitchbook2.feature.counters.CountersRoute
import com.macareen.stitchbook2.feature.counters.CountersViewModel
import com.macareen.stitchbook2.feature.draft.DraftEditorRoute
import com.macareen.stitchbook2.feature.draft.DraftEditorViewModel
import com.macareen.stitchbook2.feature.focus.GuideFocusRoute
import com.macareen.stitchbook2.feature.focus.GuideFocusViewModel
import com.macareen.stitchbook2.feature.home.HomeRoute
import com.macareen.stitchbook2.feature.home.HomeViewModel
import com.macareen.stitchbook2.feature.journal.ProjectJournalRoute
import com.macareen.stitchbook2.feature.journal.ProjectJournalViewModel
import com.macareen.stitchbook2.feature.library.LibraryRoute
import com.macareen.stitchbook2.feature.library.PatternGuidesRoute
import com.macareen.stitchbook2.feature.library.PatternGuidesViewModel
import com.macareen.stitchbook2.feature.library.PdfViewerRoute
import com.macareen.stitchbook2.feature.library.PdfViewerViewModel
import com.macareen.stitchbook2.feature.library.LibraryViewModel
import com.macareen.stitchbook2.feature.materials.ProjectMaterialsRoute
import com.macareen.stitchbook2.feature.materials.ProjectMaterialsViewModel
import com.macareen.stitchbook2.feature.calculators.CalculatorsScreen
import com.macareen.stitchbook2.feature.projects.ProjectDetailRoute
import com.macareen.stitchbook2.feature.projects.ProjectDetailViewModel
import com.macareen.stitchbook2.feature.projects.ProjectFormRoute
import com.macareen.stitchbook2.feature.projects.ProjectFormViewModel
import com.macareen.stitchbook2.feature.projects.ProjectsRoute
import com.macareen.stitchbook2.feature.projects.ProjectsViewModel
import com.macareen.stitchbook2.feature.projects.route
import com.macareen.stitchbook2.feature.sessions.ProjectSessionsRoute
import com.macareen.stitchbook2.feature.sessions.ProjectSessionsViewModel
import com.macareen.stitchbook2.feature.settings.SettingsRoute
import com.macareen.stitchbook2.feature.settings.SettingsViewModel
import com.macareen.stitchbook2.feature.ravelry.RavelryRoute
import com.macareen.stitchbook2.feature.ravelry.RavelryViewModel
import com.macareen.stitchbook2.feature.stash.StashHost
import com.macareen.stitchbook2.feature.stash.StashRoute
import com.macareen.stitchbook2.feature.statistics.StatisticsRoute
import com.macareen.stitchbook2.feature.statistics.StatisticsViewModel
import com.macareen.stitchbook2.feature.stash.StashViewModel
import com.macareen.stitchbook2.feature.tools.BulkToolCreationRoute
import com.macareen.stitchbook2.feature.tools.BulkToolCreationViewModel
import com.macareen.stitchbook2.feature.tools.ToolSetsRoute
import com.macareen.stitchbook2.feature.tools.ToolSetsViewModel
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
    val userPreferencesRepository = application.container.userPreferencesRepository
    val materialsRepository = application.container.materialsRepository
    val journalRepository = application.container.journalRepository
    val sessionRepository = application.container.sessionRepository

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
                onResumeGuide = { resume ->
                    navController.navigate(GuideFocusDestination.route(resume.guideId, resume.projectId))
                },
                onOpenStatistics = {
                    navController.navigate(ProjectDestination.STATISTICS_ROUTE)
                },
                onOpenCalculators = {
                    navController.navigate(CalculatorsDestination.ROUTE)
                },
                onOpenCounters = {
                    navController.navigate(CountersDestination.ROUTE)
                }
            )
        }
        composable(ProjectDestination.STATISTICS_ROUTE) {
            val viewModel: StatisticsViewModel = viewModel(
                factory = StatisticsViewModel.factory(
                    projectRepository = projectRepository,
                    sessionRepository = sessionRepository,
                    materialsRepository = materialsRepository,
                    stashRepository = stashRepository
                )
            )
            StatisticsRoute(
                viewModel = viewModel,
                onShareSummary = { navController.navigate(ProjectDestination.SUMMARY_CARD_ROUTE) }
            )
        }
        composable(ProjectDestination.SUMMARY_CARD_ROUTE) {
            val viewModel: ShareCardViewModel = viewModel(
                factory = ShareCardViewModel.factory(
                    projectId = null,
                    projectRepository = projectRepository,
                    journalRepository = journalRepository,
                    sessionRepository = sessionRepository,
                    materialsRepository = materialsRepository,
                    stashRepository = stashRepository
                )
            )
            ShareCardRoute(viewModel = viewModel)
        }
        composable(
            route = ProjectDestination.ASSISTED_IMPORT_ROUTE,
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
            val viewModel: AssistedImportViewModel = viewModel(
                factory = AssistedImportViewModel.factory(
                    projectId = projectId,
                    projectRepository = projectRepository,
                    textExtractor = application.container.pdfTextExtractor,
                    decoder = application.container.structuredGuideDecoder,
                    createGuide = application.container.createGuideFromStructuredGuideUseCase
                )
            )
            AssistedImportRoute(
                viewModel = viewModel,
                onDraftCreated = { guideId ->
                    // The import screen has done its job; Back from the editor returns to the project.
                    navController.navigate(DraftEditorDestination.route(guideId, projectId)) {
                        popUpTo(ProjectDestination.ASSISTED_IMPORT_ROUTE) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = ProjectDestination.CARD_ROUTE,
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
            val viewModel: ShareCardViewModel = viewModel(
                factory = ShareCardViewModel.factory(
                    projectId = projectId,
                    projectRepository = projectRepository,
                    journalRepository = journalRepository,
                    sessionRepository = sessionRepository,
                    materialsRepository = materialsRepository,
                    stashRepository = stashRepository
                )
            )
            ShareCardRoute(viewModel = viewModel)
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
                factory = LibraryViewModel.factory(
                    libraryRepository,
                    application.container.patternFolder,
                    application.container.syncPatternFolder
                )
            )
            LibraryRoute(
                viewModel = viewModel,
                onOpenGuides = { libraryItemId ->
                    navController.navigate(PatternGuidesDestination.route(libraryItemId))
                },
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
                },
                navArgument(PdfViewerDestination.PAGE_ARGUMENT) {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val libraryItemId = backStackEntry.arguments?.getString(
                PdfViewerDestination.LIBRARY_ITEM_ID_ARGUMENT
            )
                .orEmpty()
            val startPage = backStackEntry.arguments?.getInt(PdfViewerDestination.PAGE_ARGUMENT)?.takeIf { it >= 1 }
            val viewModel: PdfViewerViewModel = viewModel(
                factory = PdfViewerViewModel.factory(
                    libraryItemId = libraryItemId,
                    repository = libraryRepository
                )
            )
            PdfViewerRoute(viewModel = viewModel, startPage = startPage)
        }
        composable(
            route = PatternGuidesDestination.ROUTE,
            arguments = listOf(
                navArgument(PdfViewerDestination.LIBRARY_ITEM_ID_ARGUMENT) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val libraryItemId = backStackEntry.arguments?.getString(PdfViewerDestination.LIBRARY_ITEM_ID_ARGUMENT).orEmpty()
            val viewModel: PatternGuidesViewModel = viewModel(
                factory = PatternGuidesViewModel.factory(
                    libraryItemId,
                    libraryRepository,
                    guideRepository,
                    createGuideFromPdfUseCase,
                    readPatternFile = { uri ->
                        withContext(Dispatchers.IO) {
                            runCatching {
                                application.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
                            }.getOrNull()
                        }
                    },
                    projectRepository = projectRepository,
                    materialsRepository = materialsRepository
                )
            )
            PatternGuidesRoute(
                viewModel = viewModel,
                onOpenPdf = { navController.navigate(PdfViewerDestination.route(it)) },
                onEditGuide = { navController.navigate(DraftEditorDestination.route(it)) },
                onOpenProject = { navController.navigate(ProjectDestination.detailRoute(it)) }
            )
        }
        composable(TopLevelDestination.Stash.route) {
            val stashViewModel: StashViewModel = viewModel(
                factory = StashViewModel.factory(stashRepository, materialsRepository, journalRepository)
            )
            val toolsViewModel: ToolsViewModel = viewModel(
                factory = ToolsViewModel.factory(toolRepository, projectRepository)
            )
            StashHost(
                yarn = { StashRoute(viewModel = stashViewModel) },
                tools = {
                    ToolsRoute(
                        viewModel = toolsViewModel,
                        onBulkCreate = {
                            navController.navigate(BulkToolCreationDestination.ROUTE)
                        },
                        onManageSets = {
                            navController.navigate(ToolSetsDestination.ROUTE)
                        }
                    )
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
        composable(ToolSetsDestination.ROUTE) {
            val viewModel: ToolSetsViewModel = viewModel(
                factory = ToolSetsViewModel.factory(toolRepository)
            )
            ToolSetsRoute(viewModel = viewModel)
        }
        composable(CalculatorsDestination.ROUTE) {
            CalculatorsScreen()
        }
        composable(CountersDestination.ROUTE) {
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
                factory = SettingsViewModel.factory(backupService, userPreferencesRepository)
            )
            val ravelryViewModel: RavelryViewModel = viewModel(
                factory = RavelryViewModel.factory(application.container.ravelryCredentialStore, application.container.ravelrySync)
            )
            SettingsRoute(viewModel = viewModel, ravelrySection = { RavelryRoute(ravelryViewModel) })
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
                    toolRepository = toolRepository,
                    createGuideFromPdfUseCase = createGuideFromPdfUseCase,
                    backupService = backupService,
                    counterRepository = counterRepository,
                    materialsRepository = materialsRepository,
                    journalRepository = journalRepository,
                    sessionRepository = sessionRepository
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
                    navController.navigate(GuideFocusDestination.route(guideId, projectId))
                },
                onEditDraft = { guideId ->
                    navController.navigate(DraftEditorDestination.route(guideId, projectId))
                },
                onOpenSection = { section ->
                    navController.navigate(section.route(projectId))
                }
            )
        }
        composable(
            route = ProjectDestination.MATERIALS_ROUTE,
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
            val viewModel: ProjectMaterialsViewModel = viewModel(
                factory = ProjectMaterialsViewModel.factory(
                    projectId = projectId,
                    projectRepository = projectRepository,
                    stashRepository = stashRepository,
                    libraryRepository = libraryRepository,
                    materialsRepository = materialsRepository
                )
            )
            ProjectMaterialsRoute(viewModel = viewModel)
        }
        composable(
            route = ProjectDestination.JOURNAL_ROUTE,
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
            val viewModel: ProjectJournalViewModel = viewModel(
                factory = ProjectJournalViewModel.factory(
                    projectId = projectId,
                    projectRepository = projectRepository,
                    journalRepository = journalRepository
                )
            )
            ProjectJournalRoute(viewModel = viewModel)
        }
        composable(
            route = ProjectDestination.SESSIONS_ROUTE,
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
            val viewModel: ProjectSessionsViewModel = viewModel(
                factory = ProjectSessionsViewModel.factory(
                    projectId = projectId,
                    projectRepository = projectRepository,
                    sessionRepository = sessionRepository
                )
            )
            ProjectSessionsRoute(viewModel = viewModel)
        }
        composable(
            route = DraftEditorDestination.ROUTE,
            arguments = listOf(
                navArgument(DraftEditorDestination.GUIDE_ID_ARGUMENT) {
                    type = NavType.StringType
                },
                navArgument(DraftEditorDestination.PROJECT_ID_ARGUMENT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val guideId = backStackEntry.arguments?.getString(
                DraftEditorDestination.GUIDE_ID_ARGUMENT
            )
                .orEmpty()
            val projectId = backStackEntry.arguments?.getString(DraftEditorDestination.PROJECT_ID_ARGUMENT)
            val viewModel: DraftEditorViewModel = viewModel(
                factory = DraftEditorViewModel.factory(
                    guideId = GuideId(guideId),
                    guideRepository = guideRepository,
                    executionRepository = executionRepository,
                    projectId = projectId
                )
            )
            DraftEditorRoute(
                viewModel = viewModel,
                onDone = navController::popBackStack,
                onStartOrContinue = {
                    navController.navigate(GuideFocusDestination.route(guideId, projectId))
                }
            )
        }
        composable(
            route = GuideFocusDestination.ROUTE,
            arguments = listOf(
                navArgument(GuideFocusDestination.GUIDE_ID_ARGUMENT) {
                    type = NavType.StringType
                },
                navArgument(GuideFocusDestination.PROJECT_ID_ARGUMENT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val guideId = backStackEntry.arguments?.getString(
                GuideFocusDestination.GUIDE_ID_ARGUMENT
            )
                .orEmpty()
            val projectId = backStackEntry.arguments?.getString(GuideFocusDestination.PROJECT_ID_ARGUMENT)
            val viewModel: GuideFocusViewModel = viewModel(
                factory = GuideFocusViewModel.factory(
                    guideId = GuideId(guideId),
                    guideRepository = guideRepository,
                    executionRepository = executionRepository,
                    counterRepository = counterRepository,
                    projectId = projectId,
                    materialsRepository = materialsRepository,
                    sessionRepository = sessionRepository,
                    projectRepository = projectRepository
                )
            )
            GuideFocusRoute(
                viewModel = viewModel,
                onOpenPattern = { pattern ->
                    navController.navigate(PdfViewerDestination.route(pattern.libraryItemId, pattern.page))
                }
            )
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
