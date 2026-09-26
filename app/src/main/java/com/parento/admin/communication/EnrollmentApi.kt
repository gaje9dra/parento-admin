package com.parento.admin.communication

import com.parento.admin.domain.EnrollmentCreation
import com.parento.admin.domain.EnrollmentSession
import com.parento.admin.domain.OperationResult

interface EnrollmentApi {
    suspend fun create(accessToken: String): OperationResult<EnrollmentCreation>
    suspend fun list(accessToken: String): OperationResult<List<EnrollmentSession>>
    suspend fun get(accessToken: String, enrollmentId: String): OperationResult<EnrollmentSession>
    suspend fun cancel(accessToken: String, enrollmentId: String): OperationResult<EnrollmentSession>
}
