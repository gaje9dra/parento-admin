package com.parento.admin.screensharing

import com.parento.admin.domain.OperationResult

/**
 * Boundary for a future approved screen-frame transport.
 *
 * Phase 9.1 does not expose a media-frame transport contract, so this
 * implementation deliberately does not invent a streaming protocol or
 * fabricate frames.
 */
interface ScreenStreamClient {
    suspend fun connect(
        adminSessionBinding: String,
        managedDeviceId: String,
        screenSessionId: String,
    ): OperationResult<Unit>

    suspend fun disconnect()

    val state: ScreenTransportState

    /**
     * Returns the next approved frame/data payload when a real transport
     * contract exists. Null means no frame is currently available.
     */
    suspend fun receiveFrame(): OperationResult<ScreenFrame?> 
}

data class ScreenFrame(
    val payload: ByteArray,
    val width: Int,
    val height: Int,
    val timestampEpochMillis: Long,
)
