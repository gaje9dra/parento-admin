package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.admin.device.ManagedDeviceStatus
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.ConnectionState
import com.parento.admin.domain.DeviceStatus
import com.parento.admin.domain.EnrollmentState
import com.parento.admin.domain.OperationResult
import com.parento.admin.screensharing.ScreenSharingRepository
import com.parento.admin.screensharing.ScreenSharingSession
import com.parento.admin.screensharing.ScreenSharingSessionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ScreenSharingUiState {
    data object Idle : ScreenSharingUiState
    data object Starting : ScreenSharingUiState
    data class Session(val value: ScreenSharingSession) : ScreenSharingUiState
    data class Error(val message: String, val canRetry: Boolean = true) : ScreenSharingUiState
}

class ScreenSharingViewModel(
    private val repository: ScreenSharingRepository,
    private val onSessionExpired: () -> Unit,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val _uiState = MutableStateFlow<ScreenSharingUiState>(ScreenSharingUiState.Idle)
    val uiState: StateFlow<ScreenSharingUiState> = _uiState.asStateFlow()

    private var selectedDevice: ManagedDeviceStatus? = null
    private var sessionId: String? = null
    private var pollJob: Job? = null
    private var operationBusy = false

    fun bindDevice(device: ManagedDeviceStatus) {
        selectedDevice = device
    }

    fun clearDevice() {
        selectedDevice = null
        sessionId = null
        pollJob?.cancel()
        pollJob = null
        _uiState.value = ScreenSharingUiState.Idle
    }

    fun start() {
        val device = selectedDevice ?: return
        if (operationBusy || _uiState.value is ScreenSharingUiState.Starting) return
        val validationError = validateDevice(device)
        if (validationError != null) {
            _uiState.value = ScreenSharingUiState.Error(validationError, canRetry = false)
            return
        }
        operationBusy = true
        _uiState.value = ScreenSharingUiState.Starting
        viewModelScope.launch {
            when (val result = repository.createSession(device.deviceId, UUID.randomUUID().toString())) {
                is OperationResult.Success -> {
                    sessionId = result.value.sessionId
                    operationBusy = false
                    _uiState.value = ScreenSharingUiState.Session(result.value)
                    startPolling()
                }
                is OperationResult.Failure -> {
                    operationBusy = false
                    handleFailure(result.error)
                }
            }
        }
    }

    fun refresh() {
        val id = sessionId ?: return
        if (operationBusy) return
        viewModelScope.launch {
            when (val result = repository.getSession(id)) {
                is OperationResult.Success -> {
                    _uiState.value = ScreenSharingUiState.Session(result.value)
                    if (result.value.status.isTerminal) {
                        pollJob?.cancel()
                    }
                }
                is OperationResult.Failure -> handleFailure(result.error)
            }
        }
    }

    fun stop() {
        val id = sessionId ?: return
        val current = (_uiState.value as? ScreenSharingUiState.Session)?.value ?: return
        if (operationBusy || current.status.isTerminal) return
        operationBusy = true
        viewModelScope.launch {
            when (val result = repository.stopSession(id)) {
                is OperationResult.Success -> {
                    operationBusy = false
                    _uiState.value = ScreenSharingUiState.Session(result.value)
                    if (result.value.status.isTerminal) pollJob?.cancel()
                }
                is OperationResult.Failure -> {
                    operationBusy = false
                    handleFailure(result.error)
                }
            }
        }
    }

    fun onBackground() {
        pollJob?.cancel()
        pollJob = null
    }

    fun onForeground() {
        if (sessionId != null) reconcile()
    }

    fun reconcile() {
        val id = sessionId ?: return
        viewModelScope.launch {
            when (val result = repository.getSession(id)) {
                is OperationResult.Success -> {
                    _uiState.value = ScreenSharingUiState.Session(result.value)
                    if (!result.value.status.isTerminal) startPolling()
                }
                is OperationResult.Failure -> handleFailure(result.error)
            }
        }
    }

    fun isExpiredNow(): Boolean {
        val session = (_uiState.value as? ScreenSharingUiState.Session)?.value ?: return false
        return runCatching {
            java.time.Instant.parse(session.expiresAt).toEpochMilli() <= nowEpochMillis()
        }.getOrDefault(false)
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val id = sessionId ?: break
                when (val result = repository.getSession(id)) {
                    is OperationResult.Success -> {
                        _uiState.value = ScreenSharingUiState.Session(result.value)
                        if (result.value.status.isTerminal) break
                    }
                    is OperationResult.Failure -> {
                        if (result.error is AdminError.SessionExpired) {
                            onSessionExpired()
                            break
                        }
                        _uiState.value = ScreenSharingUiState.Error(messageFor(result.error))
                        break
                    }
                }
            }
        }
    }

    private fun validateDevice(device: ManagedDeviceStatus): String? {
        if (device.enrollmentState != EnrollmentState.ENROLLED) return "Screen sharing is available only for enrolled devices."
        if (device.deviceStatus == DeviceStatus.REVOKED || device.enrollmentState == EnrollmentState.REVOKED) {
            return "This managed device has been revoked."
        }
        if (device.connectionState != ConnectionState.CONNECTED) {
            return "The managed device must have an active communication session."
        }
        if (device.deviceStatus !in setOf(DeviceStatus.AVAILABLE, DeviceStatus.CONNECTED)) {
            return "The managed device is not available for screen sharing."
        }
        return null
    }

    private fun handleFailure(error: AdminError) {
        if (error is AdminError.SessionExpired || error is AdminError.SessionRevoked) {
            onSessionExpired()
            return
        }
        _uiState.value = ScreenSharingUiState.Error(messageFor(error))
    }

    private fun messageFor(error: AdminError): String = when (error) {
        AdminError.Authorization -> "You are not authorized to view this device screen."
        AdminError.Network -> "Network connection unavailable."
        AdminError.Timeout -> "The screen-sharing request timed out."
        AdminError.InvalidState -> "Screen sharing is not available in the current session state."
        is AdminError.DeviceNotFound -> "The managed device was not found."
        AdminError.ServerUnavailable -> "The Parento server is temporarily unavailable."
        AdminError.Validation -> "The screen-sharing request was rejected as invalid."
        else -> "Screen sharing is currently unavailable."
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
