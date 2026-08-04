package com.macareen.stitchbook2.domain.repository

import com.macareen.stitchbook2.domain.execution.DefinitionRevisionId
import com.macareen.stitchbook2.domain.execution.GuideId
import com.macareen.stitchbook2.domain.guide.DefinitionRevision
import com.macareen.stitchbook2.domain.guide.Guide
import com.macareen.stitchbook2.domain.guide.GuideDraft
import kotlinx.coroutines.flow.Flow

/**
 * Thrown by [GuideRepository.saveDraft] when the given draft's node tree is
 * structurally invalid (a duplicate node ID, a node with more than one
 * parent, a node unreachable from a root, or a reference to a node that does
 * not exist).
 *
 * This is the domain-facing contract for that failure. Implementations must
 * translate any storage-specific tree-validation exception into this type
 * before it crosses the repository boundary; callers (ViewModels in
 * particular) should depend only on this type, never on a concrete exception
 * defined in the data layer.
 */
class DraftValidationException(
    message: String
) : IllegalArgumentException(message)

/**
 * Thrown by [GuideRepository.saveDraft] when the given draft's `version` no
 * longer matches what is actually persisted -- i.e. another save committed
 * first.
 *
 * This is the domain-facing contract for that failure, mirroring
 * [ExecutionVersionConflictException]'s role for Executions. Implementations
 * must translate any storage-specific conflict exception into this type
 * before it crosses the repository boundary.
 */
class DraftVersionConflictException(
    guideId: GuideId
) : IllegalStateException(
    "Draft changed before it could be saved: ${guideId.value}"
)

interface GuideRepository {
    fun observeGuides(projectId: String): Flow<List<Guide>>

    suspend fun getGuide(guideId: GuideId): Guide?

    suspend fun createGuide(
        projectId: String,
        name: String,
        notes: String? = null
    ): Guide

    suspend fun updateGuideMetadata(
        guideId: GuideId,
        name: String,
        notes: String?
    ): Guide?

    suspend fun deleteGuide(guideId: GuideId)

    suspend fun loadDraft(guideId: GuideId): GuideDraft?

    /**
     * Persists [draft] as the given Guide's current Draft state.
     *
     * Throws [DraftValidationException] if [draft]'s node tree is
     * structurally invalid, or [DraftVersionConflictException] if
     * [draft].version no longer matches what is persisted.
     */
    suspend fun saveDraft(draft: GuideDraft): GuideDraft

    suspend fun createDraftFromLatestRevision(guideId: GuideId): GuideDraft

    suspend fun listRevisions(guideId: GuideId): List<DefinitionRevision>

    suspend fun loadRevision(
        revisionId: DefinitionRevisionId
    ): DefinitionRevision?

    /**
     * The executable Definition Revision with the highest `revisionNumber`
     * for [guideId] -- i.e. the most recently published revision -- or null
     * if [guideId] has never published one.
     *
     * This is the single authoritative definition of "latest published
     * revision": callers (Focus Mode's Start action in particular) must use
     * this instead of deriving their own ordering over [listRevisions], so
     * revision-selection stays defined in exactly one place.
     */
    suspend fun getLatestRevision(guideId: GuideId): DefinitionRevision?

    /**
     * Publishes the guide's latest persisted draft state as a new immutable
     * [DefinitionRevision].
     *
     * This publishes whatever is currently saved for the draft, not
     * necessarily the state a caller previously loaded via [loadDraft]. There
     * is no expected-version check against the caller's own copy here;
     * staleness protection for concurrent edits is handled by [saveDraft]'s
     * optimistic concurrency, not by this method.
     *
     * Throws [DraftValidationException] if the draft is structurally
     * incomplete or otherwise invalid for publication (a required field is
     * missing, a container is empty, a Range's bounds are invalid, and so
     * on) -- the draft itself is left unchanged. Throws
     * [DraftVersionConflictException] if the draft row backing this Guide
     * was concurrently changed or no longer exists.
     */
    suspend fun publishDraft(guideId: GuideId): DefinitionRevision
}
