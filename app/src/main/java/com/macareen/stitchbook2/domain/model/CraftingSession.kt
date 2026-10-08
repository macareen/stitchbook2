package com.macareen.stitchbook2.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One timed stretch of crafting (PRODUCT_SPEC.md 6.4). Only wall-clock
 * timestamps are stored -- never a ticking counter -- so a running or
 * paused session survives process death and needs no background service
 * (ROADMAP.md Phase 8: "no always-on background execution when
 * platform-safe timestamps suffice").
 *
 * - Running: [endedAt] == null and [pausedAt] == null.
 * - Paused: [endedAt] == null and [pausedAt] != null.
 * - Finished: [endedAt] != null.
 *
 * [zoneId] is the device time zone when the session started. Day-boundary
 * rule: a session counts entirely toward the local calendar date on which
 * it *started*, in that recorded zone -- a session from 23:30 to 00:30 is
 * one hour on the first day, and travelling never moves past sessions to a
 * different day.
 */
data class CraftingSession(
    val id: String,
    val projectId: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val pausedAt: Long?,
    val pausedTotalMillis: Long,
    val zoneId: String,
    /** Rows/rounds the user recorded for this session -- a recorded value. */
    val rowsCompleted: Int?,
    /** User's stitch count per row, used only to *estimate* stitches. */
    val stitchesPerRow: Int?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isRunning: Boolean get() = endedAt == null && pausedAt == null
    val isPaused: Boolean get() = endedAt == null && pausedAt != null
    val isActive: Boolean get() = endedAt == null

    /** Worked time, excluding pauses, measured up to [now] for an active session. Never negative. */
    fun workedMillis(now: Long): Long {
        // While paused, time stops accruing at the pause moment.
        val end = endedAt ?: pausedAt ?: now
        return (end - startedAt - pausedTotalMillis).coerceAtLeast(0L)
    }

    fun localStartDate(): LocalDate = Instant.ofEpochMilli(startedAt).atZone(safeZone(zoneId)).toLocalDate()
}

fun safeZone(zoneId: String): ZoneId = try {
    ZoneId.of(zoneId)
} catch (_: Exception) {
    ZoneId.systemDefault()
}

/** Timer transitions as pure functions; each returns the updated session. */
object SessionTimer {

    fun start(id: String, projectId: String?, now: Long, zoneId: String) = CraftingSession(
        id = id,
        projectId = projectId,
        startedAt = now,
        endedAt = null,
        pausedAt = null,
        pausedTotalMillis = 0,
        zoneId = zoneId,
        rowsCompleted = null,
        stitchesPerRow = null,
        notes = null,
        createdAt = now,
        updatedAt = now
    )

    fun pause(session: CraftingSession, now: Long): CraftingSession =
        if (!session.isRunning) session else session.copy(pausedAt = now.coerceAtLeast(session.startedAt), updatedAt = now)

    fun resume(session: CraftingSession, now: Long): CraftingSession {
        val pausedAt = session.pausedAt ?: return session
        if (session.endedAt != null) return session
        return session.copy(
            pausedAt = null,
            pausedTotalMillis = session.pausedTotalMillis + (now - pausedAt).coerceAtLeast(0L),
            updatedAt = now
        )
    }

    /** Stopping a paused session ends it at the pause moment, so the paused tail isn't counted. */
    fun stop(session: CraftingSession, now: Long): CraftingSession {
        if (session.endedAt != null) return session
        val end = session.pausedAt ?: now
        return session.copy(endedAt = end.coerceAtLeast(session.startedAt), pausedAt = null, updatedAt = now)
    }

    /**
     * A user correction (PRODUCT_SPEC.md 6.4: "Session correction"):
     * replaces start and worked duration outright, dropping recorded pauses
     * since the user is now stating the true worked time.
     */
    fun correct(session: CraftingSession, startedAt: Long, workedMillis: Long, now: Long): CraftingSession =
        session.copy(
            startedAt = startedAt,
            endedAt = startedAt + workedMillis.coerceAtLeast(0L),
            pausedAt = null,
            pausedTotalMillis = 0,
            updatedAt = now
        )
}
