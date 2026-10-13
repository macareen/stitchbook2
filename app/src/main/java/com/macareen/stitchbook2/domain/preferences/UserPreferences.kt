package com.macareen.stitchbook2.domain.preferences

import kotlinx.coroutines.flow.StateFlow

/**
 * Small, device-local display preferences (ROADMAP.md Phase 1: "basic local
 * preferences such as theme behavior and measurement display defaults").
 * These are presentation choices, not user content, so they are deliberately
 * not part of the JSON safety export.
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val measurementSystem: MeasurementSystem = MeasurementSystem.IMPERIAL,
    val navigationStyle: BottomNavigationStyle = BottomNavigationStyle.WORDS,
    val timerReviewMinutes: Int = com.macareen.stitchbook2.domain.model.SessionTimeReview.DEFAULT_MINUTES
)

enum class BottomNavigationStyle(val storageValue: String) {
    WORDS("WORDS"), ICONS("ICONS");
    companion object {
        fun fromStorageValue(value: String?): BottomNavigationStyle =
            entries.firstOrNull { it.storageValue == value } ?: WORDS
    }
}

enum class ThemeMode(val storageValue: String) {
    SYSTEM("SYSTEM"),
    LIGHT("LIGHT"),
    DARK("DARK");

    companion object {
        fun fromStorageValue(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

/**
 * Only affects how lengths are *displayed*. Stored values keep their
 * documented canonical units (tool sizes in millimetres, yarn yardage in
 * yards), so switching this never rewrites a record.
 */
enum class MeasurementSystem(val storageValue: String) {
    METRIC("METRIC"),
    IMPERIAL("IMPERIAL");

    companion object {
        fun fromStorageValue(value: String?): MeasurementSystem =
            entries.firstOrNull { it.storageValue == value } ?: IMPERIAL
    }
}

interface UserPreferencesRepository {
    val preferences: StateFlow<UserPreferences>

    fun setTimerReviewMinutes(minutes: Int)

    fun setNavigationStyle(style: BottomNavigationStyle)

    fun setThemeMode(mode: ThemeMode)

    fun setMeasurementSystem(system: MeasurementSystem)
}

private const val METERS_PER_YARD = 0.9144

fun yardsToMeters(yards: Double): Double = yards * METERS_PER_YARD

fun metersToYards(meters: Double): Double = meters / METERS_PER_YARD
