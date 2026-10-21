package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

class GuideNotFoundException(
    guideId: String
) : IllegalStateException("Guide not found: $guideId")

class DraftNotFoundException(
    guideId: String
) : IllegalStateException("Draft not found for guide: $guideId")

class DraftConflictException(
    draftId: String
) : IllegalStateException("Draft changed before it could be saved: $draftId")

@Dao
abstract class GuideDao {

    /** The project's own guides plus the pattern guides it uses. */
    @Query(
        """
        SELECT * FROM guides
        WHERE project_id = :projectId
           OR id IN (SELECT guide_id FROM project_guides WHERE project_id = :projectId)
           OR id IN (SELECT guide_id FROM project_pieces WHERE project_id = :projectId)
        ORDER BY updated_at DESC, name COLLATE NOCASE ASC, id ASC
        """
    )
    abstract fun observeByProject(projectId: String): Flow<List<GuideEntity>>

    @Query(
        """
        SELECT * FROM guides
        WHERE library_item_id = :libraryItemId AND is_archived = 0
        ORDER BY size_label COLLATE NOCASE ASC, name COLLATE NOCASE ASC, id ASC
        """
    )
    abstract fun observeByLibraryItem(libraryItemId: String): Flow<List<GuideEntity>>

    @Query("SELECT * FROM guides WHERE library_item_id = :libraryItemId ORDER BY size_label COLLATE NOCASE, name COLLATE NOCASE, id")
    abstract fun observeByLibraryItemIncludingArchived(libraryItemId: String): Flow<List<GuideEntity>>

    @Query("SELECT * FROM guides WHERE project_id IS NULL ORDER BY name COLLATE NOCASE, size_label COLLATE NOCASE, id")
    abstract fun observeAllPatternGuidesIncludingArchived(): Flow<List<GuideEntity>>

    @Query("UPDATE guides SET is_archived = :archived, updated_at = :updatedAt WHERE id = :guideId AND project_id IS NULL AND library_item_id IS :libraryItemId")
    abstract suspend fun setPatternGuideArchived(guideId: String, libraryItemId: String?, archived: Boolean, updatedAt: Long): Int

