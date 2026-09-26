package com.parento.admin

import com.parento.admin.audio.AudioPlaybackState
import com.parento.admin.audio.AudioTransportContext
import com.parento.admin.audio.AudioTransportState
import com.parento.admin.audio.UnavailableAudioTransport
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTransportTest {
    @Test
    fun unavailableTransportFailsClosed() = runTest {
        val transport = UnavailableAudioTransport()
        val result = transport.connect(
            context = AudioTransportContext(
                managedDeviceId = "device-1",
                audioSessionId = "session-1",
            ),
            transportState = AudioTransportState.ACTIVE,
        )
        assertTrue(result is OperationResult.Failure)
        assertEquals(AudioPlaybackState.ERROR, transport.state)
    }

    @Test
    fun transportRejectsMissingSessionBinding() = runTest {
        val transport = UnavailableAudioTransport()
        val result = transport.connect(
            context = AudioTransportContext(
                managedDeviceId = "",
                audioSessionId = "session-1",
            ),
            transportState = AudioTransportState.ACTIVE,
        )
        assertTrue(result is OperationResult.Failure)
        assertEquals(AdminError.Validation, (result as OperationResult.Failure).error)
    }

    @Test
    fun transportRejectsNonActiveSessionState() = runTest {
        val transport = UnavailableAudioTransport()
        val result = transport.connect(
            context = AudioTransportContext(
                managedDeviceId = "device-1",
                audioSessionId = "session-1",
            ),
            transportState = AudioTransportState.DISCONNECTED,
        )
        assertTrue(result is OperationResult.Failure)
        assertEquals(AdminError.InvalidState, (result as OperationResult.Failure).error)
    }

    @Test
    fun disconnectReturnsToIdle() = runTest {
        val transport = UnavailableAudioTransport()
        transport.disconnect()
        assertEquals(AudioPlaybackState.IDLE, transport.state)
    }
}
