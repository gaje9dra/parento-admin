package com.parento.admin

import com.parento.admin.domain.OperationResult
import com.parento.admin.screensharing.ScreenSharingRepository
import com.parento.admin.screensharing.ScreenSharingSession
import com.parento.admin.screensharing.ScreenSharingSessionStatus
import com.parento.admin.screensharing.ScreenTransportState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenSharingRepositoryTest {
    @Test
    fun fakeRepositoryContractCanCreateGetAndStop() = runBlocking {
        val session = ScreenSharingSession(
            sessionId = "session-1",
            managedDeviceId = "device-1",
            status = ScreenSharingSessionStatus.REQUESTED,
            createdAt = "2026-09-26T00:00:00Z",
            authorizedAt = null,
            startedAt = null,
            expiresAt = "2026-09-26T01:00:00Z",
            stoppedAt = null,
            lastActivityAt = "2026-09-26T00:00:00Z",
            terminationReason = null,
            correlationId = "c1",
            transportState = ScreenTransportState.UNAVAILABLE,
        )
        val repository = object : ScreenSharingRepository {
            override suspend fun createSession(managedDeviceId: String, correlationId: String) =
                OperationResult.Success(session)
            override suspend fun getSession(sessionId: String) =
                OperationResult.Success(session)
            override suspend fun stopSession(sessionId: String) =
                OperationResult.Success(session.copy(status = ScreenSharingSessionStatus.STOPPING))
        }

        assertEquals("session-1", (repository.createSession("device-1", "c1") as OperationResult.Success).value.sessionId)
        assertEquals("session-1", (repository.getSession("session-1") as OperationResult.Success).value.sessionId)
        assertEquals(ScreenSharingSessionStatus.STOPPING, (repository.stopSession("session-1") as OperationResult.Success).value.status)
    }
}
