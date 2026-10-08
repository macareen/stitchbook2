package com.macareen.stitchbook2.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.macareen.stitchbook2.domain.preferences.MeasurementSystem

/**
 * The user's display-only measurement preference, provided once at the
 * activity root so any screen can format lengths without each ViewModel
 * depending on the preferences repository.
 */
val LocalMeasurementSystem = staticCompositionLocalOf { MeasurementSystem.IMPERIAL }
