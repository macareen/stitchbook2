package com.macareen.stitchbook2.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Semantic aliases over [ColorScheme]'s Material3 roles. Call sites should
 * prefer these names over reaching for [ColorScheme.onBackground] or
 * [ColorScheme.surfaceVariant] directly, so intent stays legible without
 * inventing a second, parallel color system to keep in sync.
 */
val ColorScheme.textPrimary: Color
    get() = onBackground

val ColorScheme.textSecondary: Color
    get() = onSurfaceVariant

val ColorScheme.surfaceSubtle: Color
    get() = surfaceVariant
