package com.parento.admin.domain

interface EnrollmentRepository {
    suspend fun create(): OperationResult<EnrollmentCreation>
    suspend fun get(enrollmentId: String): OperationResult<EnrollmentSession>
    suspend fun list(): OperationResult<List<EnrollmentSession>>
    suspend fun cancel(enrollmentId: String): OperationResult<EnrollmentSession>
}
