package com.parento.admin.domain

sealed interface AdminError {
    data object Authentication : AdminError
    data object Authorization : AdminError
    data object Network : AdminError
    data object Timeout : AdminError
    data object InvalidCredentials : AdminError
    data object AccountDisabled : AdminError
    data object SessionExpired : AdminError
    data object SessionRevoked : AdminError
    data object Validation : AdminError
    data object ServerUnavailable : AdminError
    data object AuthenticationRateLimited : AdminError
    data object UnknownAuthentication : AdminError
    data object EnrollmentNotFound : AdminError
    data object EnrollmentExpired : AdminError
    data object EnrollmentStateConflict : AdminError
    data object EnrollmentAlreadyConsumed : AdminError
    data object EnrollmentRateLimited : AdminError
    data class DeviceNotFound(val deviceId: String) : AdminError
    data object Policy : AdminError
    data object Backend : AdminError
    data object LocalStorage : AdminError
    data object InvalidState : AdminError
    data object Unknown : AdminError
}
