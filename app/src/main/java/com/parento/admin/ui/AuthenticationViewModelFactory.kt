package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.parento.admin.auth.AuthenticationRepository

class AuthenticationViewModelFactory(
    private val repository: AuthenticationRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AuthenticationViewModel::class.java))
        return AuthenticationViewModel(repository) as T
    }
}
