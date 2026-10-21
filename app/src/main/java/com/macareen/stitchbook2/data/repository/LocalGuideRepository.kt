package com.macareen.stitchbook2.data.repository

import com.macareen.stitchbook2.domain.execution.GuideDefinitionError
import com.macareen.stitchbook2.domain.repository.DraftProblem
import com.macareen.stitchbook2.data.database.DraftConflictException
import com.macareen.stitchbook2.data.database.DraftNotFoundException
import com.macareen.stitchbook2.data.database.GuideDao
import com.macareen.stitchbook2.data.database.GuideDraftEntity
import com.macareen.stitchbook2.data.database.GuideEntity
import com.macareen.stitchbook2.data.database.InvalidDraftForPublicationException
import com.macareen.stitchbook2.data.database.InvalidDraftTreeException
import com.macareen.stitchbook2.data.database.toDomain
import com.macareen.stitchbook2.data.database.toNodeEntities
import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.execution.InvalidGuideDefinitionException
import com.macareen.stitchbook2.domain.guide.DefinitionRevision
import com.macareen.stitchbook2.domain.guide.DraftId
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.guide.GuideDraft
import com.macareen.stitchbook2.domain.repository.DraftValidationException
import com.macareen.stitchbook2.domain.repository.DraftVersionConflictException
import com.macareen.stitchbook2.domain.repository.GuideRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalGuideRepository(
    private val guideDao: GuideDao,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : GuideRepository {

    override fun observeGuides(projectId: String): Flow<List<Guide>> {
        return guideDao.observeByProject(projectId).map { guides ->
            guides.map { it.toDomain() }
        }
    }

    override suspend fun getGuide(guideId: GuideId): Guide? {
        return guideDao.getGuide(guideId.value)?.toDomain()
    }

    override suspend fun createGuide(
        projectId: String,
        name: String,
        notes: String?
    ): Guide {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Guide name must not be blank." }
        val now = currentTimeMillis()
        val guide = GuideEntity(
            id = newId(),
            projectId = projectId,
            name = normalizedName,
            notes = notes?.trim()?.takeIf(String::isNotEmpty),
            createdAt = now,
            updatedAt = now
        )
        val draft = GuideDraftEntity(
            id = newId(),
            guideId = guide.id,
            baseRevisionId = null,
            createdAt = now,
            updatedAt = now,
            version = 0
        )
        guideDao.insertGuideWithDraft(guide, draft)
        return guide.toDomain()
    }

    override fun observePatternGuides(libraryItemId: String): Flow<List<Guide>> =
        guideDao.observeByLibraryItem(libraryItemId).map { guides -> guides.map { it.toDomain() } }

    override fun observeAllPatternGuides(): Flow<List<Guide>> =
        guideDao.observeAllPatternGuides().map { guides -> guides.map { it.toDomain() } }

    override suspend fun createPatternGuide(libraryItemId: String, sizeLabel: String, name: String): Guide {
        val normalizedName = name.trim()
        val normalizedSize = sizeLabel.trim()
        require(normalizedName.isNotEmpty()) { "Guide name must not be blank." }
        require(normalizedSize.isNotEmpty()) { "Size must not be blank." }
        val now = currentTimeMillis()
        val guide = GuideEntity(
            id = newId(),
            projectId = null,
            libraryItemId = libraryItemId,
            sizeLabel = normalizedSize,
            name = normalizedName,
            notes = null,
            createdAt = now,
            updatedAt = now
        )
        val draft = GuideDraftEntity(
            id = newId(),
            guideId = guide.id,
            baseRevisionId = null,
            createdAt = now,
            updatedAt = now,
            version = 0
        )
        guideDao.insertGuideWithDraft(guide, draft)
        return guide.toDomain()
    }

    override suspend fun useGuideInProject(projectId: String, guideId: GuideId) {
        guideDao.useGuideInProject(projectId, guideId.value, currentTimeMillis())
    }

    override suspend fun copyPatternGuideToSize(sourceGuideId: GuideId, targetSize: String, name: String): Guide =
        guideDao.copyPatternGuideToSize(sourceGuideId.value, targetSize, name, newId(), newId(), currentTimeMillis()).toDomain()

    override suspend fun stopUsingGuideInProject(projectId: String, guideId: GuideId) {
        guideDao.deleteProjectGuideLink(projectId, guideId.value)
    }

    override suspend fun updateGuideMetadata(
        guideId: GuideId,
        name: String,
        notes: String?
    ): Guide? {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Guide name must not be blank." }
        val updated = guideDao.updateGuideMetadata(
            guideId = guideId.value,
            name = normalizedName,
            notes = notes?.trim()?.takeIf(String::isNotEmpty),
            updatedAt = currentTimeMillis()
        )
        return if (updated == 1) guideDao.getGuide(guideId.value)?.toDomain() else null
    }

    override fun observePatternGuidesIncludingArchived(libraryItemId: String): Flow<List<Guide>> =
        guideDao.observeByLibraryItemIncludingArchived(libraryItemId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllPatternGuidesIncludingArchived(): Flow<List<Guide>> =
        guideDao.observeAllPatternGuidesIncludingArchived().map { rows -> rows.map { it.toDomain() } }

    override suspend fun setPatternGuideArchived(guideId: GuideId, libraryItemId: String?, archived: Boolean): Boolean =
        guideDao.setPatternGuideArchived(guideId.value, libraryItemId, archived, currentTimeMillis()) == 1

    override suspend fun deleteUnusedPatternGuide(guideId: GuideId, libraryItemId: String): Boolean =
        guideDao.deleteUnusedPatternGuide(guideId.value, libraryItemId) == 1

    override suspend fun deleteGuide(guideId: GuideId) {
        if (guideDao.deleteGuide(guideId.value) == 0 && getGuide(guideId) != null) {
            throw IllegalStateException("This guide is used by project pieces. Archive it to keep their work.")
        }
    }

    override suspend fun loadDraft(guideId: GuideId): GuideDraft? {
        return guideDao.getDraft(guideId.value)?.toDomain()
    }

    override suspend fun getReferenceNotes(guideId: GuideId): String? {
        val guide = getGuide(guideId) ?: return null
        val pattern = guide.libraryItemId?.let { guideDao.getPatternReference(it)?.toDomain() }
        return listOfNotNull(guide.notes, pattern?.let(com.macareen.stitchbook2.domain.library.PatternSuggestions::reference))
            .joinToString("\n\n").ifBlank { null }
    }

    override suspend fun moveDraftReferences(expectedGuide: Guide, expectedDraft: GuideDraft): GuideDraft {
        require(expectedGuide.id == expectedDraft.guideId)
        val plan = com.macareen.stitchbook2.domain.guide.GuideReferenceCleanup.preview(expectedDraft)
        if (plan.movedTexts.isEmpty()) return expectedDraft
        val updated = plan.draft.copy(updatedAt = currentTimeMillis(), version = expectedDraft.version + 1)
        val entity = GuideDraftEntity(updated.id.value, updated.guideId.value, updated.baseRevisionId?.value,
            updated.createdAt, updated.updatedAt, updated.version)
        return try {
            guideDao.replaceDraftAndReference(expectedGuide, entity, expectedDraft.version, updated.toNodeEntities(),
                plan.appendTo(expectedGuide.notes), plan).toDomain()
        } catch (_: DraftConflictException) { throw DraftVersionConflictException(expectedDraft.guideId) }
    }

    override suspend fun saveDraft(draft: GuideDraft): GuideDraft {
        val updated = draft.copy(
            updatedAt = currentTimeMillis(),
            version = draft.version + 1
        )
        val entity = GuideDraftEntity(
            id = updated.id.value,
            guideId = updated.guideId.value,
            baseRevisionId = updated.baseRevisionId?.value,
            createdAt = updated.createdAt,
            updatedAt = updated.updatedAt,
            version = updated.version
        )
        return try {
            guideDao.replaceDraft(
                draft = entity,
                expectedVersion = draft.version,
                nodes = updated.toNodeEntities()
            ).toDomain()
        } catch (error: InvalidDraftTreeException) {
            throw DraftValidationException(error.message.orEmpty())
        } catch (_: DraftConflictException) {
            throw DraftVersionConflictException(draft.guideId)
        }
    }

    override suspend fun createDraftFromLatestRevision(
        guideId: GuideId
    ): GuideDraft {
        return guideDao.createDraftFromLatestRevision(
            guideId = guideId.value,
            draftId = DraftId(newId()).value,
            createdAt = currentTimeMillis()
        ).toDomain()
    }

    override suspend fun listRevisions(
        guideId: GuideId
    ): List<DefinitionRevision> {
        return guideDao.getRevisions(guideId.value).map { it.toDomain() }
    }

    override suspend fun loadRevision(
        revisionId: DefinitionRevisionId
    ): DefinitionRevision? {
        return guideDao.getRevision(revisionId.value)?.toDomain()
    }

    override suspend fun getLatestRevision(guideId: GuideId): DefinitionRevision? {
        return guideDao.getLatestRevision(guideId.value)?.toDomain()
    }

    override suspend fun publishDraft(guideId: GuideId): DefinitionRevision {
        return try {
            guideDao.publishDraft(
                guideId = guideId.value,
                revisionId = DefinitionRevisionId(newId()).value,
                createdAt = currentTimeMillis()
            ).toDomain()
        } catch (error: InvalidDraftForPublicationException) {
            throw DraftValidationException(error.message.orEmpty(), DraftProblem.MISSING_DETAIL)
        } catch (error: InvalidGuideDefinitionException) {
            throw DraftValidationException(error.message.orEmpty(), problemOf(error.errors))
        } catch (_: DraftNotFoundException) {
            // The draft this publish targeted is gone -- from the caller's
            // perspective that is exactly what a version conflict already
            // means: the basis for this write no longer holds, so reload
            // authoritative state the same way a stale saveDraft would.
            throw DraftVersionConflictException(guideId)
        } catch (_: DraftConflictException) {
            throw DraftVersionConflictException(guideId)
        }
    }
}

/** The most useful single thing to tell the user about why a definition is invalid. */
internal fun problemOf(errors: List<GuideDefinitionError>): DraftProblem = when {
    errors.any { it is GuideDefinitionError.EmptyDefinition } -> DraftProblem.NOTHING_TO_KNIT
    errors.any {
        it is GuideDefinitionError.EmptyChildren || it is GuideDefinitionError.ContainerWithoutExecutableDescendant
    } -> DraftProblem.EMPTY_SECTION
    errors.any {
        it is GuideDefinitionError.InvalidRangeBounds ||
            it is GuideDefinitionError.BlankRangeUnitLabel ||
            it is GuideDefinitionError.NonPositiveRepeatCount
    } -> DraftProblem.MISSING_DETAIL
    else -> DraftProblem.OTHER
}
