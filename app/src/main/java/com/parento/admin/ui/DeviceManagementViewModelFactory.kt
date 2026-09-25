package com.parento.admin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.parento.admin.device.ManagedDeviceRepository

class DeviceManagementViewModelFactory(
    private val repository: ManagedDeviceRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.MutableCreationExtras): T {
        if (modelClass.isAssignableFrom(DeviceManagementViewModel::class.java)) {
            return DeviceManagementViewModel(repository, onSessionExpired) as T
        }
        return super.create(modelClass, extras)
    }
}
