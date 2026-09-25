package com.parento.admin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.EnrollmentRepository
import com.parento.admin.domain.EnrollmentSession
import com.parento.admin.domain.EnrollmentSessionStatus
import com.parento.admin.domain.OperationResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EnrollmentViewModel(
    private val repository: EnrollmentRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val onSessionExpired: () -> Unit = {},
) : ViewModel() {
    private val operationMutex = Mutex()
    private val _uiState = MutableStateFlow<EnrollmentUiState>(restoreState())
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()
    private var operationInFlight = false
    private var pollingJob: Job? = null

    fun createEnrollment() {
        if (operationInFlight) return
        operationInFlight = true
        viewModelScope.launch {
            try {
                operationMutex.withLock {
                    _uiState.value = EnrollmentUiState.Creating
                    when (val result = repository.create()) {
                        is OperationResult.Success -> {
                            persistEnrollmentId(result.value.enrollment.id)
                            _uiState.value = EnrollmentUiState.Active(result.value.enrollment, result.value.authorizationSecret)
                        }
                        is OperationResult.Failure -> showFailure(result.error)
                    }
                }
            } finally { operationInFlight = false }
        }
    }

    fun refresh() {
        if (operationInFlight) return
        val enrollment = currentEnrollment() ?: return
        operationInFlight = true
        viewModelScope.launch {
            try {
                operationMutex.withLock {
                    when (val result = repository.get(enrollment.id)) {
                        is OperationResult.Success -> _uiState.value = stateFor(result.value, secretForState())
                        is OperationResult.Failure -> {
                            if (result.error == AdminError.SessionExpired) onSessionExpired()
                            _uiState.value = EnrollmentUiState.Error(messageFor(result.error), enrollment, result.error.retryable())
                        }
                    }
                }
            } finally { operationInFlight = false }
        }
    }

    fun cancelEnrollment() {
        if (operationInFlight) return
        val enrollment = currentEnrollment() ?: return
        if (enrollment.status.isTerminal) return
        operationInFlight = true
        viewModelScope.launch {
            try {
                operationMutex.withLock {
                    when (val result = repository.cancel(enrollment.id)) {
                        is OperationResult.Success -> {
                            stopPolling()
                            _uiState.value = stateFor(result.value, null)
                        }
                        is OperationResult.Failure -> {
                            if (result.error == AdminError.SessionExpired) onSessionExpired()
                            _uiState.value = EnrollmentUiState.Error(messageFor(result.error), enrollment, result.error.retryable())
                        }
                    }
                }
            } finally { operationInFlight = false }
        }
    }

    fun startPolling() {
        if (pollingJob?.isActive == true || currentEnrollment()?.status?.isTerminal == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (currentEnrollment()?.status?.isTerminal == true) break
                refresh()
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun currentEnrollment(): EnrollmentSession? = when (val state = _uiState.value) {
        is EnrollmentUiState.Active -> state.enrollment
        is EnrollmentUiState.Completed -> state.enrollment
        is EnrollmentUiState.Terminal -> state.enrollment
        is EnrollmentUiState.Error -> state.enrollment
        else -> null
    }

    private fun secretForState(): String? = (_uiState.value as? EnrollmentUiState.Active)?.authorizationSecret

    private fun stateFor(enrollment: EnrollmentSession, secret: String?): EnrollmentUiState = when {
        enrollment.status == EnrollmentSessionStatus.COMPLETED -> {
            stopPolling(); clearPersistedEnrollment(); EnrollmentUiState.Completed(enrollment)
        }
        enrollment.status.isTerminal -> {
            stopPolling(); clearPersistedEnrollment(); EnrollmentUiState.Terminal(enrollment)
        }
        else -> EnrollmentUiState.Active(enrollment, secret)
    }

    private fun restoreState(): EnrollmentUiState {
        val id = savedStateHandle.get<String>(ENROLLMENT_ID_KEY) ?: return EnrollmentUiState.Ready
        return EnrollmentUiState.Error(
            message = "Restoring enrollment status…",
            enrollment = EnrollmentSession(id, EnrollmentSessionStatus.PENDING, java.time.Instant.EPOCH, java.time.Instant.EPOCH, java.time.Instant.EPOCH, null, null, null, null, 0),
            canRetry = true,
        )
    }

    private fun showFailure(error: AdminError) {
        if (error == AdminError.SessionExpired) onSessionExpired()
        _uiState.value = EnrollmentUiState.Error(messageFor(error), currentEnrollment(), error.retryable())
    }

    private fun persistEnrollmentId(id: String) { savedStateHandle[ENROLLMENT_ID_KEY] = id }
    private fun clearPersistedEnrollment() { savedStateHandle[ENROLLMENT_ID_KEY] = null }

    private fun messageFor(error: AdminError): String = when (error) {
        AdminError.SessionExpired, AdminError.SessionRevoked -> "Your administrator session has expired. Please sign in again."
        AdminError.Authorization -> "This enrollment is not available to the current administrator."
        AdminError.Network -> "A network connection is unavailable."
        AdminError.Timeout -> "The enrollment request timed out."
        AdminError.ServerUnavailable -> "The Parento server is temporarily unavailable."
        AdminError.EnrollmentNotFound -> "The enrollment session could not be found."
        AdminError.EnrollmentExpired -> "This enrollment has expired. Create a new enrollment to continue."
        AdminError.EnrollmentStateConflict, AdminError.EnrollmentAlreadyConsumed -> "This enrollment is no longer in a cancellable state."
        AdminError.EnrollmentRateLimited -> "Too many enrollment requests. Please wait and try again."
        AdminError.Validation -> "The enrollment request was invalid."
        else -> "The enrollment operation could not be completed."
    }

    private fun AdminError.retryable(): Boolean =
        this == AdminError.Network || this == AdminError.Timeout || this == AdminError.ServerUnavailable

    override fun onCleared() { stopPolling(); super.onCleared() }

    companion object {
        private const val ENROLLMENT_ID_KEY = "active_enrollment_id"
        private const val POLL_INTERVAL_MS = 5_000L
    }
}