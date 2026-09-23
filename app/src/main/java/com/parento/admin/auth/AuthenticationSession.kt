package com.parento.admin.auth

data class AuthenticationSession(
    val admin: AuthenticatedAdmin,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAtEpochMillis: Long,
    val sessionExpiresAtEpochMillis: Long,
)
