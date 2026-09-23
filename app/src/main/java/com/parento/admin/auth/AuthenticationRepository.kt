package com.parento.admin.auth

import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult

interface AuthenticationRepository {
    suspend fun login(credentials: AdminLoginCredentials): OperationResult<AuthenticatedAdmin>
    suspend fun logout(): OperationResult<Unit>
    suspend fun getCurrentAuthenticatedAdmin(): OperationResult<AuthenticatedAdmin>
    suspend fun restoreSession(): OperationResult<AuthenticatedAdmin?>
}
