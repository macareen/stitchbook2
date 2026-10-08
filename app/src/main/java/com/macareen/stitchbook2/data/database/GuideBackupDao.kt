package com.macareen.stitchbook2.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/** Every row of the guide graph, as the full-library backup reads and writes it. */
data class GuideGraphRows(
    val guides: List<GuideEntity> = emptyList(),
    val drafts: List<GuideDraftEntity> = emptyList(),
    val draftNodes: List<DraftNodeEntity> = emptyList(),
    val revisions: List<DefinitionRevisionEntity> = emptyList(),
    val revisionNodes: List<RevisionNodeEntity> = emptyList(),
    val executions: List<ExecutionEntity> = emptyList(),
    val currentFrames: List<ExecutionCurrentAddressFrameEntity> = emptyList(),
    val completedOccurrences: List<ExecutionCompletedOccurrenceEntity> = emptyList(),
    val completedOccurrenceFrames: List<ExecutionCompletedOccurrenceFrameEntity> = emptyList(),
    val activeExecutions: List<ActiveExecutionEntity> = emptyList(),
    val projectGuides: List<ProjectGuideLinkEntity> = emptyList()
)

/**
 * Raw access to the guide graph for backup and restore. Unlike [GuideDao]
 * and [ExecutionDao], it copies stored rows exactly (versions, revision
 * numbers, completed steps) instead of going through publication or the
 * execution engine, so a restore reproduces the backed-up state.
 */
@Dao
abstract class GuideBackupDao {

