package com.parento.admin.audio

enum class AudioAccessSessionStatus {
    REQUESTED, AUTHORIZED, STARTING, ACTIVE, STOPPING, STOPPED, EXPIRED, FAILED, REJECTED;
    val isTerminal: Boolean get() = this in setOf(STOPPED, EXPIRED, FAILED, REJECTED)
}

enum class AudioPlaybackState {
    IDLE, LOADING, CONNECTING, PLAYING, STOPPING, ERROR
}

data class AudioAccessSession(
    val sessionId: String,
    val managedDeviceId: String,
    val status: AudioAccessSessionStatus,
    val createdAt: String,
    val authorizedAt: String?,
    val startedAt: String?,
    val stoppedAt: String?,
    val expiresAt: String,
    val lastActivityAt: String,
    val terminationReason: String?,
    val correlationId: String,
    val transportState: Map<String, String> = emptyMap(),
)

data class AudioAvailability(
    val available: Boolean,
    val reason: String? = null,
)

fun AudioAccessSession.isPlaybackEligible(): Boolean =
    status == AudioAccessSessionStatus.ACTIVE &&
        transportState["state"]?.uppercase() == "ACTIVE"
