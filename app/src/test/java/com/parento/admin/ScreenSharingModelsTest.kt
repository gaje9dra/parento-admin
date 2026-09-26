package com.parento.admin

import com.parento.admin.screensharing.ScreenSharingSession
import com.parento.admin.screensharing.ScreenSharingSessionStatus
import com.parento.admin.screensharing.ScreenTransportState
import com.parento.admin.screensharing.isDisplayAuthorized
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSharingModelsTest {
    private fun session(status: ScreenSharingSessionStatus) = ScreenSharingSession(
        sessionId = "session-1",
        managedDeviceId = "device-1",
        status = status,
        createdAt = "2026-09-26T00:00:00Z",
        authorizedAt = null,
        startedAt = null,
        expiresAt = "2026-09-26T01:00:00Z",
        stoppedAt = null,
        lastActivityAt = "2026-09-26T00:00:00Z",
        terminationReason = null,
        correlationId = "correlation-1",
        transportState = ScreenTransportState.UNAVAILABLE,
    )

    @Test
    fun terminalStatesAreNotDisplayable() {
        assertFalse(session(ScreenSharingSessionStatus.STOPPED).isDisplayAuthorized())
        assertFalse(session(ScreenSharingSessionStatus.EXPIRED).isDisplayAuthorized())
        assertFalse(session(ScreenSharingSessionStatus.FAILED).isDisplayAuthorized())
        assertFalse(session(ScreenSharingSessionStatus.REJECTED).isDisplayAuthorized())
    }

    @Test
    fun activeSessionRequiresNonErrorTransport() {
        assertTrue(session(ScreenSharingSessionStatus.ACTIVE).isDisplayAuthorized())
    }
}
