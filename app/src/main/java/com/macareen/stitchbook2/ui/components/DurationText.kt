package com.macareen.stitchbook2.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.macareen.stitchbook2.R

/** "1 h 05 min", "12 min", or "0 min" -- whole minutes, rounded down. */
@Composable
fun formatDuration(millis: Long): String {
    val totalMinutes = (millis.coerceAtLeast(0L) / 60_000L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}

/** "1:02:03" stopwatch text for a live timer. */
fun formatStopwatch(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, seconds)
}
