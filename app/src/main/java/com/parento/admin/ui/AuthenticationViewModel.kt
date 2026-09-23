package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.admin.auth.AdminLoginCredentials
import com.parento.admin.auth.AuthenticationRepository
import com.parento.admin.auth.AuthenticationState
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthenticationViewModel(
    private val repository: AuthenticationRepository,
) : ViewModel() {
    private val operationMutex = Mutex()
    private var restoreStarted = false
    private var emailDraft = ""

    fun emailDraft(): String = emailDraft

    private val _state = MutableStateFlow<AuthenticationState>(
        AuthenticationState.Unauthenticated,
    )
    val state: StateFlow<AuthenticationState> = _state.asStateFlow()

    fun restoreSession() {
        if (restoreStarted) return
        restoreStarted = true

        viewModelScope.launch {
            operationMutex.withLock {
                _state.value = AuthenticationState.Authenticating
                when (val result = repository.restoreSession()) {
                    is OperationResult.Success -> {
                        _state.value = result.value?.let {
                            AuthenticationState.Authenticated(it)
                        } ?: AuthenticationState.Unauthenticated
                    }
                    is OperationResult.Failure -> {
                        _state.value = stateForFailure(result.error)
                    }
                }
            }
        }
    }

    fun login(email: String, password: String) {
        emailDraft = email.trim()
        if (_state.value is AuthenticationState.Authenticating) return

        viewModelScope.launch {
            operationMutex.withLock {
                if (_state.value is AuthenticationState.Authenticated) return@withLock

                _state.value = AuthenticationState.Authenticating
                when (
                    val result = repository.login(
                        AdminLoginCredentials(email, password),
                    )
                ) {
                    is OperationResult.Success -> {
                        _state.value = AuthenticationState.Authenticated(result.value)
                    }
                    is OperationResult.Failure -> {
                        _state.value = stateForFailure(result.error)
                    }
                }
            }
        }
    }

    fun validateCurrentSession() {
        if (_state.value !is AuthenticationState.Authenticated) return

        viewModelScope.launch {
            operationMutex.withLock {
                when (val result = repository.getCurrentAuthenticatedAdmin()) {
                    is OperationResult.Success -> {
                        _state.value = AuthenticationState.Authenticated(result.value)
                    }
                    is OperationResult.Failure -> {
                        _state.value = stateForFailure(result.error)
                    }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            operationMutex.withLock {
                repository.logout()
                _state.value = AuthenticationState.Unauthenticated
            }
        }
    }

    private fun stateForFailure(error: AdminError): AuthenticationState = when (error) {
        AdminError.SessionExpired -> AuthenticationState.SessionExpired
        AdminError.SessionRevoked -> AuthenticationState.SessionRevoked
        AdminError.AccountDisabled -> AuthenticationState.AccountDisabled
        else -> AuthenticationState.AuthenticationError(
            message = messageFor(error),
        )
    }

    private fun messageFor(error: AdminError): String = when (error) {
        AdminError.InvalidCredentials -> "Invalid email or password."
        AdminError.AccountDisabled -> "This administrator account is disabled."
        AdminError.Network -> "Unable to reach the Parento server."
        AdminError.Timeout -> "The request timed out. Please try again."
        AdminError.ServerUnavailable -> "The Parento server is temporarily unavailable."
        AdminError.AuthenticationRateLimited -> "Too many authentication attempts. Please wait and try again."
        AdminError.SessionExpired -> "Your session has expired. Please sign in again."
        AdminError.SessionRevoked -> "Your session is no longer valid. Please sign in again."
        AdminError.Authorization -> "This administrator action is not authorized."
        AdminError.Validation -> "Enter a valid email and password."
        AdminError.Authentication -> "Administrator authentication failed."
        else -> "Authentication could not be completed. Please try again."
    }
}
