package com.parento.admin.auth

sealed interface AuthenticationState {
    data object Unauthenticated : AuthenticationState
    data object Authenticating : AuthenticationState
    data class Authenticated(val admin: AuthenticatedAdmin) : AuthenticationState
    data class AuthenticationError(
        val message: String,
        val recoverable: Boolean = true,
    ) : AuthenticationState
    data object SessionExpired : AuthenticationState
    data object SessionRevoked : AuthenticationState
    data object AccountDisabled : AuthenticationState
}
