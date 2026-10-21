package com.macareen.stitchbook2.feature.library

import com.macareen.stitchbook2.domain.model.Project
import com.macareen.stitchbook2.domain.model.ProjectPatternLink
import com.macareen.stitchbook2.domain.model.YarnAllocation
import com.macareen.stitchbook2.domain.model.StashItem
import com.macareen.stitchbook2.domain.repository.MaterialsRepository
import com.macareen.stitchbook2.domain.model.ProjectStatus
import com.macareen.stitchbook2.domain.model.ProjectType
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.GuideDefinition
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.DefinitionRevision
import com.macareen.stitchbook2.domain.guide.DraftId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.guide.GuideDraft
import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.PatternPdfChangeResult
import com.macareen.stitchbook2.domain.model.PickedDocument
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.parsing.ExtractedDocument
import com.macareen.stitchbook2.domain.parsing.ExtractedLine
import com.macareen.stitchbook2.domain.parsing.PdfTextExtractor
import com.macareen.stitchbook2.domain.parsing.SourceReference
import com.macareen.stitchbook2.domain.repository.GuideRepository
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.usecase.CreateGuideFromPdfUseCase
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternGuidesViewModelTest {
    @Test fun creatingFromCurrentPdfSelectionOpensAnEditableSizeFilteredDraftWithoutPreviewGate() = runBlocking {
        val guides = FakePatternGuides()
        val document = ExtractedDocument(1, listOf(
            ExtractedLine("Sizes: S (M)", SourceReference(1, 1)),
            ExtractedLine("Cast on 60 (66) sts.", SourceReference(1, 2))
        ))
        val model = PatternGuidesViewModel("pattern",
            FakeLibrary(listOf(pattern.copy(pdfUri = "content://pattern.pdf", sizes = "S, M"))), guides, scope,
            createGuideFromPdf = CreateGuideFromPdfUseCase(FixedText(document), guides, { "n" }),
            readPatternFile = { ByteArray(1) })
        val collector = scope.launch { model.uiState.collect {} }
        withTimeout(5_000) { model.uiState.first { it is PatternGuidesUiState.Content } }
        model.createGuide("M", fromPattern = true)
        withTimeout(5_000) { model.pdfSourceReview.first { it != null } }

        model.createDraftFromSelection()
        val createdId = withTimeout(5_000) { model.createdGuideId.first { it != null } }

        assertEquals("created-1", createdId)
        assertEquals("Cast on 66 sts. (p.1)", guides.savedDraft?.nodes?.single()?.instructionText)
        collector.cancel()
    }

    @Test fun sourceEditsInvalidatePreviewAndSavedPatternChangesRefuseCreation() = runBlocking {
        val guides = FakePatternGuides()
        val library = FakeLibrary(listOf(pattern.copy(pdfUri = "content://pattern.pdf", sizes = "S, M")))
        val document = ExtractedDocument(2, listOf(ExtractedLine("Cast on 60 (66) sts.", SourceReference(1, 1)),
            ExtractedLine("Bind off.", SourceReference(2, 1))))
        val model = PatternGuidesViewModel("pattern", library, guides, scope,
            createGuideFromPdf = CreateGuideFromPdfUseCase(FixedText(document), guides, { "n" }), readPatternFile = { ByteArray(1) })
        val collector = scope.launch { model.uiState.collect {} }
        withTimeout(5_000) { model.uiState.first { it is PatternGuidesUiState.Content } }
        model.createGuide("M", true)
        withTimeout(5_000) { model.pdfSourceReview.first { it != null } }
        model.confirmSourceReview()
        assertTrue(guides.created.isEmpty())
        model.previewSource()
        withTimeout(5_000) { model.pdfSourceReview.first { it?.preview != null && !it.busy } }
        model.changeSourceRegions(listOf(com.macareen.stitchbook2.domain.parsing.PdfSourceRegion(1)))
        assertEquals(null, model.pdfSourceReview.value!!.preview)
        model.confirmSourceReview()
        assertTrue(guides.created.isEmpty())
        model.previewSource()
        withTimeout(5_000) { model.pdfSourceReview.first { it?.preview != null && !it.busy } }
        library.saveLibraryItem(pattern.copy(pdfUri = "content://changed.pdf"))
        model.confirmSourceReview()
        withTimeout(5_000) { model.pdfSourceReview.first { it?.changed == true } }
        assertTrue(guides.created.isEmpty())
        model.dismissSourceReview()
        assertEquals(null, model.pdfSourceReview.value)
        collector.cancel()
    }

    @Test fun overlappingSelectionRetainsTheReviewAndCreatesNothing() = runBlocking {
        val guides = FakePatternGuides()
        val model = PatternGuidesViewModel("pattern", FakeLibrary(listOf(pattern.copy(pdfUri = "content://pattern.pdf"))), guides, scope,
            createGuideFromPdf = CreateGuideFromPdfUseCase(FixedText(ExtractedDocument(1,
                listOf(ExtractedLine("Bind off.", SourceReference(1, 1))))), guides, { "n" }), readPatternFile = { ByteArray(1) })
        val collector = scope.launch { model.uiState.collect {} }
        withTimeout(5_000) { model.uiState.first { it is PatternGuidesUiState.Content } }
        model.createGuide("M", true)
        withTimeout(5_000) { model.pdfSourceReview.first { it != null } }
        val region = com.macareen.stitchbook2.domain.parsing.PdfSourceRegion(1)
        model.changeSourceRegions(listOf(region, region))
        model.previewSource()
        withTimeout(5_000) { model.pdfSourceReview.first { it?.problem != null && !it.busy } }
        assertEquals(listOf(region, region), model.pdfSourceReview.value!!.regions)
        assertTrue(guides.created.isEmpty())
        collector.cancel()
    }

    @Test fun archivingAndRestoringMoveTheSameGuideBetweenLists() {
        val guides = FakePatternGuides()
        guides.add(guide("m", "M"), true)
        val model = viewModel(guides)
        model.setGuideArchived("m", true)
        val archived = model.uiState.value as PatternGuidesUiState.Content
        assertTrue(archived.guides.isEmpty())
        assertEquals("m", archived.archivedGuides.single().guide.id.value)
        assertTrue(archived.archivedGuides.single().isPublished)
        model.setGuideArchived("m", false)
        val restored = model.uiState.value as PatternGuidesUiState.Content
        assertEquals("m", restored.guides.single().guide.id.value)
        assertTrue(restored.archivedGuides.isEmpty())
    }
    @Test fun refusedArchiveLeavesTheGuideVisible() {
        val guides = FakePatternGuides()
        guides.add(guide("m", "M"), false)
        guides.allowArchive = false
        val model = viewModel(guides)
        model.setGuideArchived("m", true)
        val content = model.uiState.value as PatternGuidesUiState.Content
        assertEquals(1, content.guides.size)
        assertTrue(content.guideArchiveFailed)
    }

    @Test fun removesAnUnusedGuideFromTheCurrentPattern() {
        val guides = FakePatternGuides()
        guides.add(guide("m", "M"), false)
        val model = viewModel(guides)
        model.removeUnusedGuide("m")
        val state = model.uiState.value as PatternGuidesUiState.Content
        assertTrue(state.guides.isEmpty())
        assertTrue(!state.isCreating && !state.guideRemovalFailed)
    }

    @Test fun refusedRemovalKeepsTheGuideAndReportsFailure() {
        val guides = FakePatternGuides()
        guides.add(guide("m", "M"), true)
        guides.allowRemoval = false
        val model = viewModel(guides)
        model.removeUnusedGuide("m")
        val state = model.uiState.value as PatternGuidesUiState.Content
        assertEquals(1, state.guides.size)
        assertTrue(state.guideRemovalFailed && !state.isCreating)
    }


    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val pattern = LibraryItem(
        id = "pattern",
        title = "Seaside Cardigan",
        craft = Craft.KNITTING,
        author = null,
        sourceUrl = null,
        tags = emptyList(),
        notes = null,
        bookmarked = false,
        createdAt = 0,
        updatedAt = 0
    )

    @Test
    fun showsThePatternsGuidesBySizeAndWhetherTheyAreReady() {
        val guides = FakePatternGuides()
        guides.add(guide("m", "M"), published = true)
        guides.add(guide("l", "L"), published = false)
        val viewModel = viewModel(guides)

        val content = viewModel.uiState.value as PatternGuidesUiState.Content
        assertEquals(listOf("M" to true, "L" to false), content.guides.map { it.guide.sizeLabel to it.isPublished })
    }

    @Test
    fun aNewSizeGuideIsNamedAfterThePatternAndOpensForEditing() {
        val guides = FakePatternGuides()
        val viewModel = viewModel(guides)

        viewModel.createGuide(" S ")

        assertEquals("Seaside Cardigan · S", guides.created.single().name)
        assertEquals("created-1", viewModel.createdGuideId.value)
        viewModel.consumeCreatedGuide()
        assertEquals(null, viewModel.createdGuideId.value)
    }

    @Test
    fun aGuideFilledFromThePatternReadsItsPdfForThatSize() {
        val guides = FakePatternGuides()
        val read = mutableListOf<String>()
        val document = ExtractedDocument(1, listOf(ExtractedLine("Cast on 60 (66) sts.", SourceReference(1, 1))))
        val viewModel = PatternGuidesViewModel(
            "pattern",
            FakeLibrary(listOf(pattern.copy(pdfUri = "content://pattern.pdf", sizes = "S, M"))),
            guides,
            scope,
            createGuideFromPdf = CreateGuideFromPdfUseCase(FixedText(document), guides, newNodeId = { "n" }),
            readPatternFile = { uri -> read += uri; ByteArray(1) }
        ).also { vm -> scope.launch { vm.uiState.collect {} } }

        assertEquals(listOf("S", "M"), (viewModel.uiState.value as PatternGuidesUiState.Content).sizeChoices)
        viewModel.createGuide("M", fromPattern = true)

        runBlocking { withTimeout(5_000) { viewModel.pdfSourceReview.first { it != null } } }
        assertTrue(guides.created.isEmpty())
        viewModel.previewSource()
        runBlocking { withTimeout(5_000) { viewModel.pdfSourceReview.first { it?.preview != null && !it.busy } } }
        assertTrue(guides.created.isEmpty())
        viewModel.confirmSourceReview()
        val createdId = runBlocking { withTimeout(5_000) { viewModel.createdGuideId.first { it != null } } }

        assertEquals(listOf("content://pattern.pdf", "content://pattern.pdf"), read)
        assertEquals("created-1", createdId)
        assertEquals("Cast on 66 sts. (p.1)", guides.savedDraft?.nodes?.single()?.instructionText)
    }

    @Test
    fun pdfSizeChoicesAppearBeforeAnyGuideExistsAndOverrideSavedMeasurements() = runBlocking {
        val guides = FakePatternGuides()
        val document = ExtractedDocument(1, listOf(
            ExtractedLine("Sizes: XS (S) M (L) XL", SourceReference(1, 1)),
            ExtractedLine("Cast on 60 (66) 72 (78) 84 sts.", SourceReference(1, 2))
        ))
        val viewModel = PatternGuidesViewModel(
            "pattern", FakeLibrary(listOf(pattern.copy(pdfUri = "content://pattern.pdf", sizes = "91, 97, 101"))),
            guides, scope,
            createGuideFromPdf = CreateGuideFromPdfUseCase(FixedText(document), guides, newNodeId = { "n" }),
            readPatternFile = { ByteArray(1) }
        )
        val content = withTimeout(5_000) {
            viewModel.uiState.first { it is PatternGuidesUiState.Content && it.sizeChoices == listOf("XS", "S", "M", "L", "XL") }
        } as PatternGuidesUiState.Content
        assertTrue(content.guides.isEmpty())
        assertTrue(guides.created.isEmpty())
    }

    @Test
    fun replacingOrRemovingThePdfDoesNotRetainOldChoicesAndMetadataEditsDoNotRereadIt() = runBlocking {
        val library = FakeLibrary(listOf(pattern.copy(pdfUri = "content://first.pdf")))
        val reads = mutableListOf<String>()
        val extractor = object : PdfTextExtractor {
            override suspend fun extract(input: InputStream): ExtractedDocument {
                val sizes = if (input.read() == 1) "Sizes: XS (S) M" else "Sizes: L (XL) 2XL"
                return ExtractedDocument(1, listOf(ExtractedLine(sizes, SourceReference(1, 1))))
            }
        }
        val viewModel = PatternGuidesViewModel("pattern", library, FakePatternGuides(), scope,
            createGuideFromPdf = CreateGuideFromPdfUseCase(extractor, FakePatternGuides(), newNodeId = { "n" }),
            readPatternFile = { uri -> reads += uri; byteArrayOf(if (uri.endsWith("first.pdf")) 1 else 2) })
        suspend fun awaitChoices(expected: List<String>) = withTimeout(5_000) {
            viewModel.uiState.first { it is PatternGuidesUiState.Content && it.sizeChoices == expected }
        }
        awaitChoices(listOf("XS", "S", "M"))
        library.saveLibraryItem(pattern.copy(pdfUri = "content://first.pdf", notes = "Edited"))
        library.saveLibraryItem(pattern.copy(pdfUri = "content://second.pdf"))
        awaitChoices(listOf("L", "XL", "2XL"))
        library.saveLibraryItem(pattern.copy(sizes = "S, M"))
        awaitChoices(listOf("S", "M"))
        assertEquals(listOf("content://first.pdf", "content://second.pdf"), reads)
    }

    @Test
    fun aPatternFileThatCantBeOpenedSaysSoAndCreatesNothing() {
        val guides = FakePatternGuides()
        val viewModel = PatternGuidesViewModel(
            "pattern",
            FakeLibrary(listOf(pattern.copy(pdfUri = "content://gone.pdf"))),
            guides,
            scope,
            createGuideFromPdf = CreateGuideFromPdfUseCase(FixedText(ExtractedDocument(0, emptyList())), guides, newNodeId = { "n" }),
            readPatternFile = { null }
        ).also { vm -> scope.launch { vm.uiState.collect {} } }

        viewModel.createGuide("M", fromPattern = true)

        assertEquals(PatternPdfProblem.UNREADABLE, (viewModel.uiState.value as PatternGuidesUiState.Content).pdfProblem)
        assertTrue(guides.created.isEmpty())
        viewModel.attachPdf(PickedDocument("content://replacement.pdf", "replacement.pdf"))
        viewModel.confirmPdfChange(viewModel.pdfChangeReview.value!!)
        assertEquals(null, (viewModel.uiState.value as PatternGuidesUiState.Content).pdfProblem)
    }

    @Test
    fun aMissingPatternSaysSo() {
        val viewModel = PatternGuidesViewModel("gone", FakeLibrary(emptyList()), FakePatternGuides(), scope)
        scope.launch { viewModel.uiState.collect {} }

        assertTrue(viewModel.uiState.value is PatternGuidesUiState.Missing)
    }

    @Test
    fun startingAProjectFillsItFromThePatternAndOpensIt() {
        val projects = SavedProjects()
        val viewModel = PatternGuidesViewModel(
            "pattern",
            FakeLibrary(listOf(pattern.copy(notes = "A relaxed cardigan."))),
            FakePatternGuides(),
            scope,
            projectRepository = projects,
            newId = { "project-1" },
            clock = { 7L }
        )

        viewModel.startProject()

        val project = projects.saved.single()
        assertEquals("Seaside Cardigan", project.name)
        assertEquals(ProjectType.CARDIGAN, project.projectType)
        assertEquals(ProjectStatus.PLANNED, project.status)
        assertEquals("A relaxed cardigan.", project.description)
        assertEquals("project-1", viewModel.startedProjectId.value)
    }

    @Test fun completedProjectCountUpdatesWithRecordedStatusAndLinks() = runBlocking<Unit> {
        val projects = LiveProjects()
        val materials = LivePatternLinks()
        val viewModel = PatternGuidesViewModel("pattern", FakeLibrary(listOf(pattern)), FakePatternGuides(), scope,
            projectRepository = projects, materialsRepository = materials)
        suspend fun awaitCount(expected: Int) = withTimeout(5_000) {
            viewModel.uiState.first { it is PatternGuidesUiState.Content && it.completedProjects == expected }
        }
        val project = Project("one", "One", Craft.LOOM_KNITTING, ProjectType.CARDIGAN, ProjectStatus.ACTIVE, null, 0, 0)
        projects.saveProject(project)
        materials.links.value = listOf(ProjectPatternLink("one", "pattern"))
        awaitCount(0)
        projects.saveProject(project.copy(status = ProjectStatus.COMPLETED))
        awaitCount(1)
        materials.links.value = emptyList()
        awaitCount(0)
        materials.links.value = listOf(ProjectPatternLink("one", "pattern"))
        awaitCount(1)
        projects.deleteProject(project)
        awaitCount(0)
    }

    @Test fun attachingAnOriginalPdfPreservesCustomMetadataAndExistingGuides() {
        val original = pattern.copy(title = "My chosen name", notes = "Keep this", pdfLastViewedPage = 4)
        val library = FakeLibrary(listOf(original))
        val guides = FakePatternGuides().also { it.add(guide("existing", "M"), published = true) }
        val vm = PatternGuidesViewModel("pattern", library, guides, scope, clock = { 77 })
        scope.launch { vm.uiState.collect {} }
        vm.attachPdf(com.macareen.stitchbook2.domain.model.PickedDocument("content://original", "random.pdf"))
        assertEquals(original, (vm.uiState.value as PatternGuidesUiState.Content).pattern)
        vm.confirmPdfChange(vm.pdfChangeReview.value!!)
        val state = vm.uiState.value as PatternGuidesUiState.Content
        assertEquals(original.copy(pdfUri = "content://original", pdfFileName = "random.pdf", pdfLastViewedPage = null, updatedAt = 77), state.pattern)
        assertEquals("existing", state.guides.single().guide.id.value)
        assertTrue(state.guides.single().isPublished)
        assertTrue(!state.isCreating)
    }

    @Test fun changedPatternRequiresFreshReviewAndStaleCallbacksCannotSave() = runBlocking<Unit> {
        val original = pattern.copy(pdfUri = "content://old", pdfFileName = "old.pdf", pdfLastViewedPage = 5)
        val library = FakeLibrary(listOf(original))
        val vm = PatternGuidesViewModel("pattern", library, FakePatternGuides(), scope)
        scope.launch { vm.uiState.collect {} }
        vm.attachPdf(PickedDocument("content://new", "new.pdf"))
        val first = vm.pdfChangeReview.value!!
        val changed = original.copy(notes = "Concurrent edit", pdfUri = "content://other", pdfFileName = "other.pdf")
        library.saveLibraryItem(changed)
        vm.confirmPdfChange(first)
        val refreshed = vm.pdfChangeReview.value!!
        assertEquals(PdfChangeProblem.CHANGED, refreshed.problem)
        assertEquals(changed, refreshed.expected)
        vm.confirmPdfChange(first)
        assertEquals(changed, library.observeLibraryItem("pattern").first())
        vm.confirmPdfChange(refreshed)
        assertEquals("Concurrent edit", library.observeLibraryItem("pattern").first()!!.notes)
        assertEquals("content://new", library.observeLibraryItem("pattern").first()!!.pdfUri)
        assertEquals(null, vm.pdfChangeReview.value)
    }

    @Test fun failedSaveKeepsSelectionAndBusySaveRejectsDismissAndDuplicate() {
        val library = FakeLibrary(listOf(pattern))
        val vm = PatternGuidesViewModel("pattern", library, FakePatternGuides(), scope)
        scope.launch { vm.uiState.collect {} }
        vm.attachPdf(PickedDocument("content://new", "new.pdf"))
        library.failPdf = true
        vm.confirmPdfChange(vm.pdfChangeReview.value!!)
        assertEquals(PdfChangeProblem.FAILED, vm.pdfChangeReview.value!!.problem)
        library.failPdf = false
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        library.pdfGate = gate
        val retry = vm.pdfChangeReview.value!!
        vm.confirmPdfChange(retry)
        vm.confirmPdfChange(retry)
        vm.dismissPdfChange()
        vm.requestPdfChange(null)
        assertTrue(vm.pdfChangeReview.value!!.isSaving)
        assertEquals(2, library.pdfCalls)
        gate.complete(Unit)
        assertEquals(null, vm.pdfChangeReview.value)
    }

    @Test fun unlinkRequiresConfirmationAndCanceledReviewCannotBeReused() {
        val original = pattern.copy(pdfUri = "content://old", pdfFileName = "old.pdf", pdfLastViewedPage = 4)
        val library = FakeLibrary(listOf(original))
        val vm = PatternGuidesViewModel("pattern", library, FakePatternGuides(), scope, clock = { 99 })
        scope.launch { vm.uiState.collect {} }
        vm.requestPdfChange(null)
        val canceled = vm.pdfChangeReview.value!!
        vm.dismissPdfChange()
        vm.requestPdfChange(null)
        vm.confirmPdfChange(canceled)
        assertEquals(original, (vm.uiState.value as PatternGuidesUiState.Content).pattern)
        vm.confirmPdfChange(vm.pdfChangeReview.value!!)
        assertEquals(original.copy(pdfUri = null, pdfFileName = null, pdfLastViewedPage = null, updatedAt = 99),
            (vm.uiState.value as PatternGuidesUiState.Content).pattern)
    }

    @Test fun missingPatternCannotBeRecreatedByRetryingPdfReview() = runBlocking<Unit> {
        val library = FakeLibrary(listOf(pattern))
        val vm = PatternGuidesViewModel("pattern", library, FakePatternGuides(), scope)
        scope.launch { vm.uiState.collect {} }
        vm.attachPdf(PickedDocument("content://new", "new.pdf"))
        library.deleteLibraryItem(pattern)
        vm.confirmPdfChange(vm.pdfChangeReview.value!!)
        assertEquals(PdfChangeProblem.MISSING, vm.pdfChangeReview.value!!.problem)
        vm.confirmPdfChange(vm.pdfChangeReview.value!!)
        assertEquals(1, library.pdfCalls)
        assertEquals(null, library.observeLibraryItem("pattern").first())
    }

    @Test fun editingPatternDetailsPreservesDocumentIdentityBookmarksAndGuideWork() {
        val original = pattern.copy(bookmarked = true, pdfUri = "content://original", pdfFileName = "original.pdf", pdfLastViewedPage = 7)
        val library = FakeLibrary(listOf(original))
        val guides = FakePatternGuides().also { it.add(guide("ready", "M"), true) }
        val vm = PatternGuidesViewModel("pattern", library, guides, scope, clock = { 50 })
        scope.launch { vm.uiState.collect {} }
        vm.editPattern()
        val review = vm.patternEditReview.value!!
        vm.savePatternEdit(review, " New title ", Craft.LOOM_KNITTING, " Designer ", " https://example.com ",
            listOf(" soft ", "", "blue,green"), " My note ", PatternMetadataInput(" 12 passes ", " M/L ", "123,5", " Loom ", " 123 "))
        val actual = (vm.uiState.value as PatternGuidesUiState.Content).pattern
        assertEquals(original.copy(title = "New title", craft = Craft.LOOM_KNITTING, author = "Designer", sourceUrl = "https://example.com",
            tags = listOf("soft", "bluegreen"), notes = "My note", gauge = "12 passes", sizes = "M/L", yardageRequired = 123.5,
            recommendedTools = "Loom", ravelryPatternId = "123", updatedAt = 50), actual)
        assertEquals("ready", (vm.uiState.value as PatternGuidesUiState.Content).guides.single().guide.id.value)
        assertEquals(null, vm.patternEditReview.value)
    }

    @Test fun concurrentPdfChangeRequiresReloadAndRejectsOldEditCallbacks() = runBlocking<Unit> {
        val library = FakeLibrary(listOf(pattern))
        val vm = PatternGuidesViewModel("pattern", library, FakePatternGuides(), scope)
        scope.launch { vm.uiState.collect {} }
        vm.editPattern()
        val initial = vm.patternEditReview.value!!
        val changed = pattern.copy(pdfUri = "content://elsewhere", pdfFileName = "elsewhere.pdf")
        library.saveLibraryItem(changed)
        saveEdit(vm, initial)
        assertEquals(PatternEditProblem.CHANGED, vm.patternEditReview.value!!.problem)
        saveEdit(vm, vm.patternEditReview.value!!)
        assertEquals(1, library.editCalls)
        vm.reloadPatternEdit(vm.patternEditReview.value!!)
        val reloaded = vm.patternEditReview.value!!
        assertEquals(changed, reloaded.expected)
        assertTrue(initial.requestId != reloaded.requestId)
        saveEdit(vm, initial)
        assertEquals(1, library.editCalls)
        saveEdit(vm, reloaded)
        val saved = library.observeLibraryItem("pattern").first()!!
        assertEquals("Edited", saved.title)
        assertEquals("content://elsewhere", saved.pdfUri)
    }

    @Test fun editFailureCanRetryAndBusyFormRejectsDismissPdfChangesAndDuplicateSaves() {
        val library = FakeLibrary(listOf(pattern))
        val vm = PatternGuidesViewModel("pattern", library, FakePatternGuides(), scope)
        scope.launch { vm.uiState.collect {} }
        vm.editPattern()
        library.failEdit = true
        saveEdit(vm, vm.patternEditReview.value!!)
        assertEquals(PatternEditProblem.FAILED, vm.patternEditReview.value!!.problem)
        library.failEdit = false
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        library.editGate = gate
        val retry = vm.patternEditReview.value!!
        saveEdit(vm, retry)
        saveEdit(vm, retry)
        vm.dismissPatternEdit()
        vm.attachPdf(PickedDocument("content://new", "new.pdf"))
        assertEquals(null, vm.pdfChangeReview.value)
        assertTrue(vm.patternEditReview.value!!.isSaving)
        assertEquals(2, library.editCalls)
        gate.complete(Unit)
        assertEquals(null, vm.patternEditReview.value)
    }

    @Test fun invalidTitleAndNonfiniteOrNegativeYardageCannotSave() {
        val library = FakeLibrary(listOf(pattern))
        val vm = PatternGuidesViewModel("pattern", library, FakePatternGuides(), scope)
        scope.launch { vm.uiState.collect {} }
        vm.editPattern()
        val review = vm.patternEditReview.value!!
        saveEdit(vm, review, title = " ")
        for (invalid in listOf("NaN", "Infinity", "-5", "0", "abc")) saveEdit(vm, review, metadata = PatternMetadataInput(yardageRequiredText = invalid))
        assertEquals(0, library.editCalls)
        vm.dismissPatternEdit()
        saveEdit(vm, review)
        assertEquals(0, library.editCalls)
    }

    private fun saveEdit(vm: PatternGuidesViewModel, review: PatternEditReview, title: String = "Edited",
        metadata: PatternMetadataInput = PatternMetadataInput()) =
        vm.savePatternEdit(review, title, Craft.CROCHET, "", "", emptyList(), "", metadata)

    @Test fun copyingRequiresPublishedSourceAndOpensNamedDraft() {
        val guides = FakePatternGuides().also {
            it.add(guide("ready", "M"), published = true)
            it.add(guide("draft", "S"), published = false)
        }
        val vm = viewModel(guides)
        vm.copyGuideToSize("draft", "L"); vm.copyGuideToSize("ready", " ")
        assertTrue(guides.copies.isEmpty())
        vm.copyGuideToSize("ready", " L ")
        assertEquals(Triple("ready", " L ", "Seaside Cardigan \u00b7 L"), guides.copies.single())
        assertEquals("created-1", vm.createdGuideId.value)
    }

    @Test fun failedCopyCanRetryAndBusyCopyRejectsDuplicateRequests() {
        val guides = FakePatternGuides().also { it.add(guide("ready", "M"), published = true); it.failCopy = true }
        val vm = viewModel(guides)
        vm.copyGuideToSize("ready", "L")
        assertTrue((vm.uiState.value as PatternGuidesUiState.Content).createFailed)
        assertEquals(null, vm.createdGuideId.value)
        guides.failCopy = false
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        guides.copyGate = gate
        vm.copyGuideToSize("ready", "L"); vm.copyGuideToSize("ready", "XL")
        assertEquals(2, guides.copies.size)
        gate.complete(Unit)
        assertEquals("created-1", vm.createdGuideId.value)
        assertTrue(!(vm.uiState.value as PatternGuidesUiState.Content).createFailed)
    }

    private fun viewModel(guides: FakePatternGuides): PatternGuidesViewModel =
        PatternGuidesViewModel("pattern", FakeLibrary(listOf(pattern)), guides, scope).also { vm ->
            scope.launch { vm.uiState.collect {} }
        }

    private fun guide(id: String, size: String) =
        Guide(GuideId(id), null, "Seaside Cardigan · $size", null, 0, 0, libraryItemId = "pattern", sizeLabel = size)
}

