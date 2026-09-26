package com.parento.admin.location

import com.parento.admin.domain.DeviceLocation
import com.parento.admin.domain.LocationAvailability
import com.parento.admin.domain.LocationFreshness
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLocationTest {
    private fun location(latitude: Double, longitude: Double) = DeviceLocation(
        deviceId = "device-1",
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 25.0,
        altitudeMeters = null,
        bearingDegrees = null,
        speedMetersPerSecond = null,
        observedAtEpochMillis = 1_000L,
        receivedAtEpochMillis = 2_000L,
        availability = LocationAvailability.AVAILABLE,
        freshness = LocationFreshness.FRESH,
    )

    @Test
    fun validCoordinatesAreAccepted() {
        assertTrue(location(26.9124, 75.7873).hasValidCoordinates())
    }

    @Test
    fun invalidLatitudeIsRejectedWithoutClamping() {
        assertFalse(location(91.0, 75.7873).hasValidCoordinates())
    }

    @Test
    fun invalidLongitudeIsRejectedWithoutClamping() {
        assertFalse(location(26.9124, 181.0).hasValidCoordinates())
    }

    @Test
    fun zeroCoordinatesRemainValidCoordinates() {
        assertTrue(location(0.0, 0.0).hasValidCoordinates())
    }
}