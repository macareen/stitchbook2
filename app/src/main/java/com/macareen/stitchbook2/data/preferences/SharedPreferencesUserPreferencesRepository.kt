package com.macareen.stitchbook2.data.preferences

import com.macareen.stitchbook2.domain.preferences.BottomNavigationStyle
import android.content.Context
import android.content.SharedPreferences
import com.macareen.stitchbook2.domain.preferences.MeasurementSystem
import com.macareen.stitchbook2.domain.preferences.ThemeMode
import com.macareen.stitchbook2.domain.preferences.UserPreferences
import com.macareen.stitchbook2.domain.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFERENCES_NAME = "stitchbook_preferences"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_MEASUREMENT_SYSTEM = "measurement_system"

/**
 * Plain platform `SharedPreferences` rather than a new DataStore dependency:
 * three enum values don't justify another library (AGENTS.md: "add a
 * framework only when a demonstrated need justifies it").
 */
class SharedPreferencesUserPreferencesRepository(context: Context) : UserPreferencesRepository {

    private val sharedPreferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(read())
    override val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    override fun setNavigationStyle(style: BottomNavigationStyle) {
        sharedPreferences.edit().putString("navigation_style", style.storageValue).apply()
        _preferences.value = _preferences.value.copy(navigationStyle = style)
    }

    override fun setThemeMode(mode: ThemeMode) {
        sharedPreferences.edit().putString(KEY_THEME_MODE, mode.storageValue).apply()
        _preferences.value = _preferences.value.copy(themeMode = mode)
    }

    override fun setMeasurementSystem(system: MeasurementSystem) {
        sharedPreferences.edit().putString(KEY_MEASUREMENT_SYSTEM, system.storageValue).apply()
        _preferences.value = _preferences.value.copy(measurementSystem = system)
    }

    private fun read(): UserPreferences = UserPreferences(
        navigationStyle = BottomNavigationStyle.fromStorageValue(sharedPreferences.getString("navigation_style", null)),
        themeMode = ThemeMode.fromStorageValue(sharedPreferences.getString(KEY_THEME_MODE, null)),
        measurementSystem = MeasurementSystem.fromStorageValue(
            sharedPreferences.getString(KEY_MEASUREMENT_SYSTEM, null)
        )
    )
}
