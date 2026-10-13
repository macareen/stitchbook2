package com.macareen.stitchbook2.domain.model

import java.time.*
import java.time.format.DateTimeFormatter

/** User-supplied finish time; duration determines its start without starting a live timer. */
data class ManualTimeInput(val finishedDate: String, val finishedTime: String, val minutes: String,
    val notes: String = "", val offset: String? = null, val confirmOverlap: Boolean = false)
data class ManualTimeDefaults(val finishedDate: String, val finishedTime: String, val zoneId: String) {
    companion object {
        fun at(now: Long, zone: ZoneId): ManualTimeDefaults {
            val local = Instant.ofEpochMilli(now).atZone(zone)
            return ManualTimeDefaults(local.toLocalDate().toString(), local.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")), zone.id)
        }
    }
}
enum class ManualTimeError { INVALID_DATE, INVALID_TIME, INVALID_DURATION, FUTURE_TIME, CLOCK_GAP, CHOOSE_OFFSET, POSSIBLE_OVERLAP, SAVE_FAILED }
sealed interface ManualTimeResult {
    data class Ready(val session: CraftingSession) : ManualTimeResult
    data class Invalid(val error: ManualTimeError, val offsets: List<String> = emptyList()) : ManualTimeResult
}

object ManualCraftingTime {
    fun create(input: ManualTimeInput, projectId: String?, id: String, zone: ZoneId, now: Long): ManualTimeResult {
        val date = parseLocalDateOrNull(input.finishedDate) ?: return ManualTimeResult.Invalid(ManualTimeError.INVALID_DATE)
        val time = runCatching { LocalTime.parse(input.finishedTime.trim(), DateTimeFormatter.ofPattern("H:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT)) }
            .getOrNull() ?: return ManualTimeResult.Invalid(ManualTimeError.INVALID_TIME)
        val minutes = input.minutes.trim().toLongOrNull()?.takeIf { it in 1..1440 }
            ?: return ManualTimeResult.Invalid(ManualTimeError.INVALID_DURATION)
        val local = LocalDateTime.of(date, time)
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.isEmpty()) return ManualTimeResult.Invalid(ManualTimeError.CLOCK_GAP)
        val offset = if (offsets.size == 1) offsets.single() else offsets.firstOrNull { it.id == input.offset }
            ?: return ManualTimeResult.Invalid(ManualTimeError.CHOOSE_OFFSET, offsets.map { it.id })
        val finish = runCatching { local.toInstant(offset).toEpochMilli() }.getOrNull()
            ?: return ManualTimeResult.Invalid(ManualTimeError.INVALID_DATE)
        if (finish > now) return ManualTimeResult.Invalid(ManualTimeError.FUTURE_TIME)
        val start = runCatching { Math.subtractExact(finish, minutes * 60_000L) }.getOrNull()
            ?: return ManualTimeResult.Invalid(ManualTimeError.INVALID_DURATION)
        return ManualTimeResult.Ready(SessionTimer.start(id, projectId, start, zone.id).copy(
            endedAt = finish, createdAt = now, updatedAt = now, source = CraftingSessionSource.MANUAL,
            notes = input.notes.trim().ifEmpty { null }))
    }

    /** Prior pause positions are not recorded, so this is a possible overlap, never a claim of exact duplicated work. */
    fun possiblyOverlaps(candidate: CraftingSession, recorded: List<CraftingSession>, now: Long): Boolean = recorded.any {
        val end = it.endedAt ?: it.pausedAt ?: now
        it.id != candidate.id && it.workedMillis(now) > 0 && candidate.startedAt < end && candidate.endedAt!! > it.startedAt
    }
}
