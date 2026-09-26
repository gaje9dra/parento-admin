package com.parento.admin.screensharing

import com.parento.admin.domain.OperationResult

interface ScreenSharingRepository {
    suspend fun createSession(
        managedDeviceId: String,
        correlationId: String,
    ): OperationResult<ScreenSharingSession>

    suspend fun getSession(sessionId: String): OperationResult<ScreenSharingSession>

    suspend fun stopSession(sessionId: String): OperationResult<ScreenSharingSession>
}
