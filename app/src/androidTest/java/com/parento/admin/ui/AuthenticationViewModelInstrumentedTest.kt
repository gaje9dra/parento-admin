package com.parento.admin.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parento.admin.auth.AdminLoginCredentials
import com.parento.admin.auth.AuthenticatedAdmin
import com.parento.admin.auth.AuthenticationSession
import com.parento.admin.auth.AuthenticationState
import com.parento.admin.auth.AuthenticationRepository
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticationViewModelInstrumentedTest {
    private val admin = AuthenticatedAdmin(
        id = "admin-1",
        email = "admin@example.com",
        status = "ACTIVE",
        lastAuthenticatedAt = null,
    )

    @Test
    fun duplicateLoginSubmissionsProduceOneAuthenticationRequest() = runBlocking {
        val repository = FakeRepository(loginDelayMillis = 150)
        val viewModel = AuthenticationViewModel(repository)

        viewModel.login("admin@example.com", "correct password")
        viewModel.login("admin@example.com", "correct password")

        delay(350)

        assertEquals(1, repository.loginCalls)
        assertEquals(AuthenticationState.Authenticated(admin), viewModel.state.value)
    }

    @Test
    fun duplicateLoginSubmissionsAreNotRetriedAfterTheFirstFailure() = runBlocking {
        val repository = FakeRepository(
            loginDelayMillis = 150,
            loginResult = OperationResult.Failure(AdminError.Network),
        )
        val viewModel = AuthenticationViewModel(repository)

        viewModel.login("admin@example.com", "correct password")
        viewModel.login("admin@example.com", "correct password")

        delay(350)

        assertEquals(1, repository.loginCalls)
        assertEquals(
            AuthenticationState.AuthenticationError(
                "Unable to reach the Parento server.",
            ),
            viewModel.state.value,
        )
    }

    @Test
    fun logoutTransitionsToUnauthenticatedWhenBackendLogoutFails() = runBlocking {
        val repository = FakeRepository(
            logoutResult = OperationResult.Failure(AdminError.Network),
        )
        val viewModel = AuthenticationViewModel(repository)

        viewModel.login("admin@example.com", "correct password")
        delay(100)
        viewModel.logout()
        delay(100)

        assertEquals(AuthenticationState.Unauthenticated, viewModel.state.value)
        assertTrue(repository.logoutCalls > 0)
    }

    @Test
    fun revokedSessionMapsToRevokedState() = runBlocking {
        val repository = FakeRepository(
            restoreResult = OperationResult.Failure(AdminError.SessionRevoked),
        )
        val viewModel = AuthenticationViewModel(repository)

        viewModel.restoreSession()
        delay(100)

        assertEquals(AuthenticationState.SessionRevoked, viewModel.state.value)
    }

    private fun session() = AuthenticationSession(
        admin = admin,
        accessToken = "access-token-12345678901234567890",
        refreshToken = "refresh-token-12345678901234567890",
        accessTokenExpiresAtEpochMillis = System.currentTimeMillis() + 60_000,
        sessionExpiresAtEpochMillis = System.currentTimeMillis() + 3_600_000,
    )

    private class FakeRepository(
        private val loginDelayMillis: Long = 0,
        private val logoutResult: OperationResult<Unit> = OperationResult.Success(Unit),
        private val loginResult: OperationResult<AuthenticatedAdmin> =
            OperationResult.Success(admin),
        private val restoreResult: OperationResult<AuthenticatedAdmin?> =
            OperationResult.Success(null),
    ) : AuthenticationRepository {
        var loginCalls = 0
        var logoutCalls = 0

        override suspend fun login(
            credentials: AdminLoginCredentials,
        ): OperationResult<AuthenticatedAdmin> {
            loginCalls += 1
            delay(loginDelayMillis)
            return loginResult
        }

        override suspend fun logout(): OperationResult<Unit> {
            logoutCalls += 1
            return logoutResult
        }

        override suspend fun getCurrentAuthenticatedAdmin(): OperationResult<AuthenticatedAdmin> =
            OperationResult.Success(
                AuthenticatedAdmin(
                    id = "admin-1",
                    email = "admin@example.com",
                    status = "ACTIVE",
                    lastAuthenticatedAt = null,
                ),
            )

        override suspend fun restoreSession(): OperationResult<AuthenticatedAdmin?> =
            restoreResult
    }
}
