package com.parento.admin.ui

import com.parento.admin.domain.ManagedDevice

sealed interface AdminUiState {
    data object Loading : AdminUiState

    data object Empty : AdminUiState

    data class Content(
        val managedDevices: List<ManagedDevice>,
    ) : AdminUiState

    data class Error(
        val message: String,
        val canRetry: Boolean,
    ) : AdminUiState
}
