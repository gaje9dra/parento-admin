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
                if (result.value.admin.status != ACTIVE_STATUS) {
                    secureStore.clearSession()
                    OperationResult.Failure(AdminError.AccountDisabled)
                } else {
                    secureStore.saveSession(result.value)
                    OperationResult.Success(result.value.admin)
                }
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

        // Clear local credentials before waiting on the network so logout can
        // never leave a stale authenticated session behind while the request is pending.
        secureStore.clearSession()
        val backendResult = api.logout(session)

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

        // Do not send a locally expired access credential. Refresh first while the
        // parent server-side session is still within its lifetime.
        if (session.accessTokenExpiresAtEpochMillis <= System.currentTimeMillis()) {
            return refreshAndGetCurrent(session)
        }

        return when (val current = api.current(session)) {
            is OperationResult.Success -> {
                if (current.value.status != ACTIVE_STATUS) {
                    secureStore.clearSession()
                    OperationResult.Failure(AdminError.AccountDisabled)
                } else {
                    secureStore.saveSession(session.copy(admin = current.value))
                    OperationResult.Success(current.value)
                }
            }
            is OperationResult.Failure -> {
                when (current.error) {
                    AdminError.SessionExpired -> refreshAndGetCurrent(session)
                    AdminError.SessionRevoked,
                    AdminError.AccountDisabled,
                    AdminError.Authorization -> {
                        secureStore.clearSession()
                        current
                    }
                    else -> current
                }
            }
        }
    }

    override suspend fun restoreSession(): OperationResult<AuthenticatedAdmin?> {
        val session = secureStore.readSession()
            ?: return OperationResult.Success(null)

        if (session.sessionExpiresAtEpochMillis <= System.currentTimeMillis()) {
            secureStore.clearSession()
            return OperationResult.Success(null)
        }

        if (session.accessTokenExpiresAtEpochMillis <= System.currentTimeMillis()) {
            return when (val refreshed = refreshAndGetCurrent(session)) {
                is OperationResult.Success -> OperationResult.Success(refreshed.value)
                is OperationResult.Failure -> {
                    when (refreshed.error) {
                        AdminError.SessionRevoked,
                        AdminError.AccountDisabled,
                        AdminError.Authorization,
                        AdminError.SessionExpired -> OperationResult.Failure(refreshed.error)
                        else -> refreshed
                    }
                }
            }
        }

        return when (val current = api.current(session)) {
            is OperationResult.Success -> {
                if (current.value.status != ACTIVE_STATUS) {
                    secureStore.clearSession()
                    OperationResult.Failure(AdminError.AccountDisabled)
                } else {
                    secureStore.saveSession(session.copy(admin = current.value))
                    OperationResult.Success(current.value)
                }
            }
            is OperationResult.Failure -> {
                when (current.error) {
                    AdminError.SessionExpired -> refreshAndGetCurrentAsNullable(session)
                    AdminError.SessionRevoked,
                    AdminError.AccountDisabled,
                    AdminError.Authorization -> {
                        secureStore.clearSession()
                        OperationResult.Failure(current.error)
                    }
                    else -> current
                }
            }
        }
    }

    private suspend fun refreshAndGetCurrent(
        session: AuthenticationSession,
    ): OperationResult<AuthenticatedAdmin> {
        if (session.sessionExpiresAtEpochMillis <= System.currentTimeMillis()) {
            secureStore.clearSession()
            return OperationResult.Failure(AdminError.SessionExpired)
        }

        return when (val refreshed = api.refresh(session)) {
            is OperationResult.Success -> {
                if (refreshed.value.sessionExpiresAtEpochMillis <= System.currentTimeMillis()) {
                    secureStore.clearSession()
                    return OperationResult.Failure(AdminError.SessionExpired)
                }

                secureStore.saveSession(refreshed.value)

                when (val current = api.current(refreshed.value)) {
                    is OperationResult.Success -> {
                        if (current.value.status != ACTIVE_STATUS) {
                            secureStore.clearSession()
                            OperationResult.Failure(AdminError.AccountDisabled)
                        } else {
                            secureStore.saveSession(
                                refreshed.value.copy(admin = current.value),
                            )
                            OperationResult.Success(current.value)
                        }
                    }
                    is OperationResult.Failure -> {
                        secureStore.clearSession()
                        current
                    }
                }
            }
            is OperationResult.Failure -> {
                when (refreshed.error) {
                    AdminError.SessionExpired,
                    AdminError.SessionRevoked,
                    AdminError.AccountDisabled,
                    AdminError.Authorization,
                    AdminError.InvalidCredentials -> {
                        secureStore.clearSession()
                    }
                    else -> Unit
                }
                refreshed
            }
        }
    }

    private suspend fun refreshAndGetCurrentAsNullable(
        session: AuthenticationSession,
    ): OperationResult<AuthenticatedAdmin?> =
        when (val result = refreshAndGetCurrent(session)) {
            is OperationResult.Success -> OperationResult.Success(result.value)
            is OperationResult.Failure -> OperationResult.Failure(result.error)
        }

    private companion object {
        const val ACTIVE_STATUS = "ACTIVE"
    }
}
