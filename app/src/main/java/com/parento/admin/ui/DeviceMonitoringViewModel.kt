package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.DeviceFreshness
import com.parento.admin.domain.DeviceMonitoringRepository
import com.parento.admin.domain.ManagedDeviceMonitoring
import com.parento.admin.domain.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceMonitoringViewModel(
    private val repository: DeviceMonitoringRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<DeviceMonitoringUiState>(DeviceMonitoringUiState.Loading)
    val state: StateFlow<DeviceMonitoringUiState> = _state.asStateFlow()

    fun load() {
        if (_state.value is DeviceMonitoringUiState.Content || _state.value is DeviceMonitoringUiState.Stale) return
        refreshInternal()
    }

    fun refresh() {
        refreshInternal()
    }

    private fun refreshInternal() {
        _state.value = DeviceMonitoringUiState.Loading
        viewModelScope.launch {
            when (val result = repository.refresh()) {
                is OperationResult.Success -> publish(result.value)
                is OperationResult.Failure -> _state.value = mapError(result.error)
            }
        }
    }

    private fun publish(devices: List<ManagedDeviceMonitoring>) {
        if (devices.isEmpty()) {
            _state.value = DeviceMonitoringUiState.Empty
            return
        }
        val hasStale = devices.any {
            it.freshness == DeviceFreshness.STALE || it.freshness == DeviceFreshness.VERY_STALE
        }
        _state.value = if (hasStale) DeviceMonitoringUiState.Stale(devices) else DeviceMonitoringUiState.Content(devices)
    }

    private fun mapError(error: AdminError): DeviceMonitoringUiState = when (error) {
        AdminError.Authorization -> DeviceMonitoringUiState.Unauthorized("The backend rejected access to device monitoring data.")
        AdminError.SessionExpired,
        AdminError.SessionRevoked,
        AdminError.Authentication -> DeviceMonitoringUiState.SessionExpired("The administrator session must be restored before monitoring data can be loaded.")
        AdminError.Network,
        AdminError.Timeout,
        AdminError.ServerUnavailable -> DeviceMonitoringUiState.Offline("The monitoring service is unavailable. Existing cached data is not presented as live data.")
        AdminError.Backend -> DeviceMonitoringUiState.UnavailableDependency("The authenticated Admin device-monitoring API is not available in the current backend contract.")
        is AdminError.DeviceNotFound -> DeviceMonitoringUiState.Error("The requested device could not be found.")
        else -> DeviceMonitoringUiState.Error("Device monitoring could not be loaded. Please try again.")
    }
}
