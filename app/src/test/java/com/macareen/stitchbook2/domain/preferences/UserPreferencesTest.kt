package com.macareen.stitchbook2.domain.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesTest {

    @Test
    fun unknownStoredValuesFallBackToDefaults() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue("SEPIA"))
        assertEquals(MeasurementSystem.METRIC, MeasurementSystem.fromStorageValue("FURLONGS"))
        assertEquals(ToolSizeDisplay.MILLIMETERS_FIRST, ToolSizeDisplay.fromStorageValue("UNKNOWN"))
        assertEquals(MeasurementSystem.METRIC, UserPreferences().measurementSystem)
        assertEquals(ToolSizeDisplay.MILLIMETERS_FIRST, UserPreferences().toolSizeDisplay)
    }

    @Test
    fun storedValuesRoundTrip() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStorageValue(it.storageValue)) }
        MeasurementSystem.entries.forEach {
            assertEquals(it, MeasurementSystem.fromStorageValue(it.storageValue))
        }
        ToolSizeDisplay.entries.forEach { assertEquals(it, ToolSizeDisplay.fromStorageValue(it.storageValue)) }
    }

    @Test
    fun yardMeterConversionRoundTrips() {
        assertEquals(201.168, yardsToMeters(220.0), 0.0001)
        assertEquals(220.0, metersToYards(yardsToMeters(220.0)), 0.0001)
    }

    @Test
    fun toolLengthAndRecordedSizeLabelsFollowIndependentDisplayPreferences() {
        assertEquals("25.4 mm", formatToolLength(25.4, MeasurementSystem.METRIC))
        assertEquals("1 in", formatToolLength(25.4, MeasurementSystem.IMPERIAL))
        assertEquals("4.5 mm · US 7", formatToolSize(4.5, "US 7", ToolSizeDisplay.MILLIMETERS_FIRST))
        assertEquals("US 7 · 4.5 mm", formatToolSize(4.5, "US 7", ToolSizeDisplay.RECORDED_LABEL_FIRST))
        assertEquals("US 7", formatToolSize(null, "US 7", ToolSizeDisplay.MILLIMETERS_FIRST))
        assertEquals("4 mm", formatToolSize(4.0, null, ToolSizeDisplay.RECORDED_LABEL_FIRST))
    }
}
