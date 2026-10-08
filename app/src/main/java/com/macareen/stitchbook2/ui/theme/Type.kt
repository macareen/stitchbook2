package com.macareen.stitchbook2.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.macareen.stitchbook2.R

// Headings and the current-instruction role use Fraunces, a soft old-style
// serif; everything else (labels, body, buttons) uses Nunito, a rounded sans
// that stays legible at small sizes. Both are bundled variable fonts (SIL OFL,
// licence texts in assets/licenses) so the cozy look works fully offline.
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, vararg weights: FontWeight): FontFamily =
    FontFamily(
        weights.map { weight ->
            Font(
                resId = resId,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
            )
        }
    )

private val ReadingFontFamily = variableFamily(
    R.font.fraunces,
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold
)
private val UiFontFamily = variableFamily(
    R.font.nunito,
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold
)

// Set of Material typography styles, tuned for Stitchbook's reading-first
// personality. See DESIGN_SYSTEM.md for the semantic-role mapping.
val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = ReadingFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ReadingFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ReadingFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ReadingFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp
    ),
    labelLarge = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp
    ),
    labelMedium = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Semantic typography roles layered over [Typography]'s Material3 slots.
 * Call sites should reach for these names (`MaterialTheme.typography.instruction`,
 * not `.headlineMedium`) so the reading-first intent behind each role stays
 * legible at the call site. See DESIGN_SYSTEM.md for the full hierarchy.
 */
val Typography.screenTitle: TextStyle
    get() = titleLarge

val Typography.sectionLabel: TextStyle
    get() = labelLarge

val Typography.instruction: TextStyle
    get() = headlineMedium

val Typography.metadata: TextStyle
    get() = bodyMedium

val Typography.buttonLabel: TextStyle
    get() = labelLarge

/** The editorial, serif title used at the top of a list/grid card (a project, guide, pattern, or stash item). */
val Typography.cardTitle: TextStyle
    get() = titleLarge
