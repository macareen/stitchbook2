package com.macareen.stitchbook2.domain.model

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Why a project's date fields can't be saved as entered. */
enum class ProjectDateError {
    INVALID_START,
    INVALID_TARGET,
    INVALID_COMPLETED,
    COMPLETED_BEFORE_START
}

/**
 * Parses a user-entered ISO-8601 local date ("2026-03-14"). Blank means "no
 * date" and returns null without being an error -- callers distinguish the
 * two with [isValidOptionalLocalDate].
 */
fun parseLocalDateOrNull(text: String?): LocalDate? {
    val trimmed = text?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return try {
        LocalDate.parse(trimmed)
    } catch (_: DateTimeParseException) {
        null
    }
}

fun isValidOptionalLocalDate(text: String?): Boolean =
    text.isNullOrBlank() || parseLocalDateOrNull(text) != null

/**
 * Validates the three optional project dates together. A target date before
 * the start is allowed (plans change; it's informational), but a completion
 * recorded before the project started is almost certainly a typo.
 */
fun validateProjectDates(
    startDate: String?,
    targetDate: String?,
    completedDate: String?
): Set<ProjectDateError> {
    val errors = mutableSetOf<ProjectDateError>()
    if (!isValidOptionalLocalDate(startDate)) errors += ProjectDateError.INVALID_START
    if (!isValidOptionalLocalDate(targetDate)) errors += ProjectDateError.INVALID_TARGET
    if (!isValidOptionalLocalDate(completedDate)) errors += ProjectDateError.INVALID_COMPLETED
    val start = parseLocalDateOrNull(startDate)
    val completed = parseLocalDateOrNull(completedDate)
    if (start != null && completed != null && completed.isBefore(start)) {
        errors += ProjectDateError.COMPLETED_BEFORE_START
    }
    return errors
}
