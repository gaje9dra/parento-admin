package com.parento.admin.device

import com.parento.admin.domain.ManagedDevice
import com.parento.admin.domain.OperationResult

/**
 * Service boundary for future managed-device operations.
 *
 * No pairing, enrollment, removal, remote commands, or fake device records are
 * implemented in Phase 1.2.
 */
interface DeviceManager {
    fun listDevices(): OperationResult<List<ManagedDevice>>
    fun getDevice(deviceId: String): OperationResult<ManagedDevice>
}
