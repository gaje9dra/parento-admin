package com.parento.admin

import com.parento.admin.audio.AudioAccessSession
import com.parento.admin.audio.AudioAccessSessionStatus
import com.parento.admin.audio.AudioTransportState
import com.parento.admin.audio.isPlaybackEligible
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAccessModelsTest {
    private fun session(
        status: AudioAccessSessionStatus,
        transportState: AudioTransportState = AudioTransportState.ACTIVE,
    ) = AudioAccessSession(
        sessionId = "session-1",
        managedDeviceId = "device-1",
        status = status,
        createdAt = "2026-09-26T00:00:00Z",
        authorizedAt = "2026-09-26T00:00:01Z",
        startedAt = "2026-09-26T00:00:02Z",
        stoppedAt = null,
        expiresAt = "2026-09-26T01:00:00Z",
        lastActivityAt = "2026-09-26T00:00:02Z",
        terminationReason = null,
        correlationId = "correlation-1",
        transportState = transportState,
    )

    @Test
    fun onlyActiveSessionWithActiveTransportIsPlaybackEligible() {
        assertTrue(session(AudioAccessSessionStatus.ACTIVE).isPlaybackEligible())
        assertFalse(session(AudioAccessSessionStatus.STARTING).isPlaybackEligible())
        assertFalse(session(AudioAccessSessionStatus.ACTIVE, AudioTransportState.DISCONNECTED).isPlaybackEligible())
    }

    @Test
    fun terminalStatesAreNeverPlaybackEligible() {
        AudioAccessSessionStatus.entries
            .filter { it.isTerminal }
            .forEach { assertFalse(session(it).isPlaybackEligible()) }
    }
}
