package com.parento.admin.data

import com.parento.admin.audio.AudioAccessRepository
import com.parento.admin.audio.AudioAccessSession
import com.parento.admin.communication.AdminBackendApiClient
import com.parento.admin.domain.OperationResult

class AudioAccessRepositoryImpl(
    private val api: AdminBackendApiClient,
) : AudioAccessRepository {
    override suspend fun createSession(
        managedDeviceId: String,
        correlationId: String,
    ): OperationResult<AudioAccessSession> =
        api.createAudioAccessSession(managedDeviceId, correlationId)

    override suspend fun getSession(sessionId: String): OperationResult<AudioAccessSession> =
        api.getAudioAccessSession(sessionId)

    override suspend fun stopSession(sessionId: String): OperationResult<AudioAccessSession> =
        api.stopAudioAccessSession(sessionId)
}
