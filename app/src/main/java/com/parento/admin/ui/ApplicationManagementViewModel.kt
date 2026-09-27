package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.admin.application.*
import com.parento.admin.domain.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ApplicationManagementUiState {
    data object Loading : ApplicationManagementUiState
    data class Content(
        val deviceId: String,
        val deviceName: String,
        val inventory: ApplicationInventoryPage? = null,
        val policies: List<ApplicationPolicy> = emptyList(),
        val policyState: ApplicationPolicyState? = null,
        val query: String = "",
        val loading: Boolean = false,
        val message: String? = null,
    ) : ApplicationManagementUiState
    data class Error(val message: String, val retryable: Boolean = true) : ApplicationManagementUiState
}

class ApplicationManagementViewModel(
    private val repository: ApplicationManagementRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ApplicationManagementUiState>(ApplicationManagementUiState.Loading)
    val uiState: StateFlow<ApplicationManagementUiState> = _uiState

    fun open(deviceId: String, deviceName: String) {
        _uiState.value = ApplicationManagementUiState.Content(deviceId, deviceName, loading = true)
        refresh()
    }

    fun refresh() {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        viewModelScope.launch {
            val inventory = repository.getInventory(state.deviceId)
            val policies = repository.listPolicies().let { it }
            val policyState = repository.getPolicyState(state.deviceId)
            val results = listOf(inventory, policies, policyState)
            val failure = results.firstOrNull { it is OperationResult.Failure }
            if (failure is OperationResult.Failure) {
                if (failure.error == com.parento.admin.domain.AdminError.SessionExpired) onSessionExpired()
                _uiState.value = ApplicationManagementUiState.Error(failure.error.toString())
                return@launch
            }
            _uiState.value = state.copy(
                inventory = (inventory as OperationResult.Success).value,
                policies = (policies as OperationResult.Success).value.first,
                policyState = (policyState as OperationResult.Success).value,
                loading = false,
                message = null,
            )
        }
    }

    fun setQuery(value: String) {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        _uiState.value = state.copy(query = value)
    }

    fun requestInventory() {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        viewModelScope.launch {
            when (val result = repository.requestInventory(state.deviceId)) {
                is OperationResult.Success -> _uiState.value = state.copy(message = "Inventory synchronization requested.")
                is OperationResult.Failure -> handleFailure(result)
            }
        }
    }

    fun createPolicy(name: String, description: String?, rules: List<ApplicationPolicyRule>) {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        viewModelScope.launch {
            when (val result = repository.createPolicy(name, description, rules)) {
                is OperationResult.Success -> _uiState.value = state.copy(policies = state.policies + result.value, message = "Policy created.")
                is OperationResult.Failure -> handleFailure(result)
            }
        }
    }

    fun updatePolicy(policy: ApplicationPolicy, expectedVersion: Int) {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        viewModelScope.launch {
            when (val result = repository.updatePolicy(policy, expectedVersion)) {
                is OperationResult.Success -> _uiState.value = state.copy(
                    policies = state.policies.map { if (it.id == result.value.id) result.value else it },
                    message = "Policy updated to v" + result.value.version + ".",
                )
                is OperationResult.Failure -> handleFailure(result)
            }
        }
    }

    fun assignPolicy(policyId: String) {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        viewModelScope.launch {
            when (val result = repository.assignPolicy(state.deviceId, policyId)) {
                is OperationResult.Success -> _uiState.value = state.copy(policyState = result.value, message = "Policy assignment requested.")
                is OperationResult.Failure -> handleFailure(result)
            }
        }
    }

    fun removePolicy(policyId: String) {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        viewModelScope.launch {
            when (val result = repository.removePolicy(state.deviceId, policyId)) {
                is OperationResult.Success -> _uiState.value = state.copy(policyState = result.value, message = "Policy removal requested.")
                is OperationResult.Failure -> handleFailure(result)
            }
        }
    }

    fun syncPolicy() {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        viewModelScope.launch {
            when (val result = repository.syncPolicy(state.deviceId)) {
                is OperationResult.Success -> _uiState.value = state.copy(
                    policyState = state.policyState?.copy(synchronization = result.value),
                    message = "Policy synchronization requested.",
                )
                is OperationResult.Failure -> handleFailure(result)
            }
        }
    }

    private fun handleFailure(result: OperationResult.Failure) {
        if (result.error == com.parento.admin.domain.AdminError.SessionExpired) onSessionExpired()
        _uiState.value = (_uiState.value as? ApplicationManagementUiState.Content)?.copy(message = result.error.toString())
            ?: ApplicationManagementUiState.Error(result.error.toString())
    }
}

class ApplicationManagementViewModelFactory(
    private val repository: ApplicationManagementRepository,
    private val onSessionExpired: () -> Unit,
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ApplicationManagementViewModel(repository, onSessionExpired) as T
}
