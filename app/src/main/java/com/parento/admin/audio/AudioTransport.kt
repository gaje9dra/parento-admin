package com.parento.admin.audio

import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult

/**
 * Session-bound boundary for the approved live-audio transport.
 *
 * The current backend contract exposes control/signaling state but no
 * production media-byte protocol or codec. The default implementation
 * therefore fails closed.
 */
data class AudioTransportContext(
    val managedDeviceId: String,
    val audioSessionId: String,
)

interface AudioTransport {
    val state: AudioPlaybackState

    suspend fun connect(
        context: AudioTransportContext,
        transportState: AudioTransportState,
    ): OperationResult<Unit>

    suspend fun disconnect()

    suspend fun startPlayback(): OperationResult<Unit>

    suspend fun stopPlayback()
}

class UnavailableAudioTransport : AudioTransport {
    override var state: AudioPlaybackState = AudioPlaybackState.IDLE
        private set

    override suspend fun connect(
        context: AudioTransportContext,
        transportState: AudioTransportState,
    ): OperationResult<Unit> {
        if (context.managedDeviceId.isBlank() || context.audioSessionId.isBlank()) {
            state = AudioPlaybackState.ERROR
            return OperationResult.Failure(AdminError.Validation)
        }
        if (transportState != AudioTransportState.ACTIVE) {
            state = AudioPlaybackState.ERROR
            return OperationResult.Failure(AdminError.InvalidState)
        }
        state = AudioPlaybackState.ERROR
        return OperationResult.Failure(AdminError.Backend)
    }

    override suspend fun disconnect() {
        state = AudioPlaybackState.IDLE
    }

    override suspend fun startPlayback(): OperationResult<Unit> =
        OperationResult.Failure(AdminError.Backend)

    override suspend fun stopPlayback() {
        state = AudioPlaybackState.IDLE
    }
}

interface AudioPlaybackController {
    val state: AudioPlaybackState
    suspend fun start(transport: AudioTransport): OperationResult<Unit>
    suspend fun stop(transport: AudioTransport)
}

class SessionBoundAudioPlaybackController : AudioPlaybackController {
    override val state: AudioPlaybackState
        get() = AudioPlaybackState.IDLE

    override suspend fun start(transport: AudioTransport): OperationResult<Unit> =
        transport.startPlayback()

    override suspend fun stop(transport: AudioTransport) {
        transport.stopPlayback()
        transport.disconnect()
    }
}