private class FakeLibrary(items: List<LibraryItem>) : LibraryRepository {
    private val flow = MutableStateFlow(items)
    override fun observeLibraryItems(): Flow<List<LibraryItem>> = flow
    override fun observeLibraryItem(id: String): Flow<LibraryItem?> = flow.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun saveLibraryItem(item: LibraryItem) {
        flow.value = flow.value.map { if (it.id == item.id) item else it }
    }
    var failEdit = false
    var editCalls = 0
    var editGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    override suspend fun saveIfUnchanged(expected: LibraryItem, replacement: LibraryItem): Boolean {
        editCalls++
        editGate?.await()
        if (failEdit) error("Test failure")
        if (flow.value.firstOrNull { it.id == expected.id } != expected) return false
        saveLibraryItem(replacement)
        return true
    }
    var failPdf = false
    var pdfCalls = 0
    var pdfGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    override suspend fun changePdfReviewed(expected: LibraryItem, document: PickedDocument?, now: Long): PatternPdfChangeResult {
        pdfCalls++
        pdfGate?.await()
        if (failPdf) error("Test failure")
        val current = flow.value.firstOrNull { it.id == expected.id } ?: return PatternPdfChangeResult.Missing
        if (current != expected) return PatternPdfChangeResult.Changed(current)
        saveLibraryItem(current.copy(pdfUri = document?.uri, pdfFileName = document?.displayName,
            pdfLastViewedPage = if (document != null && document.uri == current.pdfUri) current.pdfLastViewedPage else null, updatedAt = now))
        return PatternPdfChangeResult.Applied
    }
    override suspend fun deleteLibraryItem(item: LibraryItem) { flow.value = flow.value.filterNot { it.id == item.id } }
}

