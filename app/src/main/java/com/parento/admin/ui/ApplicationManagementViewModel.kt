package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.parento.admin.application.*
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

enum class ApplicationDataConnectionState { LIVE, OFFLINE, UNKNOWN }

sealed interface ApplicationManagementUiState {
    data object Loading : ApplicationManagementUiState
    data class Content(
        val deviceId: String,
        val deviceName: String,
        val inventory: ApplicationInventoryPage? = null,
        val inventoryNextCursor: String? = null,
        val policies: List<ApplicationPolicy> = emptyList(),
        val policyNextCursor: String? = null,
        val policyState: ApplicationPolicyState? = null,
        val command: ApplicationManagementCommand? = null,
        val query: String = "",
        val loading: Boolean = false,
        val inventoryLoadingMore: Boolean = false,
        val policyLoadingMore: Boolean = false,
        val actionBusy: Boolean = false,
        val connection: ApplicationDataConnectionState = ApplicationDataConnectionState.UNKNOWN,
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
    private val generation = AtomicInteger(0)

    fun clear() {
        generation.incrementAndGet()
        _uiState.value = ApplicationManagementUiState.Loading
    }

    fun open(deviceId: String, deviceName: String) {
        generation.incrementAndGet()
        _uiState.value = ApplicationManagementUiState.Content(
            deviceId = deviceId,
            deviceName = deviceName,
            loading = true,
        )
        refresh()
    }

    fun refresh() {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        if (state.loading) return
        val requestGeneration = generation.get()
        _uiState.value = state.copy(loading = true, message = null)
        viewModelScope.launch {
            val inventory = repository.getInventory(state.deviceId)
            val policies = repository.listPolicies()
            val policyState = repository.getPolicyState(state.deviceId)
            if (requestGeneration != generation.get()) return@launch

            val failure = listOf(inventory, policies, policyState)
                .filterIsInstance<OperationResult.Failure>()
                .firstOrNull()
            if (failure != null) {
                handleRefreshFailure(failure.error, state, requestGeneration)
                return@launch
            }

            val inventorySuccess = inventory as OperationResult.Success
            val policiesSuccess = policies as OperationResult.Success
            val policyStateSuccess = policyState as OperationResult.Success
            _uiState.value = state.copy(
                inventory = inventorySuccess.value,
                inventoryNextCursor = inventorySuccess.value.nextCursor,
                policies = policiesSuccess.value.first,
                policyNextCursor = policiesSuccess.value.second,
                policyState = policyStateSuccess.value,
                loading = false,
                connection = ApplicationDataConnectionState.LIVE,
                message = null,
            )
        }
    }

    fun loadMoreInventory() {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        val cursor = state.inventoryNextCursor ?: return
        if (state.inventoryLoadingMore || state.actionBusy) return
        _uiState.value = state.copy(inventoryLoadingMore = true)
        val requestGeneration = generation.get()
        viewModelScope.launch {
            when (val result = repository.getInventory(state.deviceId, cursor)) {
                is OperationResult.Success -> if (requestGeneration == generation.get()) {
                    val current = _uiState.value as? ApplicationManagementUiState.Content ?: return@launch
                    val currentInventory = current.inventory
                    _uiState.value = current.copy(
                        inventory = result.value.copy(
                            applications = currentInventory?.applications.orEmpty() + result.value.applications,
                        ),
                        inventoryNextCursor = result.value.nextCursor,
                        inventoryLoadingMore = false,
                        connection = ApplicationDataConnectionState.LIVE,
                        message = null,
                    )
                }
                is OperationResult.Failure -> if (requestGeneration == generation.get()) {
                    handleActionFailure(result.error, keepContent = true)
                }
            }
        }
    }

    fun loadMorePolicies() {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        val cursor = state.policyNextCursor ?: return
        if (state.policyLoadingMore || state.actionBusy) return
        _uiState.value = state.copy(policyLoadingMore = true)
        val requestGeneration = generation.get()
        viewModelScope.launch {
            when (val result = repository.listPolicies(cursor)) {
                is OperationResult.Success -> if (requestGeneration == generation.get()) {
                    val current = _uiState.value as? ApplicationManagementUiState.Content ?: return@launch
                    _uiState.value = current.copy(
                        policies = current.policies + result.value.first,
                        policyNextCursor = result.value.second,
                        policyLoadingMore = false,
                        connection = ApplicationDataConnectionState.LIVE,
                        message = null,
                    )
                }
                is OperationResult.Failure -> if (requestGeneration == generation.get()) {
                    handleActionFailure(result.error, keepContent = true)
                }
            }
        }
    }

    fun setQuery(value: String) {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        _uiState.value = state.copy(query = value)
    }

    fun requestInventory() {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        if (state.actionBusy || state.connection == ApplicationDataConnectionState.OFFLINE) return
        _uiState.value = state.copy(actionBusy = true, message = null)
        viewModelScope.launch {
            when (val result = repository.requestInventory(state.deviceId)) {
                is OperationResult.Success -> {
                    val current = _uiState.value as? ApplicationManagementUiState.Content ?: return@launch
                    _uiState.value = current.copy(
                        actionBusy = false,
                        command = result.value,
                        message = "Inventory sync requested. Command status: ${result.value.status.name}.",
                        connection = ApplicationDataConnectionState.LIVE,
                    )
                }
                is OperationResult.Failure -> handleActionFailure(result.error, keepContent = true)
            }
        }
    }

    fun createPolicy(name: String, description: String?, rules: List<ApplicationPolicyRule>) {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        if (state.actionBusy || state.connection == ApplicationDataConnectionState.OFFLINE) return
        _uiState.value = state.copy(actionBusy = true)
        viewModelScope.launch {
            when (val result = repository.createPolicy(name, description, rules)) {
                is OperationResult.Success -> {
                    val current = _uiState.value as? ApplicationManagementUiState.Content ?: return@launch
                    _uiState.value = current.copy(
                        policies = current.policies + result.value,
                        actionBusy = false,
                        message = "Policy created.",
                    )
                }
                is OperationResult.Failure -> handleActionFailure(result.error, keepContent = true)
            }
        }
    }

    fun updatePolicy(policy: ApplicationPolicy, expectedVersion: Int) {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        if (state.actionBusy || state.connection == ApplicationDataConnectionState.OFFLINE) return
        _uiState.value = state.copy(actionBusy = true)
        viewModelScope.launch {
            when (val result = repository.updatePolicy(policy, expectedVersion)) {
                is OperationResult.Success -> {
                    val current = _uiState.value as? ApplicationManagementUiState.Content ?: return@launch
                    _uiState.value = current.copy(
                        policies = current.policies.map { if (it.id == result.value.id) result.value else it },
                        actionBusy = false,
                        message = "Policy updated to v${result.value.version}.",
                    )
                }
                is OperationResult.Failure -> handleActionFailure(result.error, keepContent = true)
            }
        }
    }

    fun assignPolicy(policyId: String) {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        if (state.actionBusy || state.connection == ApplicationDataConnectionState.OFFLINE) return
        val policy = state.policies.firstOrNull { it.id == policyId } ?: return
        if (policy.status != PolicyStatus.ACTIVE) {
            _uiState.value = state.copy(message = "Disabled policies cannot be assigned.")
            return
        }
        _uiState.value = state.copy(actionBusy = true)
        viewModelScope.launch {
            when (val result = repository.assignPolicy(state.deviceId, policyId)) {
                is OperationResult.Success -> {
                    val current = _uiState.value as? ApplicationManagementUiState.Content ?: return@launch
                    _uiState.value = current.copy(
                        policyState = result.value,
                        actionBusy = false,
                        message = "Desired policy assignment updated. Enforcement is reported separately.",
                    )
                }
                is OperationResult.Failure -> handleActionFailure(result.error, keepContent = true)
            }
        }
    }

    fun removePolicy(policyId: String) {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        if (state.actionBusy || state.connection == ApplicationDataConnectionState.OFFLINE) return
        _uiState.value = state.copy(actionBusy = true)
        viewModelScope.launch {
            when (val result = repository.removePolicy(state.deviceId, policyId)) {
                is OperationResult.Success -> {
                    val current = _uiState.value as? ApplicationManagementUiState.Content ?: return@launch
                    _uiState.value = current.copy(
                        policyState = result.value,
                        actionBusy = false,
                        message = "Desired policy assignment removed. Enforcement is reported separately.",
                    )
                }
                is OperationResult.Failure -> handleActionFailure(result.error, keepContent = true)
            }
        }
    }

    fun syncPolicy() {
        val state = _uiState.value as? ApplicationManagementUiState.Content ?: return
        if (state.actionBusy || state.connection == ApplicationDataConnectionState.OFFLINE) return
        _uiState.value = state.copy(actionBusy = true)
        viewModelScope.launch {
            when (val result = repository.syncPolicy(state.deviceId)) {
                is OperationResult.Success -> {
                    val current = _uiState.value as? ApplicationManagementUiState.Content ?: return@launch
                    _uiState.value = current.copy(
                        policyState = current.policyState?.copy(
                            synchronization = result.value ?: current.policyState.synchronization,
                        ),
                        actionBusy = false,
                        message = "Policy synchronization requested. Command delivery does not imply enforcement success.",
                    )
                }
                is OperationResult.Failure -> handleActionFailure(result.error, keepContent = true)
            }
        }
    }

    private fun handleRefreshFailure(error: AdminError, previous: ApplicationManagementUiState.Content, requestGeneration: Int) {
        if (requestGeneration != generation.get()) return
        when (error) {
            AdminError.SessionExpired, AdminError.SessionRevoked -> {
                onSessionExpired()
                _uiState.value = ApplicationManagementUiState.Error(messageFor(error), retryable = false)
            }
            is AdminError.DeviceRevoked -> {
                _uiState.value = previous.copy(
                    loading = false,
                    actionBusy = false,
                    connection = ApplicationDataConnectionState.UNKNOWN,
                    message = "This device is revoked and is no longer manageable.",
                )
            }
            else -> if (previous.inventory != null || previous.policyState != null || previous.policies.isNotEmpty()) {
                _uiState.value = previous.copy(
                    loading = false,
                    connection = ApplicationDataConnectionState.OFFLINE,
                    message = "Live refresh failed. Showing the last in-memory state; actions require a live connection.",
                )
            } else {
                _uiState.value = ApplicationManagementUiState.Error(messageFor(error))
            }
        }
    }

    private fun handleActionFailure(error: AdminError, keepContent: Boolean) {
        when (error) {
            AdminError.SessionExpired, AdminError.SessionRevoked -> {
                onSessionExpired()
                if (!keepContent) _uiState.value = ApplicationManagementUiState.Error(messageFor(error), retryable = false)
                else (_uiState.value as? ApplicationManagementUiState.Content)?.let {
                    _uiState.value = it.copy(actionBusy = false, message = messageFor(error))
                }
            }
            is AdminError.DeviceRevoked -> {
                (_uiState.value as? ApplicationManagementUiState.Content)?.let {
                    _uiState.value = it.copy(actionBusy = false, connection = ApplicationDataConnectionState.UNKNOWN, message = messageFor(error))
                }
            }
            else -> (_uiState.value as? ApplicationManagementUiState.Content)?.let {
                _uiState.value = it.copy(
                    actionBusy = false,
                    inventoryLoadingMore = false,
                    policyLoadingMore = false,
                    connection = if (error == AdminError.Network || error == AdminError.Timeout) ApplicationDataConnectionState.OFFLINE else it.connection,
                    message = messageFor(error),
                )
            }
        }
    }

    private fun messageFor(error: AdminError): String = when (error) {
        AdminError.Authentication -> "Authentication is required."
        AdminError.Authorization -> "You are not authorized to manage this device."
        AdminError.RateLimited -> "Too many requests. Wait before trying again."
        AdminError.Network -> "Network unavailable. Last loaded data is not current."
        AdminError.Timeout -> "The backend timed out. Retry when the connection is available."
        AdminError.SessionExpired -> "Your Admin session expired. Sign in again."
        AdminError.SessionRevoked -> "Your Admin session was revoked. Sign in again."
        AdminError.Validation -> "The request was rejected as invalid."
        AdminError.ServerUnavailable -> "The backend is temporarily unavailable."
        AdminError.InvalidState -> "The server rejected this because the state changed. Refresh and retry."
        is AdminError.DeviceNotFound -> "The managed device could not be found or is no longer authorized."
        is AdminError.DeviceRevoked -> "The managed device is revoked and cannot be managed."
        else -> "The application-management request could not be completed."
    }
}

class ApplicationManagementViewModelFactory(
    private val repository: ApplicationManagementRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ApplicationManagementViewModel(repository, onSessionExpired) as T
}
