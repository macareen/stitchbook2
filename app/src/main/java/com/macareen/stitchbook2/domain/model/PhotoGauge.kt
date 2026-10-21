package com.macareen.stitchbook2.domain.model

/** Normalized photo coordinates. Counts describe intervals inside the measured rectangle. */
data class GaugeWindow(val left: Float = .15f, val top: Float = .15f, val right: Float = .85f, val bottom: Float = .85f) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        require(left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && right - left >= .049f && bottom - top >= .049f)
    }
}

object PhotoGauge {
    /** Interior lines only; boundaries represent zero and count, rather than extra stitches. */
    fun gridLines(count: Double, offset: Double = 0.0): List<Float> {
        if (!count.isFinite() || count <= 0 || count > 200 || !offset.isFinite() || offset !in 0.0..1.0) return emptyList()
        return (0..201).map { (it + offset) / count }.filter { it > 0 && it < 1 }.map(Double::toFloat)
    }
    fun measure(count: Double, span: Double, unit: SwatchLengthUnit): SwatchGauge? =
        if (count > 200) null else SwatchGauge.fromMeasurement(count, span, unit)
}
