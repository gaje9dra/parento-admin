package com.parento.admin.domain

/**
 * Authoritative monitoring information returned by the Admin/backend boundary.
 * Nullable values intentionally represent unavailable or never-reported data.
 */
data class ManagedDeviceMonitoring(
    val device: ManagedDevice,
    val managementMode: ManagementMode = ManagementMode.UNKNOWN,
    val androidVersion: String? = null,
    val apiLevel: Int? = null,
    val appVersion: String? = null,
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val batteryObservedAtEpochMillis: Long? = null,
    val networkState: NetworkState = NetworkState.UNKNOWN,
    val networkTransport: NetworkTransport = NetworkTransport.UNKNOWN,
    val networkObservedAtEpochMillis: Long? = null,
    val storageTotalBytes: Long? = null,
    val storageUsedBytes: Long? = null,
    val storageAvailableBytes: Long? = null,
    val memoryTotalBytes: Long? = null,
    val memoryAvailableBytes: Long? = null,
    val memoryObservedAtEpochMillis: Long? = null,
    val lastSeenAtEpochMillis: Long? = null,
    val lastTelemetryAtEpochMillis: Long? = null,
    val lastConnectedAtEpochMillis: Long? = null,
    val lastSuccessfulSyncAtEpochMillis: Long? = null,
    val freshness: DeviceFreshness = DeviceFreshness.UNKNOWN,
)

enum class DeviceFreshness { FRESH, STALE, VERY_STALE, NEVER_REPORTED, OFFLINE, REVOKED, UNKNOWN }
enum class ManagementMode { UNMANAGED, PROFILE_OWNER, DEVICE_OWNER, UNKNOWN }
enum class NetworkState { ONLINE, OFFLINE, UNKNOWN }
enum class NetworkTransport { WIFI, CELLULAR, UNKNOWN }
