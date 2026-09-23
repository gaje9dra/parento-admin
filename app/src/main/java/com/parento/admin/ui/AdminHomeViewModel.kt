package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.ManagedDevice

class AdminHomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<AdminUiState>(AdminUiState.Empty)
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    fun showLoading() {
        _uiState.value = AdminUiState.Loading
    }

    fun showEmpty() {
        _uiState.value = AdminUiState.Empty
    }

    fun showDevices(devices: List<ManagedDevice>) {
        _uiState.value = if (devices.isEmpty()) {
            AdminUiState.Empty
        } else {
            AdminUiState.Content(devices)
        }
    }

    fun showError(error: AdminError, canRetry: Boolean = true) {
        _uiState.value = AdminUiState.Error(
            message = errorMessage(error),
            canRetry = canRetry,
        )
    }

    private fun errorMessage(error: AdminError): String = when (error) {
        AdminError.Authentication -> "Administrator authentication is unavailable in this phase."
        AdminError.Authorization -> "This action is not authorized."
        AdminError.Network -> "A network connection is unavailable."
        is AdminError.DeviceNotFound -> "The requested device could not be found."
        AdminError.Policy -> "The policy operation could not be completed."
        AdminError.Backend -> "The management service is temporarily unavailable."
        AdminError.LocalStorage -> "Local application data could not be read."
        AdminError.Unknown -> "Something went wrong. Please try again."
    }
}