    @Query("SELECT * FROM guides") protected abstract suspend fun allGuides(): List<GuideEntity>
    @Query("SELECT * FROM guide_drafts") protected abstract suspend fun allDrafts(): List<GuideDraftEntity>
    @Query("SELECT * FROM draft_nodes") protected abstract suspend fun allDraftNodes(): List<DraftNodeEntity>
    @Query("SELECT * FROM definition_revisions") protected abstract suspend fun allRevisions(): List<DefinitionRevisionEntity>
    @Query("SELECT * FROM revision_nodes") protected abstract suspend fun allRevisionNodes(): List<RevisionNodeEntity>
    @Query("SELECT * FROM executions") protected abstract suspend fun allExecutions(): List<ExecutionEntity>
    @Query("SELECT * FROM execution_current_address_frames")
    protected abstract suspend fun allCurrentFrames(): List<ExecutionCurrentAddressFrameEntity>
    @Query("SELECT * FROM execution_completed_occurrences")
    protected abstract suspend fun allCompletedOccurrences(): List<ExecutionCompletedOccurrenceEntity>
    @Query("SELECT * FROM execution_completed_occurrence_frames")
    protected abstract suspend fun allCompletedOccurrenceFrames(): List<ExecutionCompletedOccurrenceFrameEntity>
    @Query("SELECT * FROM active_executions") protected abstract suspend fun allActiveExecutions(): List<ActiveExecutionEntity>
    @Query("SELECT * FROM project_guides") protected abstract suspend fun allProjectGuides(): List<ProjectGuideLinkEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertGuides(rows: List<GuideEntity>)
    @Upsert protected abstract suspend fun upsertGuides(rows: List<GuideEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertDrafts(rows: List<GuideDraftEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertDraftNodes(rows: List<DraftNodeEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertRevisions(rows: List<DefinitionRevisionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertRevisionNodes(rows: List<RevisionNodeEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) protected abstract suspend fun insertExecutions(rows: List<ExecutionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCurrentFrames(rows: List<ExecutionCurrentAddressFrameEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCompletedOccurrences(rows: List<ExecutionCompletedOccurrenceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCompletedOccurrenceFrames(rows: List<ExecutionCompletedOccurrenceFrameEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertActiveExecutions(rows: List<ActiveExecutionEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertProjectGuides(rows: List<ProjectGuideLinkEntity>)

    @Query("DELETE FROM guides WHERE id = :guideId") protected abstract suspend fun deleteGuide(guideId: String)
    @Query("DELETE FROM executions WHERE guide_id = :guideId") protected abstract suspend fun deleteExecutionsOf(guideId: String)
    @Query("DELETE FROM guide_drafts WHERE guide_id = :guideId") protected abstract suspend fun deleteDraftsOf(guideId: String)
    @Query("DELETE FROM definition_revisions WHERE guide_id = :guideId") protected abstract suspend fun deleteRevisionsOf(guideId: String)
    @Query("DELETE FROM project_guides WHERE project_id = :projectId AND guide_id = :guideId")
    protected abstract suspend fun deleteProjectGuide(projectId: String, guideId: String)

    @Transaction
    open suspend fun load(): GuideGraphRows = GuideGraphRows(
        guides = allGuides(),
        drafts = allDrafts(),
        draftNodes = allDraftNodes(),
        revisions = allRevisions(),
        revisionNodes = allRevisionNodes(),
        executions = allExecutions(),
        currentFrames = allCurrentFrames(),
        completedOccurrences = allCompletedOccurrences(),
        completedOccurrenceFrames = allCompletedOccurrenceFrames(),
        activeExecutions = allActiveExecutions(),
        projectGuides = allProjectGuides()
    )

    /**
     * Makes the stored graph match [rows]. Guides missing from [rows] are
     * deleted (their children cascade). Kept guide rows are upserted in
     * place, so project-guide links to them survive; each kept guide's
     * executions, draft, and revisions are then deleted in that order --
     * executions first because their revision key is NO ACTION -- and
     * reinserted from [rows].
     */
    @Transaction
    open suspend fun replace(rows: GuideGraphRows) {
        val keep = rows.guides.map { it.id }.toSet()
        allGuides().filter { it.id !in keep }.forEach { deleteGuide(it.id) }
        if (rows.guides.isNotEmpty()) upsertGuides(rows.guides)
        keep.forEach { guideId ->
            deleteExecutionsOf(guideId)
            deleteDraftsOf(guideId)
            deleteRevisionsOf(guideId)
        }
        insertChildren(rows)
        val links = rows.projectGuides.toSet()
        allProjectGuides().filter { it !in links }.forEach { deleteProjectGuide(it.projectId, it.guideId) }
        if (rows.projectGuides.isNotEmpty()) insertProjectGuides(rows.projectGuides)
    }

    /** Inserts [rows] for guides that don't exist yet; any collision aborts the whole restore. */
    @Transaction
    open suspend fun add(rows: GuideGraphRows) {
        if (rows.guides.isNotEmpty()) insertGuides(rows.guides)
        insertChildren(rows)
        if (rows.projectGuides.isNotEmpty()) insertProjectGuides(rows.projectGuides)
    }

    /** Foreign-key order: revisions before the drafts and executions built on them, executions before their pointers. */
    private suspend fun insertChildren(rows: GuideGraphRows) {
        if (rows.revisions.isNotEmpty()) insertRevisions(rows.revisions)
        if (rows.revisionNodes.isNotEmpty()) insertRevisionNodes(rows.revisionNodes)
        if (rows.drafts.isNotEmpty()) insertDrafts(rows.drafts)
        if (rows.draftNodes.isNotEmpty()) insertDraftNodes(rows.draftNodes)
        if (rows.executions.isNotEmpty()) insertExecutions(rows.executions)
        if (rows.currentFrames.isNotEmpty()) insertCurrentFrames(rows.currentFrames)
        if (rows.completedOccurrences.isNotEmpty()) insertCompletedOccurrences(rows.completedOccurrences)
        if (rows.completedOccurrenceFrames.isNotEmpty()) insertCompletedOccurrenceFrames(rows.completedOccurrenceFrames)
        if (rows.activeExecutions.isNotEmpty()) insertActiveExecutions(rows.activeExecutions)
    }
}
