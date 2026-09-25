package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.DeviceLocation
import com.parento.admin.domain.LocationAvailability
import com.parento.admin.domain.LocationFreshness
import com.parento.admin.domain.OperationResult
import com.parento.admin.location.DeviceLocationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationViewModel(
    private val deviceId: String,
    private val useCase: DeviceLocationUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LocationUiState>(LocationUiState.Loading)
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.value = LocationUiState.Loading
        viewModelScope.launch {
            when (val result = useCase(deviceId)) {
                is OperationResult.Success -> _uiState.value = result.value.toUiState()
                is OperationResult.Failure -> _uiState.value = result.error.toUiState()
            }
        }
    }

    fun refresh() = load()

    private fun DeviceLocation?.toUiState(): LocationUiState {
        if (this == null) return LocationUiState.NeverReported
        return when (availability) {
            LocationAvailability.NEVER_REPORTED -> LocationUiState.NeverReported
            LocationAvailability.UNAVAILABLE,
            LocationAvailability.UNKNOWN -> LocationUiState.Unavailable
            LocationAvailability.REVOKED -> LocationUiState.Revoked
            LocationAvailability.AVAILABLE -> when (freshness) {
                LocationFreshness.FRESH -> LocationUiState.Available(this)
                LocationFreshness.STALE -> LocationUiState.Stale(this)
                LocationFreshness.VERY_STALE -> LocationUiState.VeryStale(this)
                LocationFreshness.UNKNOWN -> LocationUiState.Available(this)
            }
        }
    }

    private fun AdminError.toUiState(): LocationUiState = when (this) {
        AdminError.Authorization,
        AdminError.SessionExpired,
        AdminError.SessionRevoked,
        AdminError.AccountDisabled -> LocationUiState.Unauthorized
        is AdminError.DeviceNotFound -> LocationUiState.Unavailable
        else -> LocationUiState.Error(
            message = when (this) {
                AdminError.Network -> "Network connection unavailable."
                AdminError.Timeout -> "The location request timed out."
                AdminError.Backend -> "Location retrieval is unavailable until the backend location contract is configured."
                AdminError.ServerUnavailable -> "The Parento server is temporarily unavailable."
                AdminError.Validation -> "The location response was invalid."
                else -> "The latest device location could not be retrieved."
            },
            canRetry = this !is AdminError.Authorization &&
                this !is AdminError.SessionExpired &&
                this !is AdminError.SessionRevoked &&
                this !is AdminError.AccountDisabled,
        )
    }
}