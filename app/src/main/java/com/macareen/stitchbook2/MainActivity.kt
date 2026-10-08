package com.macareen.stitchbook2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macareen.stitchbook2.domain.preferences.ThemeMode
import com.macareen.stitchbook2.ui.components.LocalMeasurementSystem
import com.macareen.stitchbook2.ui.theme.StitchbookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferencesRepository = (application as StitchbookApplication).container.userPreferencesRepository
        setContent {
            val preferences by preferencesRepository.preferences.collectAsStateWithLifecycle()
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            StitchbookTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalMeasurementSystem provides preferences.measurementSystem) {
                    StitchbookApp()
                }
            }
        }
    }
}
