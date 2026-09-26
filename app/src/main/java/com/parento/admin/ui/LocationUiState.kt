package com.parento.admin.ui

import com.parento.admin.domain.DeviceLocation

sealed interface LocationUiState {
    data object Loading : LocationUiState
    data object NeverReported : LocationUiState
    data object Unavailable : LocationUiState
    data object Unauthorized : LocationUiState
    data object Revoked : LocationUiState
    data class Available(val location: DeviceLocation) : LocationUiState
    data class Stale(val location: DeviceLocation) : LocationUiState
    data class VeryStale(val location: DeviceLocation) : LocationUiState
    data class Error(val message: String, val canRetry: Boolean) : LocationUiState
}