private class FakePatternGuides : GuideRepository {
    val copies = mutableListOf<Triple<String, String, String>>()
    var failCopy = false
    var copyGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    override suspend fun copyPatternGuideToSize(sourceGuideId: GuideId, targetSize: String, name: String): Guide {
        copies += Triple(sourceGuideId.value, targetSize, name)
        copyGate?.await()
        if (failCopy) error("Test failure")
        return createPatternGuide("pattern", targetSize.trim(), name)
    }
    private val guides = MutableStateFlow<List<Guide>>(emptyList())
    var allowArchive = true
    override fun observePatternGuidesIncludingArchived(libraryItemId: String): Flow<List<Guide>> =
        guides.map { list -> list.filter { it.libraryItemId == libraryItemId } }
    override suspend fun setPatternGuideArchived(guideId: GuideId, libraryItemId: String?, archived: Boolean): Boolean {
        val guide = guides.value.firstOrNull { it.id == guideId && it.libraryItemId == libraryItemId && it.projectId == null }
        if (!allowArchive || guide == null) return false
        guides.value = guides.value.map { if (it.id == guideId) it.copy(isArchived = archived) else it }
        return true
    }
    var allowRemoval = true
    override suspend fun deleteUnusedPatternGuide(guideId: GuideId, libraryItemId: String): Boolean {
        val target = guides.value.firstOrNull { it.id == guideId && it.libraryItemId == libraryItemId && it.projectId == null }
        if (!allowRemoval || target == null) return false
        guides.value = guides.value - target
        drafts.remove(guideId.value)
        published.remove(guideId.value)
        return true
    }
    private val published = mutableSetOf<String>()
    val created = mutableListOf<Guide>()
    private val drafts = mutableMapOf<String, GuideDraft>()
    var savedDraft: GuideDraft? = null
        private set

