package com.macareen.stitchbook2.data.preferences

import com.macareen.stitchbook2.domain.preferences.BottomNavigationStyle
import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import com.macareen.stitchbook2.domain.preferences.MeasurementSystem
import com.macareen.stitchbook2.domain.preferences.ThemeMode
import com.macareen.stitchbook2.domain.preferences.ToolSizeDisplay
import com.macareen.stitchbook2.domain.preferences.UserPreferences
import com.macareen.stitchbook2.domain.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFERENCES_NAME = "stitchbook_preferences"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_MEASUREMENT_SYSTEM = "measurement_system"
private const val KEY_TOOL_SIZE_DISPLAY = "tool_size_display"

/**
 * Plain platform `SharedPreferences` rather than a new DataStore dependency:
 * These small display choices do not justify another library (AGENTS.md: "add a
 * framework only when a demonstrated need justifies it").
 */
class SharedPreferencesUserPreferencesRepository(context: Context) : UserPreferencesRepository {

    private val sharedPreferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(read())
    override val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    override fun setTimerReviewMinutes(minutes: Int) {
        require(minutes in com.macareen.stitchbook2.domain.model.SessionTimeReview.thresholdRange)
        sharedPreferences.edit { putInt("timer_review_minutes", minutes) }
        _preferences.value = _preferences.value.copy(timerReviewMinutes = minutes)
    }

    override fun setNavigationStyle(style: BottomNavigationStyle) {
        sharedPreferences.edit { putString("navigation_style", style.storageValue) }
        _preferences.value = _preferences.value.copy(navigationStyle = style)
    }

    override fun setThemeMode(mode: ThemeMode) {
        sharedPreferences.edit { putString(KEY_THEME_MODE, mode.storageValue) }
        _preferences.value = _preferences.value.copy(themeMode = mode)
    }

    override fun setMeasurementSystem(system: MeasurementSystem) {
        sharedPreferences.edit { putString(KEY_MEASUREMENT_SYSTEM, system.storageValue) }
        _preferences.value = _preferences.value.copy(measurementSystem = system)
    }

    override fun setToolSizeDisplay(display: ToolSizeDisplay) {
        sharedPreferences.edit { putString(KEY_TOOL_SIZE_DISPLAY, display.storageValue) }
        _preferences.value = _preferences.value.copy(toolSizeDisplay = display)
    }

    private fun read(): UserPreferences = UserPreferences(
        timerReviewMinutes = sharedPreferences.getInt("timer_review_minutes", 30).takeIf { it in 1..1440 } ?: 30,
        navigationStyle = BottomNavigationStyle.fromStorageValue(sharedPreferences.getString("navigation_style", null)),
        themeMode = ThemeMode.fromStorageValue(sharedPreferences.getString(KEY_THEME_MODE, null)),
        measurementSystem = MeasurementSystem.fromStorageValue(
            sharedPreferences.getString(KEY_MEASUREMENT_SYSTEM, null)
        ),
        toolSizeDisplay = ToolSizeDisplay.fromStorageValue(sharedPreferences.getString(KEY_TOOL_SIZE_DISPLAY, null))
    )
}
