package com.parento.admin.audio

import com.parento.admin.domain.OperationResult

interface AudioAccessRepository {
    suspend fun createSession(
        managedDeviceId: String,
        correlationId: String,
    ): OperationResult<AudioAccessSession>

    suspend fun getSession(sessionId: String): OperationResult<AudioAccessSession>

    suspend fun stopSession(sessionId: String): OperationResult<AudioAccessSession>
}
