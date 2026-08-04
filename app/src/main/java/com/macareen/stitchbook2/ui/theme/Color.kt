package com.macareen.stitchbook2.ui.theme

import androidx.compose.ui.graphics.Color

// Stitchbook's palette is warm throughout: soft porcelain/ivory surfaces,
// warm charcoal text, and an authored old-rose/dusty-raspberry accent — not
// a generic muted berry, and not a beige/tan wash. No color role uses a cold
// blue-grey Material-default tone. See DESIGN_SYSTEM.md for the reasoning
// behind these choices.

// Light scheme
internal val LightBackground = Color(0xFFFBF8F5)
internal val LightOnBackground = Color(0xFF2A211D)
internal val LightSurface = Color(0xFFFBF8F5)
internal val LightOnSurface = Color(0xFF2A211D)
internal val LightSurfaceVariant = Color(0xFFEDE0D5)
internal val LightOnSurfaceVariant = Color(0xFF6D5D53)
internal val LightSurfaceDim = Color(0xFFE0D2C5)
internal val LightSurfaceBright = Color(0xFFFBF8F5)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFF6F0EA)
internal val LightSurfaceContainer = Color(0xFFEEE1D3)
internal val LightSurfaceContainerHigh = Color(0xFFE6D7C7)
internal val LightSurfaceContainerHighest = Color(0xFFDFCEBC)
internal val LightInverseSurface = Color(0xFF362B27)
internal val LightInverseOnSurface = Color(0xFFF6F0EA)

// Signature accent: an authored old-rose/dusty-raspberry, not a safe
// default berry. This is Stitchbook's one deliberately expressive color.
internal val LightPrimary = Color(0xFF9C3A56)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFF3D7DE)
internal val LightOnPrimaryContainer = Color(0xFF3D0F1C)
internal val LightInversePrimary = Color(0xFFF0B4C0)

// Supporting accent (secondary emphasis, future categories/tags): muted
// plum — restrained, not currently applied decoratively anywhere.
internal val LightSecondary = Color(0xFF6B4B63)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFEEDBE7)
internal val LightOnSecondaryContainer = Color(0xFF2B1526)

// Supporting accent (future progress/category indicators): deep teal —
// restrained, not currently applied decoratively anywhere.
internal val LightTertiary = Color(0xFF2E6664)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFD3E7E4)
internal val LightOnTertiaryContainer = Color(0xFF08201F)

internal val LightError = Color(0xFFA23B34)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFF9DAD4)
internal val LightOnErrorContainer = Color(0xFF410E0A)

internal val LightOutline = Color(0xFF8C796D)
internal val LightOutlineVariant = Color(0xFFDACBBC)
internal val LightScrim = Color(0xFF000000)

// Dark scheme — background/surface and the pale-rose primary treatment are
// intentionally left as-is; only secondary/tertiary and primaryContainer
// shift slightly to stay coordinated with the richer light palette.
internal val DarkBackground = Color(0xFF201A17)
internal val DarkOnBackground = Color(0xFFF1E4DD)
internal val DarkSurface = Color(0xFF201A17)
internal val DarkOnSurface = Color(0xFFF1E4DD)
internal val DarkSurfaceVariant = Color(0xFF4A3E38)
internal val DarkOnSurfaceVariant = Color(0xFFD2C2B9)
internal val DarkSurfaceDim = Color(0xFF201A17)
internal val DarkSurfaceBright = Color(0xFF473A35)
internal val DarkSurfaceContainerLowest = Color(0xFF1A1512)
internal val DarkSurfaceContainerLow = Color(0xFF28211D)
internal val DarkSurfaceContainer = Color(0xFF2D2521)
internal val DarkSurfaceContainerHigh = Color(0xFF38302A)
internal val DarkSurfaceContainerHighest = Color(0xFF433A34)
internal val DarkInverseSurface = Color(0xFFF1E4DD)
internal val DarkInverseOnSurface = Color(0xFF201A17)

internal val DarkPrimary = Color(0xFFE8AEBA)
internal val DarkOnPrimary = Color(0xFF4A1420)
internal val DarkPrimaryContainer = Color(0xFF6B2E40)
internal val DarkOnPrimaryContainer = Color(0xFFFFD9E1)
internal val DarkInversePrimary = Color(0xFF9C3A56)

internal val DarkSecondary = Color(0xFFD4B7CE)
internal val DarkOnSecondary = Color(0xFF3B2140)
internal val DarkSecondaryContainer = Color(0xFF52394E)
internal val DarkOnSecondaryContainer = Color(0xFFEEDBE7)

internal val DarkTertiary = Color(0xFF9BCBC7)
internal val DarkOnTertiary = Color(0xFF073332)
internal val DarkTertiaryContainer = Color(0xFF204E4C)
internal val DarkOnTertiaryContainer = Color(0xFFD3E7E4)

internal val DarkError = Color(0xFFF2B7B0)
internal val DarkOnError = Color(0xFF601410)
internal val DarkErrorContainer = Color(0xFF7F3229)
internal val DarkOnErrorContainer = Color(0xFFF9DAD4)

internal val DarkOutline = Color(0xFFA08D83)
internal val DarkOutlineVariant = Color(0xFF4F423C)
internal val DarkScrim = Color(0xFF000000)
