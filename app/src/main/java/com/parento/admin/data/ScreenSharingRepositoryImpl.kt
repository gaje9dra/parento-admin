package com.parento.admin.data

import com.parento.admin.communication.AdminBackendApiClient
import com.parento.admin.domain.OperationResult
import com.parento.admin.screensharing.ScreenSharingRepository
import com.parento.admin.screensharing.ScreenSharingSession

class ScreenSharingRepositoryImpl(
    private val api: AdminBackendApiClient,
) : ScreenSharingRepository {
    override suspend fun createSession(
        managedDeviceId: String,
        correlationId: String,
    ): OperationResult<ScreenSharingSession> =
        api.createScreenSharingSession(managedDeviceId, correlationId)

    override suspend fun getSession(sessionId: String): OperationResult<ScreenSharingSession> =
        api.getScreenSharingSession(sessionId)

    override suspend fun stopSession(sessionId: String): OperationResult<ScreenSharingSession> =
        api.stopScreenSharingSession(sessionId)
}
