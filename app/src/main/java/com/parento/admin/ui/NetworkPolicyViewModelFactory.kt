package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.parento.admin.device.ManagedDeviceRepository
import com.parento.admin.policy.NetworkPolicyRepository

class NetworkPolicyViewModelFactory(
    private val repository: NetworkPolicyRepository,
    private val deviceRepository: ManagedDeviceRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        NetworkPolicyViewModel(repository, onSessionExpired, deviceRepository) as T
}