    fun add(guide: Guide, published: Boolean) {
        guides.value = guides.value + guide
        if (published) this.published += guide.id.value
    }

    override fun observePatternGuides(libraryItemId: String): Flow<List<Guide>> =
        guides.map { list -> list.filter { it.libraryItemId == libraryItemId && !it.isArchived } }

    override suspend fun createPatternGuide(libraryItemId: String, sizeLabel: String, name: String): Guide {
        val guide = Guide(GuideId("created-${created.size + 1}"), null, name, null, 0, 0, libraryItemId, sizeLabel.trim())
        created += guide
        guides.value = guides.value + guide
        drafts[guide.id.value] = GuideDraft(DraftId("d-${created.size}"), guide.id, null, 0, 0, 0, emptyList(), emptyList())
        return guide
    }

    override suspend fun getLatestRevision(guideId: GuideId): DefinitionRevision? =
        if (guideId.value in published) {
            DefinitionRevision(DefinitionRevisionId("r"), guideId, 1, 0, GuideDefinition(guideId, DefinitionRevisionId("r"), emptyList(), emptyList()))
        } else {
            null
        }

    override fun observeGuides(projectId: String): Flow<List<Guide>> = flowOf(emptyList())
    override suspend fun getGuide(guideId: GuideId): Guide? = unused()
    override suspend fun createGuide(projectId: String, name: String, notes: String?): Guide = unused()
    override suspend fun updateGuideMetadata(guideId: GuideId, name: String, notes: String?): Guide? =
        guides.value.firstOrNull { it.id == guideId }?.copy(name = name, notes = notes)?.also { updated ->
            guides.value = guides.value.map { if (it.id == guideId) updated else it }
        }
    override suspend fun deleteGuide(guideId: GuideId): Unit = unused()
    override suspend fun loadDraft(guideId: GuideId): GuideDraft? = drafts[guideId.value]
    override suspend fun saveDraft(draft: GuideDraft): GuideDraft = draft.also { savedDraft = it }
    override suspend fun createDraftFromLatestRevision(guideId: GuideId): GuideDraft = unused()
    override suspend fun listRevisions(guideId: GuideId): List<DefinitionRevision> = unused()
    override suspend fun loadRevision(revisionId: DefinitionRevisionId): DefinitionRevision? = unused()
    override suspend fun publishDraft(guideId: GuideId): DefinitionRevision = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("Not used by the pattern screen")
}

