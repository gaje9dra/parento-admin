package com.parento.admin

import com.parento.admin.audio.AudioPlaybackState
import com.parento.admin.audio.UnavailableAudioTransport
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
            managedDeviceId = "device-1",
            audioSessionId = "session-1",
            transportState = mapOf("state" to "ACTIVE"),
        )
        assertTrue(result is OperationResult.Failure)
        assertEquals(AudioPlaybackState.ERROR, transport.state)
    }

    @Test
    fun disconnectReturnsToIdle() = runTest {
        val transport = UnavailableAudioTransport()
        transport.disconnect()
        assertEquals(AudioPlaybackState.IDLE, transport.state)
    }
}
