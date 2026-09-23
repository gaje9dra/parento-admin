package com.parento.admin.ui

import com.parento.admin.auth.AdminLoginCredentials
import com.parento.admin.auth.AuthenticatedAdmin
import com.parento.admin.auth.AuthenticationRepository
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationViewModelTest {
    private val admin = AuthenticatedAdmin(
        id = "admin-1",
        email = "admin@example.com",
        status = "ACTIVE",
        lastAuthenticatedAt = null,
    )

    @Test
    fun unauthorizedSessionTransitionsToExpired() = runBlocking {
        val viewModel = AuthenticationViewModel(
            FakeRepository(
                currentResult = OperationResult.Failure(AdminError.SessionExpired),
            ),
        )

        viewModel.restoreSession()
        waitForState(viewModel) { it is com.parento.admin.auth.AuthenticationState.SessionExpired }

        assertTrue(
            viewModel.state.value is com.parento.admin.auth.AuthenticationState.SessionExpired,
        )
    }

    @Test
    fun duplicateLoginCallsSerializeToOneAuthenticationRequest() = runBlocking {
        val repository = FakeRepository(loginDelayMillis = 100)
        val viewModel = AuthenticationViewModel(repository)

        viewModel.login("admin@example.com", "correct password")
        viewModel.login("admin@example.com", "correct password")

        delay(250)

        assertEquals(1, repository.loginCalls)
        assertEquals(
            com.parento.admin.auth.AuthenticationState.Authenticated(admin),
            viewModel.state.value,
        )
    }

    @Test
    fun logoutClearsAuthenticatedStateWhenBackendLogoutFails() = runBlocking {
        val repository = FakeRepository(
            loginResult = OperationResult.Success(session().let { it.admin }),
            logoutResult = OperationResult.Failure(AdminError.Network),
        )
        val viewModel = AuthenticationViewModel(repository)

        viewModel.login("admin@example.com", "correct password")
        delay(50)
        viewModel.logout()
        delay(50)

        assertEquals(
            com.parento.admin.auth.AuthenticationState.Unauthenticated,
            viewModel.state.value,
        )
    }

    private fun session() = com.parento.admin.auth.AuthenticationSession(
        admin = admin,
        accessToken = "access-token-12345678901234567890",
        refreshToken = "refresh-token-12345678901234567890",
        accessTokenExpiresAtEpochMillis = System.currentTimeMillis() + 60_000,
        sessionExpiresAtEpochMillis = System.currentTimeMillis() + 3_600_000,
    )

    private suspend fun waitForState(
        viewModel: AuthenticationViewModel,
        predicate: (com.parento.admin.auth.AuthenticationState) -> Boolean,
    ) {
        repeat(20) {
            if (predicate(viewModel.state.value)) return
            delay(10)
        }
    }
}

private class FakeRepository(
    private val loginResult: OperationResult<AuthenticatedAdmin> =
        OperationResult.Success(
            AuthenticatedAdmin("admin-1", "admin@example.com", "ACTIVE", null),
        ),
    private val currentResult: OperationResult<AuthenticatedAdmin> =
        OperationResult.Success(
            AuthenticatedAdmin("admin-1", "admin@example.com", "ACTIVE", null),
        ),
    private val logoutResult: OperationResult<Unit> = OperationResult.Success(Unit),
    private val loginDelayMillis: Long = 0,
) : AuthenticationRepository {
    var loginCalls = 0
        private set

    override suspend fun login(
        credentials: AdminLoginCredentials,
    ): OperationResult<AuthenticatedAdmin> {
        loginCalls += 1
        delay(loginDelayMillis)
        return loginResult
    }

    override suspend fun logout(): OperationResult<Unit> = logoutResult

    override suspend fun getCurrentAuthenticatedAdmin(): OperationResult<AuthenticatedAdmin> =
        currentResult

    override suspend fun restoreSession(): OperationResult<AuthenticatedAdmin?> =
        currentResult.let {
            when (it) {
                is OperationResult.Success -> OperationResult.Success(it.value)
                is OperationResult.Failure -> it
            }
        }
}
