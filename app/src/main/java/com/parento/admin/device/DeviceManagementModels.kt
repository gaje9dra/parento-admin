package com.parento.admin.device

import com.parento.admin.domain.ConnectionState
import com.parento.admin.domain.DeviceStatus
import com.parento.admin.domain.EnrollmentState

enum class ManagementMode { UNMANAGED, PROFILE_OWNER, DEVICE_OWNER, UNKNOWN }
enum class MonitoringFreshness { CURRENT, STALE, VERY_STALE, NEVER_REPORTED, DISCONNECTED, REVOKED, UNKNOWN }

data class DeviceMonitoring(
    val androidVersion: String,
    val apiLevel: Int,
    val appVersion: String,
    val appVersionCode: Int,
    val managementMode: ManagementMode,
    val batteryPercentage: Int?,
    val chargingState: String,
    val batteryStatus: String,
    val networkState: String,
    val storageTotalBytes: Long?,
    val storageAvailableBytes: Long?,
    val storageUsedBytes: Long?,
    val memoryTotalBytes: Long?,
    val memoryAvailableBytes: Long?,
    val memoryLow: Boolean?,
    val lastSuccessfulInitializationAt: String?,
    val lastSuccessfulCommunicationAt: String?,
    val lastMonitoringUpdateAt: String,
)

data class ManagedDeviceStatus(
    val deviceId: String,
    val displayName: String,
    val enrollmentState: EnrollmentState,
    val deviceStatus: DeviceStatus,
    val connectionState: ConnectionState,
    val lastSeenAt: String?,
    val lastSeenAgeMs: Long?,
    val expiresAt: String?,
    val monitoringFreshness: MonitoringFreshness,
    val monitoring: DeviceMonitoring?,
    val nextCursor: String? = null,
)

enum class AdminCommandType(val wireValue: String) {
    FUTURE_COMMAND("FUTURE_COMMAND"),
}

enum class CommandStatus {
    CREATED, QUEUED, DELIVERING, DELIVERED, ACKNOWLEDGED, RUNNING,
    SUCCEEDED, FAILED, EXPIRED, CANCELLED, REJECTED
}

data class AdminCommand(
    val id: String,
    val deviceId: String,
    val type: AdminCommandType,
    val version: Int,
    val status: CommandStatus,
    val createdAt: String,
    val expiresAt: String,
    val deliveryAt: String?,
    val acknowledgedAt: String?,
    val startedAt: String?,
    val completedAt: String?,
    val cancelledAt: String?,
    val failureCode: String?,
    val errorCategory: String?,
    val resultCode: String?,
)

data class EnrollmentDeviceReference(
    val deviceId: String,
    val enrollmentStatus: String,
)

data class DeviceListPage(
    val devices: List<ManagedDeviceStatus>,
    val nextCursor: String?,
)
