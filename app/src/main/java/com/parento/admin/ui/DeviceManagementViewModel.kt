package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.admin.device.AdminCommand
import com.parento.admin.device.ManagedDeviceRepository
import com.parento.admin.device.ManagedDeviceStatus
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DeviceListUiState {
    data object Loading : DeviceListUiState
    data object Empty : DeviceListUiState
    data class Content(val devices: List<ManagedDeviceStatus>, val nextCursor: String? = null, val loadingMore: Boolean = false) : DeviceListUiState
    data class Error(val message: String, val canRetry: Boolean = true) : DeviceListUiState
}

sealed interface DeviceDetailUiState {
    data object Idle : DeviceDetailUiState
    data object Loading : DeviceDetailUiState
    data object Refreshing : DeviceDetailUiState
    data class Content(
        val status: ManagedDeviceStatus,
        val command: AdminCommand? = null,
        val commandBusy: Boolean = false,
    ) : DeviceDetailUiState
    data class Error(val message: String, val canRetry: Boolean = true) : DeviceDetailUiState
}

class DeviceManagementViewModel(
    private val repository: ManagedDeviceRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    private val _listState = MutableStateFlow<DeviceListUiState>(DeviceListUiState.Loading)
    val listState: StateFlow<DeviceListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow<DeviceDetailUiState>(DeviceDetailUiState.Idle)
    val detailState: StateFlow<DeviceDetailUiState> = _detailState.asStateFlow()

    private var selectedDeviceId: String? = null

    fun loadDevices(refresh: Boolean = false) {
        if (!refresh && _listState.value is DeviceListUiState.Content) return
        _listState.value = DeviceListUiState.Loading
        viewModelScope.launch {
            when (val result = repository.listDevices()) {
                is OperationResult.Success -> applyPage(result.value, append = false)
                is OperationResult.Failure -> handleListFailure(result.error)
            }
        }
    }

    fun loadMoreDevices() {
        val current = _listState.value as? DeviceListUiState.Content ?: return
        val cursor = current.nextCursor ?: return
        if (current.loadingMore) return
        _listState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            when (val result = repository.listDevices(cursor)) {
                is OperationResult.Success -> applyPage(result.value, append = true)
                is OperationResult.Failure -> {
                    if (result.error is AdminError.SessionExpired) onSessionExpired()
                    _listState.value = current.copy(loadingMore = false)
                }
            }
        }
    }

    private fun applyPage(
        page: com.parento.admin.device.DeviceListPage,
        append: Boolean,
    ) {
        val existing = (_listState.value as? DeviceListUiState.Content)?.devices.orEmpty()
        val devices = if (append) existing + page.devices else page.devices
        _listState.value = if (devices.isEmpty()) {
            DeviceListUiState.Empty
        } else {
            DeviceListUiState.Content(
                devices = devices.distinctBy { it.deviceId },
                nextCursor = page.nextCursor,
                loadingMore = false,
            )
        }
    }

    fun openDevice(deviceId: String) {
        val normalized = deviceId.trim()
        if (normalized.isEmpty()) {
            _detailState.value = DeviceDetailUiState.Error("Enter a managed-device ID.")
            return
        }
        if (runCatching { UUID.fromString(normalized) }.isFailure) {
            _detailState.value = DeviceDetailUiState.Error("Enter a valid managed-device UUID.")
            return
        }
        selectedDeviceId = normalized
        _detailState.value = DeviceDetailUiState.Loading
        refreshDevice(normalized)
    }

    fun currentSelectedDeviceStatus(): ManagedDeviceStatus? =
        (_detailState.value as? DeviceDetailUiState.Content)?.status

    fun clearSelection() {
        selectedDeviceId = null
        _detailState.value = DeviceDetailUiState.Idle
    }

    fun refreshSelectedDevice() {
        selectedDeviceId?.let { refreshDevice(it) }
    }

    private fun refreshDevice(deviceId: String) {
        viewModelScope.launch {
            if (_detailState.value is DeviceDetailUiState.Content) {
                _detailState.value = DeviceDetailUiState.Refreshing
            }
            when (val result = repository.getDeviceStatus(deviceId)) {
                is OperationResult.Success -> {
                    val currentCommand = (_detailState.value as? DeviceDetailUiState.Content)?.command
                    _detailState.value = DeviceDetailUiState.Content(result.value, currentCommand)
                }
                is OperationResult.Failure -> handleDetailFailure(result.error)
            }
        }
    }

    fun createFutureCommand() {
        val deviceId = selectedDeviceId ?: return
        val current = _detailState.value as? DeviceDetailUiState.Content ?: return
        _detailState.value = current.copy(commandBusy = true)
        viewModelScope.launch {
            when (val result = repository.createFutureCommand(deviceId, UUID.randomUUID().toString())) {
                is OperationResult.Success ->
                    _detailState.value = current.copy(command = result.value, commandBusy = false)
                is OperationResult.Failure -> {
                    if (result.error is AdminError.SessionExpired) onSessionExpired()
                    _detailState.value = current.copy(
                        commandBusy = false,
                        command = null,
                    )
                    if (result.error is AdminError.InvalidState) {
                        _detailState.value = DeviceDetailUiState.Error("The command could not be created in the current device state.")
                    }
                }
            }
        }
    }

    fun refreshCommand() {
        val deviceId = selectedDeviceId ?: return
        val command = (_detailState.value as? DeviceDetailUiState.Content)?.command ?: return
        viewModelScope.launch {
            when (val result = repository.getCommand(deviceId, command.id)) {
                is OperationResult.Success -> {
                    val current = _detailState.value as? DeviceDetailUiState.Content
                    if (current != null) _detailState.value = current.copy(command = result.value)
                }
                is OperationResult.Failure -> handleDetailFailure(result.error)
            }
        }
    }

    fun cancelCommand() {
        val deviceId = selectedDeviceId ?: return
        val command = (_detailState.value as? DeviceDetailUiState.Content)?.command ?: return
        viewModelScope.launch {
            when (val result = repository.cancelCommand(deviceId, command.id)) {
                is OperationResult.Success -> {
                    val current = _detailState.value as? DeviceDetailUiState.Content
                    if (current != null) _detailState.value = current.copy(command = result.value)
                }
                is OperationResult.Failure -> handleDetailFailure(result.error)
            }
        }
    }

    private fun handleListFailure(error: AdminError) {
        if (error is AdminError.DeviceNotFound) {
            _listState.value = DeviceListUiState.Error("A managed device could not be found.")
        } else {
            if (error is AdminError.SessionExpired) onSessionExpired()
            _listState.value = DeviceListUiState.Error(messageFor(error))
        }
    }

    private fun handleDetailFailure(error: AdminError) {
        if (error is AdminError.SessionExpired) onSessionExpired()
        _detailState.value = DeviceDetailUiState.Error(messageFor(error))
    }

    private fun messageFor(error: AdminError): String = when (error) {
        AdminError.Network -> "Network connection unavailable."
        AdminError.Timeout -> "The request timed out."
        AdminError.Authorization -> "This device operation is not authorized."
        AdminError.SessionExpired, AdminError.SessionRevoked -> "Your administrator session has expired."
        is AdminError.DeviceNotFound -> "The managed device was not found."
        AdminError.InvalidState -> "The requested operation is not valid for the current state."
        AdminError.ServerUnavailable -> "The Parento server is temporarily unavailable."
        AdminError.Validation -> "The request was rejected as invalid."
        else -> "Managed-device information is currently unavailable."
    }
}
