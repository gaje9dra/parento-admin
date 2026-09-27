package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import com.parento.admin.policy.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NetworkPolicyListUiState {
    data object Loading : NetworkPolicyListUiState
    data object Empty : NetworkPolicyListUiState
    data class Content(val policies: List<NetworkPolicy>, val nextCursor: String?, val stale: Boolean = false) : NetworkPolicyListUiState
    data class Error(val message: String, val canRetry: Boolean = true) : NetworkPolicyListUiState
}

sealed interface NetworkPolicyDetailUiState {
    data object Idle : NetworkPolicyDetailUiState
    data object Loading : NetworkPolicyDetailUiState
    data object Saving : NetworkPolicyDetailUiState
    data class Content(
        val policy: NetworkPolicy,
        val deviceState: NetworkPolicyDeviceState? = null,
        val selectedDeviceId: String? = null,
        val stale: Boolean = false,
        val message: String? = null,
    ) : NetworkPolicyDetailUiState
    data class Error(val message: String, val canRetry: Boolean = true) : NetworkPolicyDetailUiState
}

class NetworkPolicyViewModel(
    private val repository: NetworkPolicyRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    private val _list = MutableStateFlow<NetworkPolicyListUiState>(NetworkPolicyListUiState.Loading)
    val list: StateFlow<NetworkPolicyListUiState> = _list.asStateFlow()
    private val _detail = MutableStateFlow<NetworkPolicyDetailUiState>(NetworkPolicyDetailUiState.Idle)
    val detail: StateFlow<NetworkPolicyDetailUiState> = _detail.asStateFlow()

    fun loadPolicies(refresh: Boolean = false) {
        if (!refresh && _list.value is NetworkPolicyListUiState.Content) return
        _list.value = NetworkPolicyListUiState.Loading
        viewModelScope.launch {
            when (val r = repository.listPolicies()) {
                is OperationResult.Success -> _list.value = if (r.value.first.isEmpty()) NetworkPolicyListUiState.Empty else NetworkPolicyListUiState.Content(r.value.first, r.value.second)
                is OperationResult.Failure -> {
                    if (r.error is AdminError.SessionExpired) onSessionExpired()
                    _list.value = NetworkPolicyListUiState.Error(messageFor(r.error))
                }
            }
        }
    }

    fun open(policyId: String) {
        _detail.value = NetworkPolicyDetailUiState.Loading
        viewModelScope.launch {
            when (val r = repository.getPolicy(policyId)) {
                is OperationResult.Success -> _detail.value = NetworkPolicyDetailUiState.Content(r.value)
                is OperationResult.Failure -> handleFailure(r.error)
            }
        }
    }

    fun create(name: String, description: String?, rules: List<NetworkPolicyRule>) {
        val normalized = NetworkPolicyValidator.normalizeRules(rules) ?: run {
            _detail.value = NetworkPolicyDetailUiState.Error("One or more policy rules are invalid or duplicated.")
            return
        }
        _detail.value = NetworkPolicyDetailUiState.Saving
        viewModelScope.launch {
            when (val r = repository.createPolicy(name, description, normalized)) {
                is OperationResult.Success -> {
                    _detail.value = NetworkPolicyDetailUiState.Content(r.value)
                    loadPolicies(refresh = true)
                }
                is OperationResult.Failure -> handleFailure(r.error)
            }
        }
    }

    fun update(policy: NetworkPolicy) {
        val normalized = NetworkPolicyValidator.normalizeRules(policy.rules) ?: run {
            _detail.value = NetworkPolicyDetailUiState.Error("One or more policy rules are invalid or duplicated.")
            return
        }
        _detail.value = NetworkPolicyDetailUiState.Saving
        viewModelScope.launch {
            when (val r = repository.updatePolicy(policy.copy(rules = normalized))) {
                is OperationResult.Success -> {
                    val current = _detail.value as? NetworkPolicyDetailUiState.Content
                    _detail.value = NetworkPolicyDetailUiState.Content(r.value, current?.deviceState, current?.selectedDeviceId)
                    loadPolicies(refresh = true)
                }
                is OperationResult.Failure -> handleFailure(r.error)
            }
        }
    }

    fun loadDeviceState(deviceId: String) {
        val current = _detail.value as? NetworkPolicyDetailUiState.Content ?: return
        viewModelScope.launch {
            when (val r = repository.getDeviceState(deviceId)) {
                is OperationResult.Success -> _detail.value = current.copy(deviceState = r.value, selectedDeviceId = deviceId)
                is OperationResult.Failure -> handleFailure(r.error)
            }
        }
    }

    fun assign(deviceId: String) {
        val policy = (_detail.value as? NetworkPolicyDetailUiState.Content)?.policy ?: return
        viewModelScope.launch {
            when (val r = repository.assignPolicy(deviceId, policy.id)) {
                is OperationResult.Success -> _detail.value = (_detail.value as NetworkPolicyDetailUiState.Content).copy(deviceState = r.value, selectedDeviceId = deviceId, message = "Assignment requested. Command and enforcement state remain separate.")
                is OperationResult.Failure -> handleFailure(r.error)
            }
        }
    }

    fun remove(deviceId: String) {
        val policy = (_detail.value as? NetworkPolicyDetailUiState.Content)?.policy ?: return
        viewModelScope.launch {
            when (val r = repository.removePolicy(deviceId, policy.id)) {
                is OperationResult.Success -> _detail.value = (_detail.value as NetworkPolicyDetailUiState.Content).copy(deviceState = r.value, selectedDeviceId = deviceId, message = "Assignment removed. Enforcement is confirmed separately.")
                is OperationResult.Failure -> handleFailure(r.error)
            }
        }
    }

    fun sync(deviceId: String) = action(deviceId) { repository.requestSync(it) }
    fun requestStatus(deviceId: String) = action(deviceId) { repository.requestStatus(it) }

    private fun action(deviceId: String, block: suspend (String) -> OperationResult<NetworkPolicyDeviceState>) {
        viewModelScope.launch {
            when (val r = block(deviceId)) {
                is OperationResult.Success -> {
                    val c = _detail.value as? NetworkPolicyDetailUiState.Content
                    if (c != null) _detail.value = c.copy(deviceState = r.value, selectedDeviceId = deviceId, message = "Command requested. Enforcement status will update separately.")
                }
                is OperationResult.Failure -> handleFailure(r.error)
            }
        }
    }

    private fun handleFailure(error: AdminError) {
        if (error is AdminError.SessionExpired) onSessionExpired()
        _detail.value = NetworkPolicyDetailUiState.Error(messageFor(error))
    }

    private fun messageFor(error: AdminError) = when (error) {
        AdminError.Authorization -> "You are not authorized to access this policy or device."
        AdminError.Network -> "Network unavailable. Cached state, when available, may be stale."
        AdminError.Timeout -> "The request timed out. Retry when connectivity is restored."
        AdminError.Validation -> "The request was rejected as invalid."
        AdminError.ServerUnavailable -> "Parento server is temporarily unavailable."
        AdminError.RateLimited -> "Too many requests. Please wait and retry."
        else -> "Network policy data is currently unavailable."
    }
}
