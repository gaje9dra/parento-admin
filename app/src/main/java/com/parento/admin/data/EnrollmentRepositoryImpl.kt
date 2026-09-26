package com.parento.admin.data

import com.parento.admin.communication.EnrollmentApi
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.EnrollmentCreation
import com.parento.admin.domain.EnrollmentRepository
import com.parento.admin.domain.EnrollmentSession
import com.parento.admin.domain.OperationResult
import com.parento.admin.security.SessionStore

class EnrollmentRepositoryImpl(
    private val api: EnrollmentApi,
    private val sessionStore: SessionStore,
) : EnrollmentRepository {
    override suspend fun create(): OperationResult<EnrollmentCreation> = withAccessToken { api.create(it) }
    override suspend fun get(enrollmentId: String): OperationResult<EnrollmentSession> = withAccessToken { api.get(it, enrollmentId) }
    override suspend fun list(): OperationResult<List<EnrollmentSession>> = withAccessToken { api.list(it) }
    override suspend fun cancel(enrollmentId: String): OperationResult<EnrollmentSession> = withAccessToken { api.cancel(it, enrollmentId) }

    private suspend fun <T> withAccessToken(operation: suspend (String) -> OperationResult<T>): OperationResult<T> {
        val session = sessionStore.readSession()
            ?: return OperationResult.Failure(AdminError.SessionExpired)
        if (session.sessionExpiresAtEpochMillis <= System.currentTimeMillis()) {
            sessionStore.clearSession()
            return OperationResult.Failure(AdminError.SessionExpired)
        }
        return operation(session.accessToken)
    }
}
