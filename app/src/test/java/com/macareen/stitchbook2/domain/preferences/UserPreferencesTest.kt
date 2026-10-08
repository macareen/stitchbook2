package com.macareen.stitchbook2.domain.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesTest {

    @Test
    fun unknownStoredValuesFallBackToDefaults() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue("SEPIA"))
        assertEquals(MeasurementSystem.IMPERIAL, MeasurementSystem.fromStorageValue("FURLONGS"))
    }

    @Test
    fun storedValuesRoundTrip() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStorageValue(it.storageValue)) }
        MeasurementSystem.entries.forEach {
            assertEquals(it, MeasurementSystem.fromStorageValue(it.storageValue))
        }
    }

    @Test
    fun yardMeterConversionRoundTrips() {
        assertEquals(201.168, yardsToMeters(220.0), 0.0001)
        assertEquals(220.0, metersToYards(yardsToMeters(220.0)), 0.0001)
    }
}
