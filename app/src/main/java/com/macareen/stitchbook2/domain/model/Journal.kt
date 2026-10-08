package com.macareen.stitchbook2.domain.model

/**
 * A *reference* to a photo the user owns (PRODUCT_SPEC.md 6.9). [uri] is a
 * persisted-permission SAF `content://` URI -- the original is never
 * copied into app storage, and deleting this record never deletes the
 * file. [displayName] is captured at attach time so a missing file can
 * still be identified for relinking.
 *
 * Exactly one owner is expected: a project photo has [projectId]; a stash
 * photo has [stashItemId] (Phase 7: "yarn and other inventory photos use
 * the same user-accessible storage policy").
 */
data class Photo(
    val id: String,
    val projectId: String?,
    val stashItemId: String?,
    val uri: String,
    val displayName: String?,
    val caption: String?,
    /** ISO-8601 local date the photo shows, if the user recorded one. */
    val takenDate: String?,
    val milestoneId: String?,
    val role: PhotoRole,
    val createdAt: Long,
    val updatedAt: Long
)

/** A user-picked document reference (SAF URI string plus the name captured at pick time). */
data class PickedDocument(
    val uri: String,
    val displayName: String?
)

/** Marks the photos a before-and-after comparison uses. */
enum class PhotoRole(val storageValue: String) {
    NONE("NONE"),
    BEFORE("BEFORE"),
    AFTER("AFTER");

    companion object {
        fun fromStorageValue(value: String?): PhotoRole =
            entries.firstOrNull { it.storageValue == value } ?: NONE
    }
}

/** A private, dated journal entry -- never shared or exported to cards unless the user picks it. */
data class JournalEntry(
    val id: String,
    val projectId: String,
    /** ISO-8601 local date the entry is about. */
    val entryDate: String,
    val title: String?,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * A project milestone ("cast on", "body done", "blocked"). [position]
 * preserves the user's order independent of dates, which may be blank for
 * planned milestones.
 */
data class Milestone(
    val id: String,
    val projectId: String,
    val title: String,
    val reachedDate: String?,
    val notes: String?,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/** Journal entries newest first; same-day entries by creation time, newest first. */
fun List<JournalEntry>.sortedForDisplay(): List<JournalEntry> =
    sortedWith(compareByDescending<JournalEntry> { it.entryDate }.thenByDescending { it.createdAt })

/**
 * Moves the milestone [id] one place up (-1) or down (+1) and renumbers all
 * positions 0..n-1 so gaps or duplicates from older data never accumulate.
 * Returns only the milestones whose position actually changed.
 */
fun reorderMilestones(milestones: List<Milestone>, id: String, direction: Int, now: Long): List<Milestone> {
    val ordered = milestones.sortedWith(compareBy<Milestone> { it.position }.thenBy { it.createdAt }).toMutableList()
    val index = ordered.indexOfFirst { it.id == id }
    val target = index + direction
    if (index < 0 || target !in ordered.indices) return emptyList()
    ordered.add(target, ordered.removeAt(index))
    return ordered.mapIndexedNotNull { position, milestone ->
        if (milestone.position == position) null else milestone.copy(position = position, updatedAt = now)
    }
}

/** The before/after pair, if the user has marked both. The most recently updated wins on duplicates. */
fun List<Photo>.beforeAfterPair(): Pair<Photo, Photo>? {
    val before = filter { it.role == PhotoRole.BEFORE }.maxByOrNull { it.updatedAt } ?: return null
    val after = filter { it.role == PhotoRole.AFTER }.maxByOrNull { it.updatedAt } ?: return null
    return before to after
}
