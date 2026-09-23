package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.admin.auth.AdminLoginCredentials
import com.parento.admin.auth.AuthenticatedAdmin
import com.parento.admin.auth.AuthenticationRepository
import com.parento.admin.auth.AuthenticationState
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthenticationViewModel(
    private val repository: AuthenticationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthenticationState>(
        AuthenticationState.Authenticating,
    )
    val state: StateFlow<AuthenticationState> = _state.asStateFlow()

    fun restoreSession() {
        if (_state.value is AuthenticationState.Authenticated) return
        viewModelScope.launch {
            _state.value = AuthenticationState.Authenticating
            when (val result = repository.restoreSession()) {
                is OperationResult.Success -> {
                    _state.value = result.value?.let {
                        AuthenticationState.Authenticated(it)
                    } ?: AuthenticationState.Unauthenticated
                }
                is OperationResult.Failure -> {
                    _state.value = AuthenticationState.AuthenticationError(
                        messageFor(result.error),
                    )
                }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthenticationState.Authenticating
            when (
                val result = repository.login(
                    AdminLoginCredentials(email, password),
                )
            ) {
                is OperationResult.Success ->
                    _state.value = AuthenticationState.Authenticated(result.value)
                is OperationResult.Failure ->
                    _state.value = AuthenticationState.AuthenticationError(
                        messageFor(result.error),
                    )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _state.value = AuthenticationState.Unauthenticated
        }
    }

    private fun messageFor(error: AdminError): String = when (error) {
        AdminError.InvalidCredentials -> "Invalid email or password."
        AdminError.AccountDisabled -> "This administrator account is disabled."
        AdminError.Network -> "Unable to reach the Parento server."
        AdminError.Timeout -> "The request timed out. Please try again."
        AdminError.ServerUnavailable -> "The Parento server is temporarily unavailable."
        AdminError.SessionExpired -> "Your session has expired. Please sign in again."
        AdminError.Validation -> "Enter a valid email and password."
        AdminError.Authentication -> "Administrator authentication failed."
        else -> "Authentication could not be completed. Please try again."
    }
}
