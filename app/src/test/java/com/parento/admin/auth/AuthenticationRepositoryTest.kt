package com.parento.admin.auth

import com.parento.admin.communication.AuthenticationApi
import com.parento.admin.data.AuthenticationRepositoryImpl
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import com.parento.admin.security.SessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthenticationRepositoryTest {
    private val admin = AuthenticatedAdmin(
        id = "admin-1",
        email = "admin@example.com",
        status = "ACTIVE",
        lastAuthenticatedAt = null,
    )

    @Test
    fun validLoginStoresSecureSessionAndAuthenticates() = runBlocking {
        val session = session()
        val api = FakeApi(loginResult = OperationResult.Success(session))
        val store = FakeStore()
        val repository = AuthenticationRepositoryImpl(api, store)

        val result = repository.login(
            AdminLoginCredentials("admin@example.com", "valid password"),
        )

        assertEquals(OperationResult.Success(admin), result)
        assertEquals(session, store.session)
    }

    @Test
    fun invalidCredentialsRemainUnauthenticated() = runBlocking {
        val api = FakeApi(
            loginResult = OperationResult.Failure(AdminError.InvalidCredentials),
        )
        val store = FakeStore()
        val repository = AuthenticationRepositoryImpl(api, store)

        val result = repository.login(
            AdminLoginCredentials("admin@example.com", "wrong"),
        )

        assertEquals(OperationResult.Failure(AdminError.InvalidCredentials), result)
        assertNull(store.session)
    }

    @Test
    fun expiredStoredSessionIsCleared() = runBlocking {
        val store = FakeStore().apply {
            session = session(
                sessionExpiresAtEpochMillis = System.currentTimeMillis() - 1,
            )
        }
        val repository = AuthenticationRepositoryImpl(FakeApi(), store)

        val result = repository.restoreSession()

        assertEquals(OperationResult.Success(null), result)
        assertNull(store.session)
    }

    @Test
    fun sessionExpiryRefreshesAndRestoresIdentity() = runBlocking {
        val expiredAccess = session(
            accessTokenExpiresAtEpochMillis = System.currentTimeMillis() - 1,
            sessionExpiresAtEpochMillis = System.currentTimeMillis() + 60_000,
        )
        val refreshed = session(
            accessToken = "new-access",
            refreshToken = "new-refresh",
        )
        val api = FakeApi(
            currentResult = OperationResult.Failure(AdminError.SessionExpired),
            refreshResult = OperationResult.Success(refreshed),
            currentAfterRefresh = OperationResult.Success(admin),
        )
        val store = FakeStore().apply { session = expiredAccess }
        val repository = AuthenticationRepositoryImpl(api, store)

        val result = repository.getCurrentAuthenticatedAdmin()

        assertEquals(OperationResult.Success(admin), result)
        assertEquals("new-access", store.session?.accessToken)
    }

    @Test
    fun logoutClearsSessionEvenWhenBackendIsUnavailable() = runBlocking {
        val store = FakeStore().apply { session = session() }
        val repository = AuthenticationRepositoryImpl(
            FakeApi(logoutResult = OperationResult.Failure(AdminError.Network)),
            store,
        )

        val result = repository.logout()

        assertEquals(OperationResult.Success(Unit), result)
        assertNull(store.session)
    }

    private fun session(
        accessToken: String = "access-token",
        refreshToken: String = "refresh-token",
        accessTokenExpiresAtEpochMillis: Long = System.currentTimeMillis() + 60_000,
        sessionExpiresAtEpochMillis: Long = System.currentTimeMillis() + 3_600_000,
    ) = AuthenticationSession(
        admin = admin,
        accessToken = accessToken,
        refreshToken = refreshToken,
        accessTokenExpiresAtEpochMillis = accessTokenExpiresAtEpochMillis,
        sessionExpiresAtEpochMillis = sessionExpiresAtEpochMillis,
    )
}

private class FakeStore : SessionStore {
    var session: AuthenticationSession? = null

    override fun readSession(): AuthenticationSession? = session
    override fun saveSession(session: AuthenticationSession) {
        this.session = session
    }
    override fun clearSession() {
        session = null
    }
}

private class FakeApi(
    private val loginResult: OperationResult<AuthenticationSession> =
        OperationResult.Failure(AdminError.Network),
    private val currentResult: OperationResult<AuthenticatedAdmin> =
        OperationResult.Success(
            AuthenticatedAdmin("admin-1", "admin@example.com", "ACTIVE", null),
        ),
    private val refreshResult: OperationResult<AuthenticationSession> =
        OperationResult.Failure(AdminError.SessionExpired),
    private val currentAfterRefresh: OperationResult<AuthenticatedAdmin> =
        OperationResult.Success(
            AuthenticatedAdmin("admin-1", "admin@example.com", "ACTIVE", null),
        ),
    private val logoutResult: OperationResult<Unit> = OperationResult.Success(Unit),
) : AuthenticationApi {
    override suspend fun login(
        credentials: AdminLoginCredentials,
    ): OperationResult<AuthenticationSession> = loginResult

    override suspend fun refresh(
        session: AuthenticationSession,
    ): OperationResult<AuthenticationSession> = refreshResult

    override suspend fun current(
        session: AuthenticationSession,
    ): OperationResult<AuthenticatedAdmin> =
        if (refreshResult is OperationResult.Success) currentAfterRefresh else currentResult

    override suspend fun logout(
        session: AuthenticationSession,
    ): OperationResult<Unit> = logoutResult
}
