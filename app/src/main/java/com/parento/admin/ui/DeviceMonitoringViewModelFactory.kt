package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.parento.admin.domain.DeviceMonitoringRepository

class DeviceMonitoringViewModelFactory(
    private val repository: DeviceMonitoringRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DeviceMonitoringViewModel::class.java))
        return DeviceMonitoringViewModel(repository) as T
    }
}
