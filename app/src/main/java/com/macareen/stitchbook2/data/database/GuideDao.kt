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

    @Query(
        """
        SELECT * FROM guides
        WHERE project_id = :projectId
        ORDER BY updated_at DESC, name COLLATE NOCASE ASC, id ASC
        """
    )
    abstract fun observeByProject(projectId: String): Flow<List<GuideEntity>>

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

    @Query("DELETE FROM guides WHERE id = :guideId")
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
