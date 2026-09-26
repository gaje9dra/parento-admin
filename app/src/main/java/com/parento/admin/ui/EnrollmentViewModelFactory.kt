package com.parento.admin.ui

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.parento.admin.domain.EnrollmentRepository

class EnrollmentViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val repository: EnrollmentRepository,
    private val onSessionExpired: () -> Unit = {},
) : AbstractSavedStateViewModelFactory(owner, null) {
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
        if (modelClass.isAssignableFrom(EnrollmentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EnrollmentViewModel(repository, handle, onSessionExpired) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: " + modelClass.name)
    }
}