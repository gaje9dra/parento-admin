package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.admin.audio.AudioAccessRepository
import com.parento.admin.audio.AudioAccessSession
import com.parento.admin.audio.AudioAccessSessionStatus
import com.parento.admin.audio.AudioPlaybackController
import com.parento.admin.audio.AudioPlaybackState
import com.parento.admin.audio.AudioTransport
import com.parento.admin.audio.AudioTransportContext
import com.parento.admin.audio.AudioTransportState
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
    private var playbackAttemptedSessionId: String? = null

    fun bindDevice(device: ManagedDeviceStatus) {
        if (selectedDevice?.deviceId == device.deviceId) {
            selectedDevice = device
            return
        }
        pollJob?.cancel()
        pollJob = null
        viewModelScope.launch { stopLocalPlayback() }
        selectedDevice = device
        sessionId = null
        playbackAttemptedSessionId = null
        operationBusy = false
        _uiState.value = AudioAccessUiState.Idle
    }

    fun clearDevice() {
        pollJob?.cancel()
        pollJob = null
        viewModelScope.launch { stopLocalPlayback() }
        selectedDevice = null
        sessionId = null
        playbackAttemptedSessionId = null
        operationBusy = false
        _uiState.value = AudioAccessUiState.Idle
    }

    fun start() {
        val device = selectedDevice ?: return
        val currentSession = (_uiState.value as? AudioAccessUiState.Session)?.value
        if (currentSession != null) return
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
                    val session = result.value
                    if (session.managedDeviceId != device.deviceId || session.sessionId.isBlank()) {
                        handleFailure(AdminError.Authorization)
                        return@launch
                    }
                    sessionId = session.sessionId
                    playbackAttemptedSessionId = null
                    _uiState.value = AudioAccessUiState.Session(session, AudioPlaybackState.IDLE)
                    if (session.status.isTerminal) {
                        applySession(session)
                    } else {
                        startPolling()
                        applySession(session)
                    }
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
        if (current.status.isTerminal) {
            viewModelScope.launch { stopLocalPlayback() }
            return
        }
        if (operationBusy) return

        operationBusy = true
        _uiState.value = AudioAccessUiState.Session(current, AudioPlaybackState.STOPPING)
        viewModelScope.launch {
            stopLocalPlayback()
            when (val result = repository.stopSession(id)) {
                is OperationResult.Success -> {
                    operationBusy = false
                    applySession(result.value)
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

    fun retry() {
        val state = _uiState.value
        if (state !is AudioAccessUiState.Error || !state.canRetry) return
        if (sessionId != null) {
            reconcile()
        } else {
            start()
        }
    }

    fun retryPlayback() {
        val session = (_uiState.value as? AudioAccessUiState.Session)?.value ?: return
        if (session.status != AudioAccessSessionStatus.ACTIVE) return
        playbackAttemptedSessionId = null
        applySession(session)
    }

    fun startNewSession() {
        val current = (_uiState.value as? AudioAccessUiState.Session)?.value ?: return
        if (!current.status.isTerminal) return
        sessionId = null
        playbackAttemptedSessionId = null
        operationBusy = false
        _uiState.value = AudioAccessUiState.Idle
        start()
    }

    fun onBackground() {
        pollJob?.cancel()
        pollJob = null
        playbackAttemptedSessionId = null
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
        playbackAttemptedSessionId = null
        operationBusy = false
        _uiState.value = AudioAccessUiState.Idle
    }

    private fun applySession(session: AudioAccessSession) {
        val expectedDeviceId = selectedDevice?.deviceId
        val expectedSessionId = sessionId
        if (expectedDeviceId == null || expectedSessionId == null ||
            session.managedDeviceId != expectedDeviceId || session.sessionId != expectedSessionId
        ) {
            pollJob?.cancel()
            viewModelScope.launch { stopLocalPlayback() }
            _uiState.value = AudioAccessUiState.Error(
                "The audio session no longer matches the selected device.",
                canRetry = false,
            )
            return
        }

        val authoritativeSession = if (isExpired(session) && !session.status.isTerminal) {
            session.copy(
                status = AudioAccessSessionStatus.EXPIRED,
                terminationReason = session.terminationReason ?: "EXPIRED",
            )
        } else {
            session
        }

        when {
            authoritativeSession.status.isTerminal -> {
                pollJob?.cancel()
                viewModelScope.launch { stopLocalPlayback() }
                _uiState.value = AudioAccessUiState.Session(
                    authoritativeSession,
                    AudioPlaybackState.IDLE,
                )
            }

            authoritativeSession.status == AudioAccessSessionStatus.ACTIVE &&
                authoritativeSession.transportState == AudioTransportState.ACTIVE -> {
                if (playbackAttemptedSessionId != authoritativeSession.sessionId) {
                    playbackAttemptedSessionId = authoritativeSession.sessionId
                    viewModelScope.launch { connectPlayback(authoritativeSession) }
                } else if (transport.state == AudioPlaybackState.PLAYING) {
                    _uiState.value = AudioAccessUiState.Session(
                        authoritativeSession,
                        AudioPlaybackState.PLAYING,
                    )
                } else {
                    _uiState.value = AudioAccessUiState.Session(
                        authoritativeSession,
                        AudioPlaybackState.ERROR,
                    )
                }
            }

            else -> {
                viewModelScope.launch { stopLocalPlayback() }
                _uiState.value = AudioAccessUiState.Session(
                    authoritativeSession,
                    AudioPlaybackState.IDLE,
                )
            }
        }
    }

    private suspend fun connectPlayback(session: AudioAccessSession) {
        _uiState.value = AudioAccessUiState.Session(session, AudioPlaybackState.CONNECTING)
        val result = transport.connect(
            context = AudioTransportContext(
                managedDeviceId = session.managedDeviceId,
                audioSessionId = session.sessionId,
            ),
            transportState = session.transportState,
        )
        when (result) {
            is OperationResult.Success -> when (val playbackResult = playback.start(transport)) {
                is OperationResult.Success ->
                    _uiState.value = AudioAccessUiState.Session(session, AudioPlaybackState.PLAYING)

                is OperationResult.Failure -> {
                    playback.stop(transport)
                    _uiState.value = AudioAccessUiState.Session(session, AudioPlaybackState.ERROR)
                }
            }

            is OperationResult.Failure -> {
                transport.disconnect()
                _uiState.value = AudioAccessUiState.Session(session, AudioPlaybackState.ERROR)
            }
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
                        if (result.error is AdminError.SessionExpired) {
                            stopForAuthorizationFailure()
                            onSessionExpired()
                            break
                        }
                        if (result.error is AdminError.SessionRevoked) {
                            stopForAuthorizationFailure()
                            onSessionExpired()
                            break
                        }
                        if (result.error is AdminError.ResourceGone) {
                            markExpiredFromFailure()
                            break
                        }
                        viewModelScope.launch { stopLocalPlayback() }
                        _uiState.value = AudioAccessUiState.Error(
                            messageFor(result.error),
                            canRetry = result.error is AdminError.Network ||
                                result.error is AdminError.Timeout ||
                                result.error is AdminError.RateLimited,
                        )
                        break
                    }
                }
            }
        }
    }

    private fun validateDevice(device: ManagedDeviceStatus): String? {
        if (device.enrollmentState == EnrollmentState.REVOKED || device.deviceStatus == DeviceStatus.REVOKED) {
            return "This managed device has been revoked."
        }
        if (device.enrollmentState != EnrollmentState.ENROLLED) {
            return "Audio access requires an enrolled device."
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
        runCatching {
            Instant.parse(session.expiresAt).toEpochMilli() <= nowEpochMillis()
        }.getOrDefault(false)

    private fun stopForAuthorizationFailure() {
        pollJob?.cancel()
        pollJob = null
        viewModelScope.launch { stopLocalPlayback() }
        _uiState.value = AudioAccessUiState.Error(
            "Audio access authorization is no longer valid.",
            canRetry = false,
        )
    }

    private fun markExpiredFromFailure() {
        val current = (_uiState.value as? AudioAccessUiState.Session)?.value ?: return
        pollJob?.cancel()
        viewModelScope.launch { stopLocalPlayback() }
        _uiState.value = AudioAccessUiState.Session(
            current.copy(
                status = AudioAccessSessionStatus.EXPIRED,
                terminationReason = current.terminationReason ?: "EXPIRED",
            ),
            AudioPlaybackState.IDLE,
        )
    }

    private fun handleFailure(error: AdminError) {
        when (error) {
            AdminError.SessionExpired,
            AdminError.SessionRevoked -> {
                stopForAuthorizationFailure()
                onSessionExpired()
            }

            AdminError.Authorization -> {
                stopForAuthorizationFailure()
            }

            AdminError.ResourceGone -> {
                markExpiredFromFailure()
            }

            else -> {
                viewModelScope.launch { stopLocalPlayback() }
                _uiState.value = AudioAccessUiState.Error(
                    messageFor(error),
                    canRetry = error is AdminError.Network ||
                        error is AdminError.Timeout ||
                        error is AdminError.RateLimited,
                )
            }
        }
    }

    private fun messageFor(error: AdminError): String = when (error) {
        AdminError.Authorization -> "You are not authorized to access this device audio."
        AdminError.Network -> "Network connection unavailable."
        AdminError.Timeout -> "The audio-access request timed out."
        AdminError.InvalidState -> "Audio access is not available in the current session state."
        AdminError.RateLimited -> "Too many audio-access requests. Please wait and try again."
        AdminError.ResourceGone -> "This audio session is no longer available."
        is AdminError.DeviceNotFound -> "The managed device was not found."
        AdminError.ServerUnavailable -> "The Parento server is temporarily unavailable."
        AdminError.Validation -> "The audio-access request was rejected as invalid."
        AdminError.SessionExpired -> "Your administrator session has expired."
        AdminError.SessionRevoked -> "Your administrator session is no longer valid."
        else -> "Audio access is currently unavailable."
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
