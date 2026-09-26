package com.parento.admin

import com.parento.admin.audio.AudioAccessRepository
import com.parento.admin.audio.AudioAccessSession
import com.parento.admin.audio.AudioAccessSessionStatus
import com.parento.admin.audio.AudioPlaybackController
import com.parento.admin.audio.AudioPlaybackState
import com.parento.admin.audio.AudioTransport
import com.parento.admin.audio.AudioTransportState
import com.parento.admin.device.ManagedDeviceStatus
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.ConnectionState
import com.parento.admin.domain.DeviceStatus
import com.parento.admin.domain.EnrollmentState
import com.parento.admin.domain.OperationResult
import com.parento.admin.ui.AudioAccessUiState
import com.parento.admin.ui.AudioAccessViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioAccessViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val device = ManagedDeviceStatus(
        deviceId = "device-1",
        displayName = "Test device",
        enrollmentState = EnrollmentState.ENROLLED,
        deviceStatus = DeviceStatus.AVAILABLE,
        connectionState = ConnectionState.CONNECTED,
        lastSeenAt = null,
        lastSeenAgeMs = null,
        expiresAt = null,
        monitoringFreshness = com.parento.admin.device.MonitoringFreshness.CURRENT,
        monitoring = null,
    )

    @Test
    fun duplicateStartDoesNotCreateAnotherSession() = runTest {
        val repository = FakeAudioRepository(createResult = OperationResult.Success(session(stopped = true)))
        val viewModel = AudioAccessViewModel(
            repository = repository,
            playback = FakePlayback(),
            onSessionExpired = {},
        )
        viewModel.bindDevice(device)

        viewModel.start()
        advanceUntilIdle()
        viewModel.start()
        advanceUntilIdle()

        assertEquals(1, repository.createCalls)
    }

    @Test
    fun mismatchedBackendDeviceIsRejected() = runTest {
        val repository = FakeAudioRepository(
            createResult = OperationResult.Success(session(deviceId = "other-device", stopped = true)),
        )
        val viewModel = AudioAccessViewModel(
            repository = repository,
            playback = FakePlayback(),
            onSessionExpired = {},
        )
        viewModel.bindDevice(device)

        viewModel.start()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AudioAccessUiState.Error)
        assertFalse((state as AudioAccessUiState.Error).canRetry)
    }

    @Test
    fun locallyExpiredSessionStopsPlaybackAndCannotBePlayed() = runTest {
        val playback = FakePlayback()
        val repository = FakeAudioRepository(
            createResult = OperationResult.Success(
                session(
                    status = AudioAccessSessionStatus.ACTIVE,
                    expiresAt = "2020-01-01T00:00:00Z",
                ),
            ),
        )
        val viewModel = AudioAccessViewModel(
            repository = repository,
            playback = playback,
            onSessionExpired = {},
            nowEpochMillis = { 1_700_000_000_000L },
        )
        viewModel.bindDevice(device)

        viewModel.start()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AudioAccessUiState.Session)
        assertEquals(
            AudioAccessSessionStatus.EXPIRED,
            (state as AudioAccessUiState.Session).value.status,
        )
        assertTrue(playback.stopCalls > 0)
    }

    @Test
    fun authorizationFailureStopsLocalPlayback() = runTest {
        val playback = FakePlayback()
        val repository = FakeAudioRepository(
            createResult = OperationResult.Failure(AdminError.Authorization),
        )
        val viewModel = AudioAccessViewModel(
            repository = repository,
            playback = playback,
            onSessionExpired = {},
        )
        viewModel.bindDevice(device)

        viewModel.start()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AudioAccessUiState.Error)
        assertEquals(1, playback.stopCalls)
    }

    private fun session(
        status: AudioAccessSessionStatus = AudioAccessSessionStatus.STOPPED,
        deviceId: String = "device-1",
        expiresAt: String = "2099-01-01T00:00:00Z",
        stopped: Boolean = false,
    ) = AudioAccessSession(
        sessionId = "session-1",
        managedDeviceId = deviceId,
        status = if (stopped) AudioAccessSessionStatus.STOPPED else status,
        createdAt = "2026-09-26T00:00:00Z",
        authorizedAt = null,
        startedAt = null,
        stoppedAt = null,
        expiresAt = expiresAt,
        lastActivityAt = "2026-09-26T00:00:00Z",
        terminationReason = null,
        correlationId = "correlation-1",
        transportState = if (status == AudioAccessSessionStatus.ACTIVE) {
            AudioTransportState.ACTIVE
        } else {
            AudioTransportState.UNAVAILABLE
        },
    )

    private class FakeAudioRepository(
        private val createResult: OperationResult<AudioAccessSession>,
    ) : AudioAccessRepository {
        var createCalls = 0
            private set

        override suspend fun createSession(
            managedDeviceId: String,
            correlationId: String,
        ): OperationResult<AudioAccessSession> {
            createCalls++
            return createResult
        }

        override suspend fun getSession(sessionId: String): OperationResult<AudioAccessSession> =
            createResult

        override suspend fun stopSession(sessionId: String): OperationResult<AudioAccessSession> =
            createResult
    }

    private class FakePlayback : AudioPlaybackController {
        override val state: AudioPlaybackState = AudioPlaybackState.IDLE
        var stopCalls = 0
            private set

        override suspend fun start(transport: AudioTransport): OperationResult<Unit> =
            OperationResult.Success(Unit)

        override suspend fun stop(transport: AudioTransport) {
            stopCalls++
        }
    }
}
