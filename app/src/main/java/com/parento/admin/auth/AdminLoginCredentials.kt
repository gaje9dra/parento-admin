package com.parento.admin.auth

data class AdminLoginCredentials(
    val email: String,
    val password: String,
) {
    fun validate(): Boolean =
        email.trim().isNotEmpty() && email.trim().contains('@') && password.length in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH
}

private const val PASSWORD_MIN_LENGTH = 15
private const val PASSWORD_MAX_LENGTH = 256
