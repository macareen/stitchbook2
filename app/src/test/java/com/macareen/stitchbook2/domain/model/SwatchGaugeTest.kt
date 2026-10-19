package com.macareen.stitchbook2.domain.model

import org.junit.Assert.*
import org.junit.Test

class SwatchGaugeTest {
    @Test fun tenCentimetresAndFourInchesRemainDistinctPhysicalSpans() {
        val metric = SwatchGauge.fromMeasurement(20.0, 10.0, SwatchLengthUnit.CENTIMETRES)!!
        assertEquals(20.0, metric.perTenCentimetres, 1e-10)
        assertEquals(20.32, metric.perFourInches, 1e-10)
        val imperial = SwatchGauge.fromMeasurement(20.0, 4.0, SwatchLengthUnit.INCHES)!!
        assertEquals(20.0, imperial.perFourInches, 1e-10)
        assertEquals(20.0 / 1.016, imperial.perTenCentimetres, 1e-10)
    }

    @Test fun arbitrarySpansAndFractionalCountsAreSupported() {
        val gauge = SwatchGauge.fromMeasurement(14.5, 5.0, SwatchLengthUnit.CENTIMETRES)!!
        assertEquals(29.0, gauge.perTenCentimetres, 1e-10)
        assertEquals(29.464, gauge.perFourInches, 1e-10)
    }

    @Test fun changingUnitsForTheSamePhysicalSpanKeepsTheGauge() {
        val metric = SwatchGauge.fromMeasurement(37.0, 7.62, SwatchLengthUnit.CENTIMETRES)!!
        val imperial = SwatchGauge.fromMeasurement(37.0, 3.0, SwatchLengthUnit.INCHES)!!
        assertEquals(metric.perTenCentimetres, imperial.perTenCentimetres, 1e-10)
        assertEquals(metric.perFourInches, imperial.perFourInches, 1e-10)
    }

    @Test fun invalidAndUnrepresentableMeasurementsHaveNoResult() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid ->
            assertNull(SwatchGauge.fromMeasurement(invalid, 10.0, SwatchLengthUnit.CENTIMETRES))
            assertNull(SwatchGauge.fromMeasurement(20.0, invalid, SwatchLengthUnit.INCHES))
        }
        assertNull(SwatchGauge.fromMeasurement(Double.MAX_VALUE, 1.0, SwatchLengthUnit.CENTIMETRES))
        assertNull(SwatchGauge.fromMeasurement(20.0, Double.MAX_VALUE, SwatchLengthUnit.INCHES))
        assertNull(SwatchGauge.fromMeasurement(Double.MIN_VALUE, Double.MAX_VALUE, SwatchLengthUnit.CENTIMETRES))
    }
}
