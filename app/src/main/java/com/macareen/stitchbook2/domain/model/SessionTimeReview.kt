package com.macareen.stitchbook2.domain.model

/** Review a running timer since its last pause/resume, edit or explicit acknowledgement. */
object SessionTimeReview {
    const val DEFAULT_MINUTES = 30
    val thresholdRange = 1..1440
    fun needsReview(session: CraftingSession, now: Long, thresholdMinutes: Int): Boolean {
        if (!session.isRunning || thresholdMinutes !in thresholdRange) return false
        val checkpoint = maxOf(session.startedAt, session.updatedAt)
        return now >= checkpoint && now - checkpoint > thresholdMinutes * 60_000L
    }
    fun keepAll(session: CraftingSession, now: Long): CraftingSession =
        if (session.isRunning) session.copy(updatedAt = maxOf(now, session.updatedAt)) else session

    /** Keep the original interval, preserve prior pauses and record the user's explicit correction. */
    fun correctAndPause(session: CraftingSession, minutes: Long, now: Long): CraftingSession? {
        val worked = session.workedMillis(now)
        if (!session.isRunning || now < session.startedAt || minutes < 0 || minutes > worked / 60_000L) return null
        val excluded = worked - minutes * 60_000L
        val note = "Timer correction: kept $minutes min of ${worked / 60_000L} min; other time excluded."
        return SessionTimer.pause(session, now).copy(
            pausedTotalMillis = session.pausedTotalMillis + excluded,
            notes = session.notes?.let { "$it\n\n$note" } ?: note
        )
    }
}
