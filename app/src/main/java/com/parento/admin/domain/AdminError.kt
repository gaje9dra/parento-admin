package com.parento.admin.domain

sealed interface AdminError {
    data object Authentication : AdminError
    data object Authorization : AdminError
    data object Network : AdminError
    data class DeviceNotFound(val deviceId: String) : AdminError
    data object Policy : AdminError
    data object Backend : AdminError
    data object LocalStorage : AdminError
    data object Unknown : AdminError
}
