package com.parento.admin.communication

import com.parento.admin.domain.ManagedDevice
import com.parento.admin.domain.OperationResult
import com.parento.admin.domain.Policy

/**
 * Admin-to-backend communication boundary.
 *
 * This interface defines future contracts without making network requests.
 */
interface BackendClient {
    fun listDevices(): OperationResult<List<ManagedDevice>>
    fun getDevice(deviceId: String): OperationResult<ManagedDevice>
    fun managePolicy(policy: Policy): OperationResult<Policy>
    fun sendEvent(event: AdminEvent): OperationResult<Unit>
}

data class AdminEvent(
    val eventType: String
)
