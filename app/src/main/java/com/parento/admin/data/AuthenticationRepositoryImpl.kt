package com.parento.admin.data

import com.parento.admin.auth.AdminLoginCredentials
import com.parento.admin.auth.AuthenticatedAdmin
import com.parento.admin.auth.AuthenticationRepository
import com.parento.admin.auth.AuthenticationSession
import com.parento.admin.communication.AuthenticationApi
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import com.parento.admin.security.SessionStore

class AuthenticationRepositoryImpl(
    private val api: AuthenticationApi,
    private val secureStore: SessionStore,
) : AuthenticationRepository {

    override suspend fun login(
        credentials: AdminLoginCredentials,
    ): OperationResult<AuthenticatedAdmin> {
        if (!credentials.validate()) return OperationResult.Failure(AdminError.Validation)

        return when (val result = api.login(credentials)) {
            is OperationResult.Success -> {
                secureStore.saveSession(result.value)
                OperationResult.Success(result.value.admin)
            }
            is OperationResult.Failure -> {
                secureStore.clearSession()
                result
            }
        }
    }

    override suspend fun logout(): OperationResult<Unit> {
        val session = secureStore.readSession()
            ?: return OperationResult.Success(Unit)

        val backendResult = api.logout(session)
        secureStore.clearSession()
        return when (backendResult) {
            is OperationResult.Success -> OperationResult.Success(Unit)
            is OperationResult.Failure -> OperationResult.Success(Unit)
        }
    }

    override suspend fun getCurrentAuthenticatedAdmin(): OperationResult<AuthenticatedAdmin> {
        val session = secureStore.readSession()
            ?: return OperationResult.Failure(AdminError.SessionExpired)

        if (session.sessionExpiresAtEpochMillis <= System.currentTimeMillis()) {
            secureStore.clearSession()
            return OperationResult.Failure(AdminError.SessionExpired)
        }

        val current = api.current(session)
        return when (current) {
            is OperationResult.Success -> {
                val updated = session.copy(admin = current.value)
                secureStore.saveSession(updated)
                OperationResult.Success(current.value)
            }
            is OperationResult.Failure -> {
                if (current.error == AdminError.SessionExpired) {
                    refreshAndGetCurrent(session)
                } else {
                    if (current.error in setOf(
                            AdminError.SessionRevoked,
                            AdminError.AccountDisabled,
                            AdminError.Authorization,
                        )
                    ) {
                        secureStore.clearSession()
                    }
                    current
                }
            }
        }
    }

    override suspend fun restoreSession(): OperationResult<AuthenticatedAdmin?> {
        val session = secureStore.readSession() ?: return OperationResult.Success(null)

        if (session.sessionExpiresAtEpochMillis <= System.currentTimeMillis()) {
            secureStore.clearSession()
            return OperationResult.Success(null)
        }

        return when (val current = api.current(session)) {
            is OperationResult.Success -> {
                secureStore.saveSession(session.copy(admin = current.value))
                OperationResult.Success(current.value)
            }
            is OperationResult.Failure -> {
                if (current.error == AdminError.SessionExpired) {
                    when (val refreshed = api.refresh(session)) {
                        is OperationResult.Success -> {
                            secureStore.saveSession(refreshed.value)
                            when (val verified = api.current(refreshed.value)) {
                                is OperationResult.Success -> {
                                    secureStore.saveSession(
                                        refreshed.value.copy(admin = verified.value),
                                    )
                                    OperationResult.Success(verified.value)
                                }
                                is OperationResult.Failure -> {
                                    secureStore.clearSession()
                                    OperationResult.Success(null)
                                }
                            }
                        }
                        is OperationResult.Failure -> {
                            secureStore.clearSession()
                            OperationResult.Success(null)
                        }
                    }
                } else {
                    if (
                        current.error in setOf(
                            AdminError.SessionExpired,
                            AdminError.SessionRevoked,
                            AdminError.AccountDisabled,
                            AdminError.Authorization,
                        )
                    ) {
                        secureStore.clearSession()
                    }
                    current
                }
            }
        }
    }

    private suspend fun refreshAndGetCurrent(
        session: AuthenticationSession,
    ): OperationResult<AuthenticatedAdmin> {
        return when (val refreshed = api.refresh(session)) {
            is OperationResult.Success -> {
                secureStore.saveSession(refreshed.value)
                when (val current = api.current(refreshed.value)) {
                    is OperationResult.Success -> {
                        secureStore.saveSession(refreshed.value.copy(admin = current.value))
                        OperationResult.Success(current.value)
                    }
                    is OperationResult.Failure -> {
                        secureStore.clearSession()
                        current
                    }
                }
            }
            is OperationResult.Failure -> {
                secureStore.clearSession()
                OperationResult.Failure(AdminError.SessionExpired)
            }
        }
    }
}
