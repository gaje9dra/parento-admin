package com.parento.admin.ui

import com.parento.admin.domain.EnrollmentSession
import com.parento.admin.domain.EnrollmentSessionStatus

sealed interface EnrollmentUiState {
    data object Ready : EnrollmentUiState
    data object Creating : EnrollmentUiState
    data class Active(
        val enrollment: EnrollmentSession,
        val authorizationSecret: String?,
        val refreshing: Boolean = false,
    ) : EnrollmentUiState
    data class Completed(val enrollment: EnrollmentSession) : EnrollmentUiState
    data class Terminal(val enrollment: EnrollmentSession) : EnrollmentUiState
    data class Error(
        val message: String,
        val enrollment: EnrollmentSession? = null,
        val canRetry: Boolean = true,
    ) : EnrollmentUiState
}

fun EnrollmentSessionStatus.isActiveForAdmin(): Boolean =
    this == EnrollmentSessionStatus.CREATED || this == EnrollmentSessionStatus.PENDING || this == EnrollmentSessionStatus.VERIFIED
