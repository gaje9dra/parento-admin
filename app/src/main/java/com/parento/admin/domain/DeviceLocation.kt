package com.parento.admin.domain

enum class LocationAvailability {
    NEVER_REPORTED,
    AVAILABLE,
    UNAVAILABLE,
    REVOKED,
    UNKNOWN,
}

enum class LocationFreshness {
    FRESH,
    STALE,
    VERY_STALE,
    UNKNOWN,
}

data class DeviceLocation(
    val deviceId: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Double?,
    val altitudeMeters: Double?,
    val bearingDegrees: Double?,
    val speedMetersPerSecond: Double?,
    val observedAtEpochMillis: Long?,
    val receivedAtEpochMillis: Long?,
    val availability: LocationAvailability,
    val freshness: LocationFreshness,
) {
    fun hasValidCoordinates(): Boolean =
        latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}