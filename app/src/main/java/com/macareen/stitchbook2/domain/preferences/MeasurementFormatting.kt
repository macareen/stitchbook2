package com.macareen.stitchbook2.domain.preferences

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private const val MILLIMETERS_PER_INCH = 25.4

/** Formats a canonical millimetre length for display without changing its stored value. */
fun formatToolLength(millimeters: Double, system: MeasurementSystem): String {
    val value = if (system == MeasurementSystem.METRIC) millimeters else millimeters / MILLIMETERS_PER_INCH
    val unit = if (system == MeasurementSystem.METRIC) "mm" else "in"
    return "${DecimalFormat("0.##", DecimalFormatSymbols(Locale.ROOT)).format(value)} $unit"
}

/** Keeps the recorded label verbatim and changes only which available size is shown first. */
fun formatToolSize(sizeMillimeters: Double?, recordedLabel: String?, display: ToolSizeDisplay): String? {
    val millimeters = sizeMillimeters?.let { formatToolLength(it, MeasurementSystem.METRIC) }
    val label = recordedLabel?.takeIf { it.isNotBlank() }
    return when (display) {
        ToolSizeDisplay.MILLIMETERS_FIRST -> listOfNotNull(millimeters, label)
        ToolSizeDisplay.RECORDED_LABEL_FIRST -> listOfNotNull(label, millimeters)
    }.joinToString(" · ").takeIf { it.isNotEmpty() }
}
