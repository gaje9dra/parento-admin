package com.parento.admin.domain

import java.time.Instant

enum class EnrollmentSessionStatus {
    CREATED, PENDING, VERIFIED, COMPLETED, EXPIRED, CANCELLED, REVOKED, FAILED;
    val isTerminal: Boolean
        get() = this == COMPLETED || this == EXPIRED || this == CANCELLED || this == REVOKED || this == FAILED
}

data class EnrollmentSession(
    val id: String,
    val status: EnrollmentSessionStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant,
    val verifiedAt: Instant?,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val managedDeviceId: String?,
    val verificationAttempts: Int,
)

data class EnrollmentCreation(
    val enrollment: EnrollmentSession,
    val authorizationSecret: String,
)
