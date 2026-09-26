package com.parento.admin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.ManagedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdminHomeViewModel(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<AdminUiState>(restoreState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    fun showLoading() {
        updateState(AdminUiState.Loading)
    }

    fun showEmpty() {
        updateState(AdminUiState.Empty)
    }

    fun showDevices(devices: List<ManagedDevice>) {
        updateState(
            if (devices.isEmpty()) AdminUiState.Empty else AdminUiState.Content(devices),
        )
    }

    fun showError(error: AdminError, canRetry: Boolean = true) {
        updateState(
            AdminUiState.Error(
                message = errorMessage(error),
                canRetry = canRetry,
            ),
        )
    }

    private fun updateState(state: AdminUiState) {
        _uiState.value = state
        savedStateHandle[STATE_KEY] = when (state) {
            AdminUiState.Loading -> STATE_LOADING
            AdminUiState.Empty -> STATE_EMPTY
            is AdminUiState.Content -> STATE_CONTENT
            is AdminUiState.Error -> {
                savedStateHandle[ERROR_MESSAGE_KEY] = state.message
                savedStateHandle[ERROR_RETRY_KEY] = state.canRetry
                STATE_ERROR
            }
        }
    }

    private fun restoreState(): AdminUiState =
        when (savedStateHandle.get<String>(STATE_KEY)) {
            STATE_LOADING -> AdminUiState.Loading
            STATE_ERROR -> {
                val message = savedStateHandle.get<String>(ERROR_MESSAGE_KEY)
                if (message != null) {
                    AdminUiState.Error(
                        message = message,
                        canRetry = savedStateHandle[ERROR_RETRY_KEY] ?: false,
                    )
                } else {
                    AdminUiState.Empty
                }
            }
            // Device records are not persisted in Phase 1, so content safely returns
            // to the neutral empty state after process recreation.
            STATE_CONTENT, STATE_EMPTY, null -> AdminUiState.Empty
            else -> AdminUiState.Empty
        }

    private fun errorMessage(error: AdminError): String = when (error) {
        AdminError.Authentication -> "Administrator authentication is unavailable."
        AdminError.Authorization -> "This action is not authorized."
        AdminError.Network -> "A network connection is unavailable."
        AdminError.Timeout -> "The request timed out. Please try again."
        AdminError.InvalidCredentials -> "Invalid email or password."
        AdminError.AccountDisabled -> "This administrator account is disabled."
        AdminError.SessionExpired -> "Your session has expired. Please sign in again."
        AdminError.SessionRevoked -> "Your session is no longer valid. Please sign in again."
        AdminError.Validation -> "The authentication request was invalid."
        AdminError.ServerUnavailable -> "The Parento server is temporarily unavailable."
        AdminError.AuthenticationRateLimited -> "Too many authentication attempts. Please wait and try again."
        AdminError.EnrollmentNotFound -> "The enrollment session could not be found."
        AdminError.EnrollmentExpired -> "The enrollment session has expired."
        AdminError.EnrollmentStateConflict -> "The enrollment is no longer in an active state."
        AdminError.EnrollmentAlreadyConsumed -> "The enrollment has already been completed."
        AdminError.EnrollmentRateLimited -> "Too many enrollment requests. Please wait and try again."
        AdminError.EnrollmentNotFound -> "The enrollment session could not be found."
        AdminError.EnrollmentExpired -> "The enrollment session has expired."
        AdminError.EnrollmentStateConflict -> "The enrollment is no longer in an active state."
        AdminError.EnrollmentAlreadyConsumed -> "The enrollment has already been completed."
        AdminError.EnrollmentRateLimited -> "Too many enrollment requests. Please wait and try again."
        AdminError.UnknownAuthentication -> "Authentication could not be completed."
        is AdminError.DeviceNotFound -> "The requested device could not be found."
        AdminError.Policy -> "The policy operation could not be completed."
        AdminError.Backend -> "The management service is temporarily unavailable."
        AdminError.LocalStorage -> "Local application data could not be read."
        AdminError.InvalidState -> "The requested local state transition is not valid."
        AdminError.Unknown -> "Something went wrong. Please try again."
    }

    private companion object {
        const val STATE_KEY = "admin_home_state"
        const val STATE_LOADING = "loading"
        const val STATE_EMPTY = "empty"
        const val STATE_CONTENT = "content"
        const val STATE_ERROR = "error"
        const val ERROR_MESSAGE_KEY = "admin_error_message"
        const val ERROR_RETRY_KEY = "admin_error_can_retry"
    }
}
