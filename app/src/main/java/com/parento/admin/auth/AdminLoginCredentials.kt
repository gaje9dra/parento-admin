package com.parento.admin.auth

data class AdminLoginCredentials(
    val email: String,
    val password: String,
) {
    fun validate(): Boolean =
        email.trim().isNotEmpty() && email.trim().contains('@') && password.isNotEmpty()
}
