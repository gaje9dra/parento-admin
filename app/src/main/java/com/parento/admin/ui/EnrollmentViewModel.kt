package com.parento.admin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.EnrollmentRepository
import com.parento.admin.domain.EnrollmentSession
import com.parento.admin.domain.EnrollmentSessionStatus
import com.parento.admin.domain.OperationResult
import java.time.Instant
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
                    val baselineEnrollmentIds = when (val baseline = repository.list()) {
                        is OperationResult.Success -> baseline.value.mapTo(mutableSetOf()) { it.id }
                        is OperationResult.Failure -> null
                    }
                    when (val result = repository.create()) {
                        is OperationResult.Success -> {
                            persistEnrollmentId(result.value.enrollment.id)
                            _uiState.value = stateFor(
                                result.value.enrollment,
                                result.value.authorizationSecret,
                            )
                        }
                        is OperationResult.Failure -> {
                            if (result.error.isCreateOutcomeAmbiguous()) {
                                reconcileCreateFailure(baselineEnrollmentIds)
                            } else {
                                showFailure(result.error)
                            }
                        }
                    }
                }
            } finally {
                operationInFlight = false
            }
        }
    }

    /**
     * Reconciles a create request whose outcome was not known to the client.
     * The backend's authenticated list endpoint is used instead of issuing a
     * second blind create request.
     */
    fun reconcileCreateFailure() {
        if (operationInFlight) return
        operationInFlight = true
        viewModelScope.launch {
            try {
                operationMutex.withLock {
                    _uiState.value = EnrollmentUiState.Creating
                    reconcileCreateFailure(null)
                }
            } finally {
                operationInFlight = false
            }
        }
    }

    fun refresh() {
        if (operationInFlight) return
        val enrollmentId = when (val state = _uiState.value) {
            is EnrollmentUiState.Restoring -> state.enrollmentId
            else -> currentEnrollment()?.id
        } ?: return

        operationInFlight = true
        viewModelScope.launch {
            try {
                operationMutex.withLock {
                    when (val result = repository.get(enrollmentId)) {
                        is OperationResult.Success -> {
                            _uiState.value = stateFor(
                                result.value,
                                secretForState(),
                            )
                        }
                        is OperationResult.Failure -> {
                            if (result.error == AdminError.SessionExpired) {
                                stopPolling()
                                onSessionExpired()
                            }
                            if (result.error.stopsPolling()) {
                                stopPolling()
                                clearPersistedEnrollment()
                            }
                            _uiState.value = EnrollmentUiState.Error(
                                message = messageFor(result.error),
                                enrollment = if (result.error.stopsPolling()) null else currentEnrollment(),
                                canRetry = result.error.retryable(),
                            )
                        }
                    }
                }
            } finally {
                operationInFlight = false
            }
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
                            if (result.error == AdminError.SessionExpired) {
                                stopPolling()
                                onSessionExpired()
                            }
                            if (result.error.stopsPolling()) stopPolling()
                            _uiState.value = EnrollmentUiState.Error(
                                message = messageFor(result.error),
                                enrollment = enrollment,
                                canRetry = result.error.retryable(),
                            )
                        }
                    }
                }
            } finally {
                operationInFlight = false
            }
        }
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        if (currentEnrollment()?.status?.isTerminal == true) return
        if (_uiState.value !is EnrollmentUiState.Active) return

        pollingJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (_uiState.value !is EnrollmentUiState.Active) break
                refresh()
                while (operationInFlight && _uiState.value is EnrollmentUiState.Active) {
                    delay(POLL_OPERATION_CHECK_MS)
                }
                if (_uiState.value !is EnrollmentUiState.Active) break
            }
            pollingJob = null
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun reconcileCreateFailure(baselineEnrollmentIds: Set<String>?) {
        when (val result = repository.list()) {
            is OperationResult.Success -> {
                val candidates = result.value
                    .filter { it.status.isActiveForAdmin() }
                    .filter { baselineEnrollmentIds == null || it.id !in baselineEnrollmentIds }
                    .sortedByDescending { it.createdAt }

                val enrollment = candidates.firstOrNull()
                if (enrollment != null) {
                    persistEnrollmentId(enrollment.id)
                    _uiState.value = EnrollmentUiState.Error(
                        message = "An enrollment was created, but its original response was lost. Review the enrollment below before continuing.",
                        enrollment = enrollment,
                        canRetry = false,
                    )
                } else {
                    _uiState.value = EnrollmentUiState.Error(
                        message = "The enrollment request outcome is unknown. Check enrollment status before creating another enrollment.",
                        canRetry = false,
                    )
                }
            }
            is OperationResult.Failure -> {
                if (result.error == AdminError.SessionExpired) {
                    stopPolling()
                    onSessionExpired()
                }
                _uiState.value = EnrollmentUiState.Error(
                    message = "The enrollment request outcome is unknown. Check enrollment status before creating another enrollment.",
                    canRetry = false,
                )
            }
        }
    }

    private fun currentEnrollment(): EnrollmentSession? = when (val state = _uiState.value) {
        is EnrollmentUiState.Active -> state.enrollment
        is EnrollmentUiState.Completed -> state.enrollment
        is EnrollmentUiState.Terminal -> state.enrollment
        is EnrollmentUiState.Error -> state.enrollment
        is EnrollmentUiState.Restoring -> null
        else -> null
    }

    private fun secretForState(): String? =
        (_uiState.value as? EnrollmentUiState.Active)?.authorizationSecret

    private fun stateFor(
        enrollment: EnrollmentSession,
        secret: String?,
    ): EnrollmentUiState = when {
        enrollment.status == EnrollmentSessionStatus.COMPLETED -> {
            stopPolling()
            clearPersistedEnrollment()
            EnrollmentUiState.Completed(enrollment)
        }
        enrollment.status.isTerminal -> {
            stopPolling()
            clearPersistedEnrollment()
            EnrollmentUiState.Terminal(enrollment)
        }
        else -> EnrollmentUiState.Active(enrollment, secret)
    }

    private fun restoreState(): EnrollmentUiState {
        val id = savedStateHandle.get<String>(ENROLLMENT_ID_KEY)
        return if (id.isNullOrBlank()) {
            EnrollmentUiState.Ready
        } else {
            EnrollmentUiState.Restoring(id)
        }
    }

    private fun showFailure(error: AdminError) {
        if (error == AdminError.SessionExpired) {
            stopPolling()
            onSessionExpired()
        }
        _uiState.value = EnrollmentUiState.Error(
            message = messageFor(error),
            enrollment = currentEnrollment(),
            canRetry = error.retryable(),
        )
    }

    private fun persistEnrollmentId(id: String) {
        savedStateHandle[ENROLLMENT_ID_KEY] = id
    }

    private fun clearPersistedEnrollment() {
        savedStateHandle[ENROLLMENT_ID_KEY] = null
    }

    private fun messageFor(error: AdminError): String = when (error) {
        AdminError.SessionExpired, AdminError.SessionRevoked ->
            "Your administrator session has expired. Please sign in again."
        AdminError.Authorization ->
            "This enrollment is not available to the current administrator."
        AdminError.Network ->
            "A network connection is unavailable."
        AdminError.Timeout ->
            "The enrollment request timed out."
        AdminError.ServerUnavailable ->
            "The Parento server is temporarily unavailable."
        AdminError.EnrollmentNotFound ->
            "The enrollment session could not be found."
        AdminError.EnrollmentExpired ->
            "This enrollment has expired. Create a new enrollment to continue."
        AdminError.EnrollmentStateConflict,
        AdminError.EnrollmentAlreadyConsumed ->
            "This enrollment is no longer available for this operation."
        AdminError.EnrollmentRateLimited ->
            "Too many enrollment requests. Please wait and try again."
        AdminError.Validation ->
            "The enrollment request was invalid."
        else ->
            "The enrollment operation could not be completed."
    }

    private fun AdminError.retryable(): Boolean =
        this == AdminError.Network ||
            this == AdminError.Timeout ||
            this == AdminError.ServerUnavailable

    private fun AdminError.isCreateOutcomeAmbiguous(): Boolean =
        this == AdminError.Network ||
            this == AdminError.Timeout ||
            this == AdminError.ServerUnavailable

    private fun AdminError.stopsPolling(): Boolean =
        this == AdminError.Authorization ||
            this == AdminError.EnrollmentNotFound ||
            this == AdminError.EnrollmentExpired ||
            this == AdminError.EnrollmentStateConflict ||
            this == AdminError.EnrollmentAlreadyConsumed ||
            this == AdminError.EnrollmentRateLimited

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    companion object {
        private const val ENROLLMENT_ID_KEY = "active_enrollment_id"
        private const val POLL_INTERVAL_MS = 5_000L
        private const val POLL_OPERATION_CHECK_MS = 50L
    }
}
