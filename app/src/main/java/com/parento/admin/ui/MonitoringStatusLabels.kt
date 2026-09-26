package com.parento.admin.ui

import com.parento.admin.device.ManagementMode
import com.parento.admin.device.MonitoringFreshness
import com.parento.admin.domain.ConnectionState

object MonitoringStatusLabels {
    fun freshness(value: MonitoringFreshness): String = when (value) {
        MonitoringFreshness.CURRENT -> "Fresh"
        MonitoringFreshness.STALE -> "Stale"
        MonitoringFreshness.VERY_STALE -> "Very stale"
        MonitoringFreshness.NEVER_REPORTED -> "Never reported"
        MonitoringFreshness.DISCONNECTED -> "Offline"
        MonitoringFreshness.REVOKED -> "Revoked"
        MonitoringFreshness.UNKNOWN -> "Unknown"
    }

    fun connection(value: ConnectionState): String = when (value) {
        ConnectionState.CONNECTED -> "Connected"
        ConnectionState.DISCONNECTED -> "Disconnected"
        ConnectionState.CONNECTING -> "Connecting"
        ConnectionState.RECONNECTING -> "Reconnecting"
        ConnectionState.ERROR -> "Unavailable"
    }

    fun management(value: ManagementMode): String = when (value) {
        ManagementMode.UNMANAGED -> "Unmanaged"
        ManagementMode.PROFILE_OWNER -> "Profile Owner"
        ManagementMode.DEVICE_OWNER -> "Device Owner"
        ManagementMode.UNKNOWN -> "Unknown"
    }
}
