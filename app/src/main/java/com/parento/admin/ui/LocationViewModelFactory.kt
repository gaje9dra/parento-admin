package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.parento.admin.location.DeviceLocationUseCase

class LocationViewModelFactory(
    private val deviceId: String,
    private val useCase: DeviceLocationUseCase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LocationViewModel::class.java))
        return LocationViewModel(deviceId, useCase) as T
    }
}