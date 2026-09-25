package com.parento.admin.ui

import com.parento.admin.domain.ManagedDeviceMonitoring

sealed interface DeviceMonitoringUiState {
    data object Loading : DeviceMonitoringUiState
    data class Content(val devices: List<ManagedDeviceMonitoring>) : DeviceMonitoringUiState
    data object Empty : DeviceMonitoringUiState
    data class Stale(val devices: List<ManagedDeviceMonitoring>) : DeviceMonitoringUiState
    data class Offline(val message: String) : DeviceMonitoringUiState
    data class Unauthorized(val message: String) : DeviceMonitoringUiState
    data class SessionExpired(val message: String) : DeviceMonitoringUiState
    data class UnavailableDependency(val message: String) : DeviceMonitoringUiState
    data class Error(val message: String) : DeviceMonitoringUiState
}
