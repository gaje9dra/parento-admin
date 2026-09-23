package com.parento.admin.domain

/**
 * Admin-side representation of a managed device.
 *
 * This is a local domain contract only; it is not backed by a live API in Phase 1.2.
 */
data class ManagedDevice(
    val deviceId: String,
    val displayName: String,
    val connectionState: ConnectionState,
    val enrollmentState: EnrollmentState,
    val status: DeviceStatus,
    val batteryPercent: Int? = null,
    val lastSynchronizedAtEpochMillis: Long? = null
)
