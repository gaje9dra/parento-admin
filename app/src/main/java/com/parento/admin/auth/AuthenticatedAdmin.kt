package com.parento.admin.auth

data class AuthenticatedAdmin(
    val id: String,
    val email: String,
    val status: String,
    val lastAuthenticatedAt: String?,
)
