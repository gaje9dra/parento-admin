package com.parento.admin.screensharing

enum class ScreenSharingSessionStatus {
    REQUESTED,
    AUTHORIZED,
    STARTING,
    ACTIVE,
    STOPPING,
    STOPPED,
    EXPIRED,
    FAILED,
    REJECTED;

    val isTerminal: Boolean
        get() = this in setOf(STOPPED, EXPIRED, FAILED, REJECTED)
}

enum class ScreenTransportState {
    UNAVAILABLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

data class ScreenSharingSession(
    val sessionId: String,
    val managedDeviceId: String,
    val status: ScreenSharingSessionStatus,
    val createdAt: String,
    val authorizedAt: String?,
    val startedAt: String?,
    val expiresAt: String,
    val stoppedAt: String?,
    val lastActivityAt: String,
    val terminationReason: String?,
    val correlationId: String,
    val transportState: ScreenTransportState,
    val transportStateDetails: Map<String, String> = emptyMap(),
)

fun ScreenSharingSession.isDisplayAuthorized(): Boolean =
    status == ScreenSharingSessionStatus.ACTIVE &&
        transportState != ScreenTransportState.ERROR
