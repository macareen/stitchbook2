package com.macareen.stitchbook2.domain.model

/**
 * Worked time for the knitting view, from a project's crafting sessions:
 * all of it, and the part since the current step began. Only the time
 * sessions were running counts, so breaks and nights don't.
 *
 * A session stores its pauses as one total, not where they fell. When a
 * step began part-way through a paused session, that session's pauses are
 * shared out in proportion to the overlap, so [stepIsEstimated] says the
 * step figure is approximate.
 */
data class KnittingTime(val totalMillis: Long, val stepMillis: Long, val stepIsEstimated: Boolean) {

    companion object {
        fun of(sessions: List<CraftingSession>, stepStartedAt: Long, now: Long): KnittingTime {
            var total = 0L
            var step = 0.0
            var estimated = false
            for (session in sessions) {
                total += session.workedMillis(now)
                val end = (session.endedAt ?: session.pausedAt ?: now).coerceAtMost(now)
                val span = end - session.startedAt
                if (span <= 0) continue
                val overlap = (end - maxOf(session.startedAt, stepStartedAt)).coerceAtLeast(0L)
                if (overlap == 0L) continue
                val pausesInside = session.pausedTotalMillis * overlap.toDouble() / span
                if (overlap < span && session.pausedTotalMillis > 0) estimated = true
                step += (overlap - pausesInside).coerceAtLeast(0.0)
            }
            return KnittingTime(total, step.toLong(), estimated)
        }
    }
}
