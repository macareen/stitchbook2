package com.macareen.stitchbook2.domain.model

import org.junit.Assert.*
import org.junit.Test

class PhotoGaugeTest {
    @Test fun boundariesDoNotAddExtraStitches() {
        assertEquals(listOf(.25f, .5f, .75f), PhotoGauge.gridLines(4.0))
        assertEquals(listOf(.125f, .375f, .625f, .875f), PhotoGauge.gridLines(4.0, .5))
        assertEquals(4, PhotoGauge.gridLines(4.5).size)
    }
    @Test fun invalidAndUnboundedGridCountsCannotAllocateLines() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0, 201.0).forEach { assertTrue(PhotoGauge.gridLines(it).isEmpty()) }
        assertTrue(PhotoGauge.gridLines(20.0, Double.NaN).isEmpty())
        assertNull(PhotoGauge.measure(201.0, 10.0, SwatchLengthUnit.CENTIMETRES))
        assertNull(PhotoGauge.measure(20.0, 0.0, SwatchLengthUnit.CENTIMETRES))
    }
    @Test fun independentPhysicalCalibrationAndFractionalCountsUseCorrectUnits() {
        assertEquals(20.0, PhotoGauge.measure(20.0, 10.0, SwatchLengthUnit.CENTIMETRES)!!.perTenCentimetres, .0001)
        assertEquals(22.5, PhotoGauge.measure(22.5, 4.0, SwatchLengthUnit.INCHES)!!.perFourInches, .0001)
        assertThrows(IllegalArgumentException::class.java) { GaugeWindow(right = .16f) }
        assertThrows(IllegalArgumentException::class.java) { GaugeWindow(top = Float.NaN) }
    }
}