private class FixedText(private val document: ExtractedDocument) : PdfTextExtractor {
    override suspend fun extract(input: InputStream): ExtractedDocument = document
    override suspend fun extract(input: InputStream, selection: com.macareen.stitchbook2.domain.parsing.PdfSourceSelection): ExtractedDocument =
        document.copy(lines = selection.regions.flatMap { region -> document.lines.filter { it.source.pageNumber == region.page } })
}

private class SavedProjects : ProjectRepository {
    val saved = mutableListOf<Project>()
    override fun observeProjects(): Flow<List<Project>> = flowOf(saved.toList())
    override fun observeProject(id: String): Flow<Project?> = flowOf(saved.firstOrNull { it.id == id })
    override suspend fun saveProject(project: Project) {
        saved += project
    }
    override suspend fun deleteProject(project: Project) {
        saved -= project
    }
}

private class LiveProjects : ProjectRepository {
    val projects = MutableStateFlow<List<Project>>(emptyList())
    override fun observeProjects(): Flow<List<Project>> = projects
    override fun observeProject(id: String): Flow<Project?> = projects.map { list -> list.find { it.id == id } }
    override suspend fun saveProject(project: Project) { projects.value = projects.value.filterNot { it.id == project.id } + project }
    override suspend fun deleteProject(project: Project) { projects.value = projects.value.filterNot { it.id == project.id } }
}

private class LivePatternLinks : MaterialsRepository {
    val links = MutableStateFlow<List<ProjectPatternLink>>(emptyList())
    override fun observePatternLinks(): Flow<List<ProjectPatternLink>> = links
    override fun observeAllocations(): Flow<List<YarnAllocation>> = flowOf(emptyList())
    override fun observeAllocationsForProject(projectId: String): Flow<List<YarnAllocation>> = flowOf(emptyList())
    override fun observePatternsForProject(projectId: String): Flow<List<LibraryItem>> = flowOf(emptyList())
    override suspend fun linkPattern(link: ProjectPatternLink) { links.value += link }
    override suspend fun unlinkPattern(link: ProjectPatternLink) { links.value -= link }
    override suspend fun saveAllocation(allocation: YarnAllocation) = error("Unused")
    override suspend fun deleteAllocation(allocation: YarnAllocation) = error("Unused")
    override suspend fun recordConsumption(item: StashItem, allocation: YarnAllocation) = error("Unused")
}
