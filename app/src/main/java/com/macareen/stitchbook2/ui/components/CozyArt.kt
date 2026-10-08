package com.macareen.stitchbook2.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macareen.stitchbook2.R
import com.macareen.stitchbook2.ui.theme.Pastel

/**
 * A hand-drawn-style skein: two yarn loops held by a cream band with a small
 * heart. Drawn on a 100 × 120 grid and scaled to the given width.
 */
@Composable
fun SkeinArt(
    pastel: Pastel,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp
) {
    Canvas(modifier = modifier.size(width = width, height = width * 1.2f)) {
        val unit = size.width / 100f
        fun o(x: Float, y: Float) = Offset(x * unit, y * unit)
        drawOval(pastel.yarn, topLeft = o(10f, 4f), size = Size(80f * unit, 56f * unit))
        drawOval(pastel.yarn, topLeft = o(10f, 60f), size = Size(80f * unit, 56f * unit))
        drawRoundRect(
            color = Color(0xFFFBF3EC),
            topLeft = o(14f, 44f),
            size = Size(72f * unit, 32f * unit),
            cornerRadius = CornerRadius(8f * unit)
        )
        val heart = Path().apply {
            moveTo(50f * unit, 68f * unit)
            cubicTo(44f * unit, 63f * unit, 40f * unit, 60f * unit, 40f * unit, 56f * unit)
            cubicTo(40f * unit, 52f * unit, 44f * unit, 50f * unit, 47f * unit, 52f * unit)
            cubicTo(48.5f * unit, 53f * unit, 49.5f * unit, 54f * unit, 50f * unit, 55f * unit)
            cubicTo(50.5f * unit, 54f * unit, 51.5f * unit, 53f * unit, 53f * unit, 52f * unit)
            cubicTo(56f * unit, 50f * unit, 60f * unit, 52f * unit, 60f * unit, 56f * unit)
            cubicTo(60f * unit, 60f * unit, 56f * unit, 63f * unit, 50f * unit, 68f * unit)
            close()
        }
        drawPath(heart, pastel.ink)
    }
}

/** The app's heart-shaped yarn ball, as a small decorative mark. */
@Composable
fun HeartYarnBall(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Image(
        painter = painterResource(R.drawable.art_heart_yarn),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}
