package com.macareen.stitchbook2.domain.model

enum class SwatchLengthUnit { CENTIMETRES, INCHES }

/** Gauge calculated from the user's count over a physically measured span. */
data class SwatchGauge(val perTenCentimetres: Double, val perFourInches: Double) {
    companion object {
        /** Counts may include part of a stitch, row, round, pass or motif. */
        fun fromMeasurement(count: Double, span: Double, unit: SwatchLengthUnit): SwatchGauge? {
            if (!count.isFinite() || count <= 0 || !span.isFinite() || span <= 0) return null
            val centimetres = when (unit) {
                SwatchLengthUnit.CENTIMETRES -> span
                SwatchLengthUnit.INCHES -> span * 2.54
            }
            if (!centimetres.isFinite() || centimetres <= 0) return null
            val perCentimetre = count / centimetres
            val perTen = perCentimetre * 10
            val perFour = perCentimetre * 10.16
            if (!perTen.isFinite() || perTen <= 0 || !perFour.isFinite() || perFour <= 0) return null
            return SwatchGauge(perTen, perFour)
        }
    }
}
