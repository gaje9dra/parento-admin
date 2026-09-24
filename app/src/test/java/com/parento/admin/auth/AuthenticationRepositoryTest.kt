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
            AdminLoginCredentials("admin@example.com", "valid administrator password"),
        )

        assertEquals(OperationResult.Success(admin), result)
        assertEquals(session, store.session)
    }

    @Test
    fun shortPasswordIsRejectedBeforeApiCall() = runBlocking {
        val api = FakeApi()
        val store = FakeStore()
        val repository = AuthenticationRepositoryImpl(api, store)

        val result = repository.login(
            AdminLoginCredentials("admin@example.com", "short"),
        )

        assertEquals(OperationResult.Failure(AdminError.Validation), result)
        assertNull(store.session)
    }

    @Test
    fun invalidCredentialsRemainUnauthenticated() = runBlocking {
        val api = FakeApi(
            loginResult = OperationResult.Failure(AdminError.InvalidCredentials),
        )
        val store = FakeStore()
        val repository = AuthenticationRepositoryImpl(api, store)

        val result = repository.login(
            AdminLoginCredentials("admin@example.com", "wrong administrator password"),
        )

        assertEquals(OperationResult.Failure(AdminError.InvalidCredentials), result)
        assertNull(store.session)
    }

    @Test
    fun currentAdminRefreshesStoredServerAuthoritativeIdentity() = runBlocking {
        val store = FakeStore().apply { session = session() }
        val updatedAdmin = admin.copy(email = "updated@example.com")
        val repository = AuthenticationRepositoryImpl(
            FakeApi(currentResult = OperationResult.Success(updatedAdmin)),
            store,
        )

        val result = repository.getCurrentAuthenticatedAdmin()

        assertEquals(OperationResult.Success(updatedAdmin), result)
        assertEquals(updatedAdmin, store.session?.admin)
    }

    @Test
    fun currentAdminNetworkFailureDoesNotInventAuthenticatedIdentity() = runBlocking {
        val store = FakeStore().apply { session = session() }
        val repository = AuthenticationRepositoryImpl(
            FakeApi(currentResult = OperationResult.Failure(AdminError.Network)),
            store,
        )

        val result = repository.getCurrentAuthenticatedAdmin()

        assertEquals(OperationResult.Failure(AdminError.Network), result)
        assertEquals(admin, store.session?.admin)
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
    fun disabledIdentityReturnedByBackendIsNotAuthenticated() = runBlocking {
        val disabledAdmin = admin.copy(status = "DISABLED")
        val store = FakeStore()
        val repository = AuthenticationRepositoryImpl(
            FakeApi(
                loginResult = OperationResult.Success(
                    session().copy(admin = disabledAdmin),
                ),
            ),
            store,
        )

        val result = repository.login(
            AdminLoginCredentials(
                "admin@example.com",
                "valid administrator password",
            ),
        )

        assertEquals(
            OperationResult.Failure(AdminError.AccountDisabled),
            result,
        )
        assertNull(store.session)
    }

    @Test
    fun expiredAccessTokenRefreshesBeforeAuthenticatedRequest() = runBlocking {
        val expired = session(
            accessTokenExpiresAtEpochMillis = System.currentTimeMillis() - 1,
        )
        val refreshed = session(
            accessToken = "new-access",
            refreshToken = "new-refresh",
        )
        val api = FakeApi(
            currentResult = OperationResult.Failure(AdminError.InvalidState),
            refreshResult = OperationResult.Success(refreshed),
            currentAfterRefresh = OperationResult.Success(admin),
        )
        val store = FakeStore().apply { session = expired }
        val repository = AuthenticationRepositoryImpl(api, store)

        val result = repository.getCurrentAuthenticatedAdmin()

        assertEquals(OperationResult.Success(admin), result)
        assertEquals("new-access", store.session?.accessToken)
        assertEquals(1, api.refreshCalls)
        assertEquals(1, api.currentCalls)
    }

    @Test
    fun refreshFailureIsPropagatedAfterCredentialsAreCleared() = runBlocking {
        val store = FakeStore().apply {
            session = session(
                accessTokenExpiresAtEpochMillis = System.currentTimeMillis() - 1,
            )
        }
        val repository = AuthenticationRepositoryImpl(
            FakeApi(refreshResult = OperationResult.Failure(AdminError.AccountDisabled)),
            store,
        )

        val result = repository.getCurrentAuthenticatedAdmin()

        assertEquals(
            OperationResult.Failure(AdminError.AccountDisabled),
            result,
        )
        assertNull(store.session)
    }

    @Test
    fun revokedSessionIsCleared() = runBlocking {
        val store = FakeStore().apply { session = session() }
        val repository = AuthenticationRepositoryImpl(
            FakeApi(currentResult = OperationResult.Failure(AdminError.SessionRevoked)),
            store,
        )

        val result = repository.getCurrentAuthenticatedAdmin()

        assertEquals(OperationResult.Failure(AdminError.SessionRevoked), result)
        assertNull(store.session)
    }

    @Test
    fun disabledAccountSessionIsCleared() = runBlocking {
        val store = FakeStore().apply { session = session() }
        val repository = AuthenticationRepositoryImpl(
            FakeApi(currentResult = OperationResult.Failure(AdminError.AccountDisabled)),
            store,
        )

        val result = repository.getCurrentAuthenticatedAdmin()

        assertEquals(OperationResult.Failure(AdminError.AccountDisabled), result)
        assertNull(store.session)
    }

    @Test
    fun restoreClearsRevokedSession() = runBlocking {
        val store = FakeStore().apply { session = session() }
        val repository = AuthenticationRepositoryImpl(
            FakeApi(currentResult = OperationResult.Failure(AdminError.SessionRevoked)),
            store,
        )

        val result = repository.restoreSession()

        assertEquals(
            OperationResult.Failure(AdminError.SessionRevoked),
            result,
        )
        assertNull(store.session)
    }

    @Test
    fun restoreClearsDisabledAccountSession() = runBlocking {
        val store = FakeStore().apply { session = session() }
        val repository = AuthenticationRepositoryImpl(
            FakeApi(currentResult = OperationResult.Failure(AdminError.AccountDisabled)),
            store,
        )

        val result = repository.restoreSession()

        assertEquals(
            OperationResult.Failure(AdminError.AccountDisabled),
            result,
        )
        assertNull(store.session)
    }

    @Test
    fun restoreClearsAuthorizationRejectedSession() = runBlocking {
        val store = FakeStore().apply { session = session() }
        val repository = AuthenticationRepositoryImpl(
            FakeApi(currentResult = OperationResult.Failure(AdminError.Authorization)),
            store,
        )

        val result = repository.restoreSession()

        assertEquals(
            OperationResult.Failure(AdminError.Authorization),
            result,
        )
        assertNull(store.session)
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
    var currentCalls = 0
    var refreshCalls = 0
    override suspend fun login(
        credentials: AdminLoginCredentials,
    ): OperationResult<AuthenticationSession> = loginResult

    override suspend fun refresh(
        session: AuthenticationSession,
    ): OperationResult<AuthenticationSession> {
        refreshCalls += 1
        return refreshResult
    }

    override suspend fun current(
        session: AuthenticationSession,
    ): OperationResult<AuthenticatedAdmin> {
        currentCalls += 1
        return if (refreshCalls > 0) currentAfterRefresh else currentResult
    }

    override suspend fun logout(
        session: AuthenticationSession,
    ): OperationResult<Unit> = logoutResult
}
