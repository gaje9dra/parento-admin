package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.admin.audio.AudioAccessRepository
import com.parento.admin.audio.AudioAccessSession
import com.parento.admin.audio.AudioPlaybackController
import com.parento.admin.audio.AudioPlaybackState
import com.parento.admin.audio.AudioTransport
import com.parento.admin.audio.UnavailableAudioTransport
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.ConnectionState
import com.parento.admin.domain.DeviceStatus
import com.parento.admin.domain.EnrollmentState
import com.parento.admin.domain.OperationResult
import com.parento.admin.device.ManagedDeviceStatus
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AudioAccessUiState {
    data object Idle : AudioAccessUiState
    data object Loading : AudioAccessUiState
    data class Session(
        val value: AudioAccessSession,
        val playback: AudioPlaybackState,
    ) : AudioAccessUiState
    data class Error(val message: String, val canRetry: Boolean = true) : AudioAccessUiState
}

class AudioAccessViewModel(
    private val repository: AudioAccessRepository,
    private val transport: AudioTransport = UnavailableAudioTransport(),
    private val playback: AudioPlaybackController = com.parento.admin.audio.SessionBoundAudioPlaybackController(),
    private val onSessionExpired: () -> Unit,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val _uiState = MutableStateFlow<AudioAccessUiState>(AudioAccessUiState.Idle)
    val uiState: StateFlow<AudioAccessUiState> = _uiState.asStateFlow()

    private var selectedDevice: ManagedDeviceStatus? = null
    private var sessionId: String? = null
    private var pollJob: Job? = null
    private var operationBusy = false

    fun bindDevice(device: ManagedDeviceStatus) {
        selectedDevice = device
    }

    fun clearDevice() {
        pollJob?.cancel()
        pollJob = null
        viewModelScope.launch { stopLocalPlayback() }
        selectedDevice = null
        sessionId = null
        _uiState.value = AudioAccessUiState.Idle
    }

    fun start() {
        val device = selectedDevice ?: return
        if (operationBusy || _uiState.value is AudioAccessUiState.Loading) return
        validateDevice(device)?.let {
            _uiState.value = AudioAccessUiState.Error(it, canRetry = false)
            return
        }
        operationBusy = true
        _uiState.value = AudioAccessUiState.Loading
        viewModelScope.launch {
            when (val result = repository.createSession(device.deviceId, UUID.randomUUID().toString())) {
                is OperationResult.Success -> {
                    operationBusy = false
                    sessionId = result.value.sessionId
                    _uiState.value = AudioAccessUiState.Session(result.value, AudioPlaybackState.IDLE)
                    startPolling()
                }
                is OperationResult.Failure -> {
                    operationBusy = false
                    handleFailure(result.error)
                }
            }
        }
    }

    fun stop() {
        val id = sessionId ?: return
        val current = (_uiState.value as? AudioAccessUiState.Session)?.value ?: return
        if (operationBusy || current.status.isTerminal) {
            viewModelScope.launch { stopLocalPlayback() }
            return
        }
        operationBusy = true
        _uiState.value = AudioAccessUiState.Session(current, AudioPlaybackState.STOPPING)
        viewModelScope.launch {
            stopLocalPlayback()
            when (val result = repository.stopSession(id)) {
                is OperationResult.Success -> {
                    operationBusy = false
                    _uiState.value = AudioAccessUiState.Session(result.value, AudioPlaybackState.IDLE)
                    if (result.value.status.isTerminal) pollJob?.cancel()
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
                is OperationResult.Success -> applySession(result.value)
                is OperationResult.Failure -> handleFailure(result.error)
            }
        }
    }

    fun onBackground() {
        pollJob?.cancel()
        pollJob = null
        viewModelScope.launch { stopLocalPlayback() }
    }

    fun onForeground() {
        if (sessionId != null) reconcile()
    }

    fun reconcile() {
        val id = sessionId ?: return
        viewModelScope.launch {
            when (val result = repository.getSession(id)) {
                is OperationResult.Success -> {
                    applySession(result.value)
                    if (!result.value.status.isTerminal) startPolling()
                }
                is OperationResult.Failure -> handleFailure(result.error)
            }
        }
    }

    fun handleAdminLogout() {
        pollJob?.cancel()
        pollJob = null
        viewModelScope.launch { stopLocalPlayback() }
        sessionId = null
        selectedDevice = null
        _uiState.value = AudioAccessUiState.Idle
    }

    private fun applySession(session: AudioAccessSession) {
        if (isExpired(session)) {
            pollJob?.cancel()
            viewModelScope.launch { stopLocalPlayback() }
            _uiState.value = AudioAccessUiState.Session(session.copy(), AudioPlaybackState.IDLE)
            onSessionExpired()
            return
        }

        when {
            session.status == com.parento.admin.audio.AudioAccessSessionStatus.ACTIVE &&
                session.transportState["state"]?.uppercase() == "ACTIVE" -> {
                if (transport.state != AudioPlaybackState.PLAYING) {
                    viewModelScope.launch { connectPlayback(session) }
                } else {
                    _uiState.value = AudioAccessUiState.Session(session, AudioPlaybackState.PLAYING)
                }
            }
            session.status.isTerminal -> {
                viewModelScope.launch { stopLocalPlayback() }
                _uiState.value = AudioAccessUiState.Session(session, AudioPlaybackState.IDLE)
                pollJob?.cancel()
            }
            else -> _uiState.value = AudioAccessUiState.Session(session, AudioPlaybackState.IDLE)
        }
    }

    private suspend fun connectPlayback(session: AudioAccessSession) {
        _uiState.value = AudioAccessUiState.Session(session, AudioPlaybackState.CONNECTING)
        val result = transport.connect(
            adminSessionBinding = session.adminBinding(),
            managedDeviceId = session.managedDeviceId,
            audioSessionId = session.sessionId,
            transportState = session.transportState,
        )
        when (result) {
            is OperationResult.Success -> when (val playbackResult = playback.start(transport)) {
                is OperationResult.Success ->
                    _uiState.value = AudioAccessUiState.Session(session, AudioPlaybackState.PLAYING)
                is OperationResult.Failure -> {
                    transport.disconnect()
                    _uiState.value = AudioAccessUiState.Session(session, AudioPlaybackState.ERROR)
                }
            }
            is OperationResult.Failure ->
                _uiState.value = AudioAccessUiState.Session(session, AudioPlaybackState.ERROR)
        }
    }

    private suspend fun stopLocalPlayback() {
        playback.stop(transport)
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val id = sessionId ?: break
                when (val result = repository.getSession(id)) {
                    is OperationResult.Success -> applySession(result.value)
                    is OperationResult.Failure -> {
                        if (result.error is AdminError.SessionExpired || result.error is AdminError.SessionRevoked) {
                            onSessionExpired()
                            break
                        }
                        _uiState.value = AudioAccessUiState.Error(messageFor(result.error))
                        break
                    }
                }
            }
        }
    }

    private fun validateDevice(device: ManagedDeviceStatus): String? {
        if (device.enrollmentState != EnrollmentState.ENROLLED) return "Audio access requires an enrolled device."
        if (device.enrollmentState == EnrollmentState.REVOKED || device.deviceStatus == DeviceStatus.REVOKED) {
            return "This managed device has been revoked."
        }
        if (device.connectionState != ConnectionState.CONNECTED) {
            return "The managed device must have an active communication session."
        }
        if (device.deviceStatus !in setOf(DeviceStatus.AVAILABLE, DeviceStatus.CONNECTED)) {
            return "The managed device is not available for audio access."
        }
        return null
    }

    private fun isExpired(session: AudioAccessSession): Boolean =
        runCatching { Instant.parse(session.expiresAt).toEpochMilli() <= nowEpochMillis() }.getOrDefault(false)

    private fun handleFailure(error: AdminError) {
        if (error is AdminError.SessionExpired || error is AdminError.SessionRevoked) {
            onSessionExpired()
            return
        }
        _uiState.value = AudioAccessUiState.Error(messageFor(error))
    }

    private fun messageFor(error: AdminError): String = when (error) {
        AdminError.Authorization -> "You are not authorized to access this device audio."
        AdminError.Network -> "Network connection unavailable."
        AdminError.Timeout -> "The audio-access request timed out."
        AdminError.InvalidState -> "Audio access is not available in the current session state."
        is AdminError.DeviceNotFound -> "The managed device was not found."
        AdminError.ServerUnavailable -> "The Parento server is temporarily unavailable."
        AdminError.Validation -> "The audio-access request was rejected as invalid."
        else -> "Audio access is currently unavailable."
    }

    private fun AudioAccessSession.adminBinding(): String =
        correlationId

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
