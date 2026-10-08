package com.macareen.stitchbook2.domain.ravelry

import com.macareen.stitchbook2.domain.repository.LibraryRepository
import com.macareen.stitchbook2.domain.repository.ProjectRepository
import com.macareen.stitchbook2.domain.repository.StashRepository
import com.macareen.stitchbook2.domain.repository.ToolRepository
import kotlinx.coroutines.flow.first

/**
 * A one-way pull from Ravelry into Stash, Tools, Projects, and Library, in two steps: [preview]
 * reads Ravelry and plans the changes without saving anything, and [apply]
 * saves exactly what the person approved. Nothing is ever deleted locally,
 * and nothing is written to Ravelry.
 */
class RavelrySync(
    private val api: RavelryApi,
    private val credentialStore: RavelryCredentialStore,
    private val stashRepository: StashRepository,
    private val toolRepository: ToolRepository,
    private val projectRepository: ProjectRepository,
    private val libraryRepository: LibraryRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {
    /** Checks the stored key and returns the Ravelry account name it belongs to. */
    suspend fun connectedUsername(): String = api.currentUsername(requireCredentials())

    suspend fun preview(): RavelryImportPlan {
        val credentials = requireCredentials()
        val username = api.currentUsername(credentials)
        val stash = api.stash(credentials, username)
        val needles = api.needles(credentials, username)
        val projects = api.projects(credentials, username)
        val library = api.library(credentials, username)
        return RavelryImportPlanner.plan(
            stash = stash,
            needles = needles,
            localStash = stashRepository.observeStashItems().first(),
            localTools = toolRepository.observeToolItems().first(),
            now = clock()
        ).copy(
            projects = RavelryImportPlanner.planProjects(projects, projectRepository.observeProjects().first(), clock()),
            patterns = RavelryImportPlanner.planPatterns(library, libraryRepository.observeLibraryItems().first(), clock())
        )
    }

    /** Saves new records, and the changed ones only when [includeChanges] is true. Returns how many were saved. */
    suspend fun apply(plan: RavelryImportPlan, includeChanges: Boolean): Int {
        val stash = plan.newStash + if (includeChanges) plan.changedStash.map { it.updated } else emptyList()
        val tools = plan.newTools + if (includeChanges) plan.changedTools.map { it.updated } else emptyList()
        val projects = plan.projects.toSave(includeChanges)
        val patterns = plan.patterns.toSave(includeChanges)
        stash.forEach { stashRepository.saveStashItem(it) }
        tools.forEach { toolRepository.saveToolItem(it) }
        projects.forEach { projectRepository.saveProject(it) }
        patterns.forEach { libraryRepository.saveLibraryItem(it) }
        return stash.size + tools.size + projects.size + patterns.size
    }

    private fun requireCredentials(): RavelryCredentials =
        credentialStore.load() ?: throw RavelryAuthException("No Ravelry key is saved on this device.")
}
