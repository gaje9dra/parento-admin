package com.parento.admin.communication

import com.parento.admin.auth.AdminLoginCredentials
import com.parento.admin.auth.AuthenticatedAdmin
import com.parento.admin.auth.AuthenticationSession
import com.parento.admin.domain.OperationResult

interface AuthenticationApi {
    suspend fun login(credentials: AdminLoginCredentials): OperationResult<AuthenticationSession>
    suspend fun refresh(session: AuthenticationSession): OperationResult<AuthenticationSession>
    suspend fun current(session: AuthenticationSession): OperationResult<AuthenticatedAdmin>
    suspend fun logout(session: AuthenticationSession): OperationResult<Unit>
}