    @Query(
        """
        SELECT * FROM guides
        WHERE project_id IS NULL AND is_archived = 0
        ORDER BY name COLLATE NOCASE ASC, size_label COLLATE NOCASE ASC, id ASC
        """
    )
    abstract fun observeAllPatternGuides(): Flow<List<GuideEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertProjectGuideLink(link: ProjectGuideLinkEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertProjectPatternLink(link: ProjectPatternLinkEntity)

    /** Resolve ownership in the transaction; never trust a stale guide passed by the UI. */
    @Transaction
    open suspend fun useGuideInProject(projectId: String, guideId: String, now: Long) {
        val guide = getGuide(guideId) ?: throw GuideNotFoundException(guideId)
        require(guide.projectId == null || guide.projectId == projectId) {
            "A project-owned guide cannot be attached to another project."
        }
        insertProjectGuideLink(ProjectGuideLinkEntity(projectId, guideId))
        guide.libraryItemId?.let { insertProjectPatternLink(ProjectPatternLinkEntity(projectId, it)) }
        if (guide.projectId == null && guide.isArchived) {
            check(setPatternGuideArchived(guideId, guide.libraryItemId, false, now) == 1)
        }
    }

    @Query("DELETE FROM project_guides WHERE project_id = :projectId AND guide_id = :guideId")
    abstract suspend fun deleteProjectGuideLink(projectId: String, guideId: String)

    @Query("SELECT * FROM guides WHERE id = :guideId LIMIT 1")
    abstract suspend fun getGuide(guideId: String): GuideEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertGuideEntity(guide: GuideEntity)

    @Query(
        """
        UPDATE guides
        SET name = :name, notes = :notes, updated_at = :updatedAt
        WHERE id = :guideId
        """
    )
    abstract suspend fun updateGuideMetadata(
        guideId: String,
        name: String,
        notes: String?,
        updatedAt: Long
    ): Int

    @Query("""
        DELETE FROM guides WHERE id = :guideId AND library_item_id = :libraryItemId AND project_id IS NULL
        AND NOT EXISTS (SELECT 1 FROM project_guides WHERE guide_id = :guideId)
        AND NOT EXISTS (SELECT 1 FROM project_pieces WHERE guide_id = :guideId)
        AND NOT EXISTS (SELECT 1 FROM executions WHERE guide_id = :guideId)
    """)
    abstract suspend fun deleteUnusedPatternGuide(guideId: String, libraryItemId: String): Int

    @Query("DELETE FROM guides WHERE id = :guideId AND NOT EXISTS (SELECT 1 FROM project_pieces WHERE guide_id = :guideId)")
    abstract suspend fun deleteGuide(guideId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertDraftEntity(draft: GuideDraftEntity)

    @Query("SELECT * FROM guide_drafts WHERE guide_id = :guideId LIMIT 1")
    protected abstract suspend fun getDraftEntity(guideId: String): GuideDraftEntity?

    @Query("SELECT * FROM draft_nodes WHERE draft_id = :draftId")
    protected abstract suspend fun getDraftNodeEntities(
        draftId: String
    ): List<DraftNodeEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertDraftNodeEntities(
        nodes: List<DraftNodeEntity>
    )

    @Query("DELETE FROM draft_nodes WHERE draft_id = :draftId")
    protected abstract suspend fun deleteDraftNodeEntities(draftId: String)

    @Query(
        """
        UPDATE guide_drafts
        SET base_revision_id = :baseRevisionId,
            updated_at = :updatedAt,
            version = :newVersion
        WHERE id = :draftId
          AND guide_id = :guideId
          AND version = :expectedVersion
        """
    )
    protected abstract suspend fun updateDraftEntity(
        draftId: String,
        guideId: String,
        baseRevisionId: String?,
        updatedAt: Long,
        expectedVersion: Long,
        newVersion: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRevisionEntity(
        revision: DefinitionRevisionEntity
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRevisionNodeEntities(
        nodes: List<RevisionNodeEntity>
    )

    @Query(
        """
        SELECT * FROM definition_revisions
        WHERE guide_id = :guideId
        ORDER BY revision_number ASC
        """
    )
    protected abstract suspend fun getRevisionEntities(
        guideId: String
    ): List<DefinitionRevisionEntity>

    @Query(
        """
        SELECT * FROM definition_revisions
        WHERE guide_id = :guideId
        ORDER BY revision_number DESC
        LIMIT 1
        """
    )
    protected abstract suspend fun getLatestRevisionEntity(
        guideId: String
    ): DefinitionRevisionEntity?

    @Query("SELECT * FROM definition_revisions WHERE id = :revisionId LIMIT 1")
    protected abstract suspend fun getRevisionEntity(
        revisionId: String
    ): DefinitionRevisionEntity?

    @Query("SELECT * FROM revision_nodes WHERE revision_id = :revisionId")
    protected abstract suspend fun getRevisionNodeEntities(
        revisionId: String
    ): List<RevisionNodeEntity>

    @Query(
        """
        SELECT COALESCE(MAX(revision_number), 0)
        FROM definition_revisions
        WHERE guide_id = :guideId
        """
    )
    protected abstract suspend fun getHighestRevisionNumber(guideId: String): Int

    @Query("SELECT * FROM guides WHERE library_item_id = :patternId")
    protected abstract suspend fun getPatternGuideRows(patternId: String): List<GuideEntity>

    @Transaction
    open suspend fun copyPatternGuideToSize(sourceGuideId: String, targetSize: String, name: String,
        newGuideId: String, newDraftId: String, now: Long): GuideEntity {
        val source = getGuide(sourceGuideId) ?: throw GuideNotFoundException(sourceGuideId)
        val patternId = requireNotNull(source.libraryItemId)
        require(source.projectId == null) { "Only reusable pattern guides can be copied between sizes." }
        val size = targetSize.trim()
        require(size.isNotEmpty() && name.isNotBlank())
        require(com.macareen.stitchbook2.domain.parsing.PatternSizes.indexOf(size, listOfNotNull(source.sizeLabel)) == null) {
            "Source and target size must differ."
        }
        val rows = getPatternGuideRows(patternId)
        val labels = rows.filterNot { it.isArchived }.mapNotNull { it.sizeLabel }
        require(com.macareen.stitchbook2.domain.parsing.PatternSizes.indexOf(size, labels) == null) {
            "That size already has a guide."
        }
        val revision = getLatestRevision(sourceGuideId) ?: error("Publish the source guide before copying it.")
        revision.toDomain() // Validate the persisted source before introducing a copied tree.
        val provenance = "Copied from ${source.name}, size ${source.sizeLabel}, revision ${revision.revision.revisionNumber}. Review all counts, repeats and measurements for size $size before publishing."
        val guide = GuideEntity(newGuideId, null, patternId, size, name.trim(),
            listOfNotNull(source.notes, provenance).joinToString("\n\n"), now, now)
        val draft = GuideDraftEntity(newDraftId, newGuideId, null, now, now, 0)
        val ids = revision.nodes.associate { it.nodeId to java.util.UUID.randomUUID().toString() }
        val nodes = revision.nodes.map { old -> old.toDraftNode(newDraftId).copy(
            nodeId = ids.getValue(old.nodeId), parentNodeId = old.parentNodeId?.let(ids::getValue)) }
        val review = DraftNodeEntity(newDraftId, java.util.UUID.randomUUID().toString(), null,
            nodes.count { it.parentNodeId == null }, "INSTRUCTION", null,
            "Review needed: $provenance", null, null, null, null, null)
        insertGuideEntity(guide); insertDraftEntity(draft); insertDraftNodeEntities(nodes + review)
        return guide
    }

    @Transaction
    open suspend fun insertGuideWithDraft(
        guide: GuideEntity,
        draft: GuideDraftEntity
    ) {
        insertGuideEntity(guide)
        insertDraftEntity(draft)
    }

    @Transaction
    open suspend fun getDraft(guideId: String): DraftAggregate? {
        val draft = getDraftEntity(guideId) ?: return null
        return DraftAggregate(
            draft = draft,
            nodes = getDraftNodeEntities(draft.id)
        )
    }

    @Transaction
    open suspend fun replaceDraftAndReference(expectedGuide: com.macareen.stitchbook2.domain.guide.Guide,
        draft: GuideDraftEntity, expectedVersion: Long, nodes: List<DraftNodeEntity>, notes: String): DraftAggregate {
        if (getGuide(expectedGuide.id.value)?.toDomain() != expectedGuide) throw DraftConflictException(draft.id)
        val changed = replaceDraft(draft, expectedVersion, nodes)
        check(updateGuideMetadata(expectedGuide.id.value, expectedGuide.name, notes, draft.updatedAt) == 1)
        return changed
    }

    @Transaction
    open suspend fun replaceDraft(
        draft: GuideDraftEntity,
        expectedVersion: Long,
        nodes: List<DraftNodeEntity>
    ): DraftAggregate {
        val updated = updateDraftEntity(
            draftId = draft.id,
            guideId = draft.guideId,
            baseRevisionId = draft.baseRevisionId,
            updatedAt = draft.updatedAt,
            expectedVersion = expectedVersion,
            newVersion = draft.version
        )
        if (updated != 1) throw DraftConflictException(draft.id)

        deleteDraftNodeEntities(draft.id)
        if (nodes.isNotEmpty()) insertDraftNodeEntities(nodes)
        return DraftAggregate(draft, nodes)
    }

    @Transaction
    open suspend fun createDraftFromLatestRevision(
        guideId: String,
        draftId: String,
        createdAt: Long
    ): DraftAggregate {
        getDraft(guideId)?.let { return it }
        if (getGuide(guideId) == null) throw GuideNotFoundException(guideId)

        val latest = getLatestRevisionEntity(guideId)
        val draft = GuideDraftEntity(
            id = draftId,
            guideId = guideId,
            baseRevisionId = latest?.id,
            createdAt = createdAt,
            updatedAt = createdAt,
            version = 0
        )
        insertDraftEntity(draft)

        val nodes = latest
            ?.let { getRevisionNodeEntities(it.id) }
            .orEmpty()
            .map { it.toDraftNode(draftId) }
        if (nodes.isNotEmpty()) insertDraftNodeEntities(nodes)
        return DraftAggregate(draft, nodes)
    }

    @Transaction
    open suspend fun getRevisions(guideId: String): List<RevisionAggregate> {
        return getRevisionEntities(guideId).map { revision ->
            RevisionAggregate(
                revision = revision,
                nodes = getRevisionNodeEntities(revision.id)
            )
        }
    }

    @Transaction
    open suspend fun getRevision(revisionId: String): RevisionAggregate? {
        val revision = getRevisionEntity(revisionId) ?: return null
        return RevisionAggregate(
            revision = revision,
            nodes = getRevisionNodeEntities(revision.id)
        )
    }

    /** The revision with the highest `revision_number` for [guideId], or null if none exists. */
    @Transaction
    open suspend fun getLatestRevision(guideId: String): RevisionAggregate? {
        val revision = getLatestRevisionEntity(guideId) ?: return null
        return RevisionAggregate(
            revision = revision,
            nodes = getRevisionNodeEntities(revision.id)
        )
    }

    @Transaction
    open suspend fun publishDraft(
        guideId: String,
        revisionId: String,
        createdAt: Long
    ): RevisionAggregate {
        val draftAggregate = getDraft(guideId)
            ?: throw DraftNotFoundException(guideId)
        val draft = draftAggregate.toDomain()
        draft.toGuideDefinitionForPublication(
            com.macareen.stitchbook2.domain.execution.DefinitionRevisionId(revisionId)
        )

        val revision = DefinitionRevisionEntity(
            id = revisionId,
            guideId = guideId,
            revisionNumber = getHighestRevisionNumber(guideId) + 1,
            createdAt = createdAt
        )
        val revisionNodes = draftAggregate.nodes.map {
            it.toRevisionNode(revisionId)
        }

        insertRevisionEntity(revision)
        if (revisionNodes.isNotEmpty()) insertRevisionNodeEntities(revisionNodes)

        val draftUpdated = updateDraftEntity(
            draftId = draftAggregate.draft.id,
            guideId = guideId,
            baseRevisionId = revisionId,
            updatedAt = createdAt,
            expectedVersion = draftAggregate.draft.version,
            newVersion = draftAggregate.draft.version + 1
        )
        if (draftUpdated != 1) {
            throw DraftConflictException(draftAggregate.draft.id)
        }

        return RevisionAggregate(revision, revisionNodes)
    }
}
