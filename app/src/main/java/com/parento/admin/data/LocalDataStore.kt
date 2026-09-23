package com.parento.admin.data

import com.parento.admin.domain.ManagedDevice
import com.parento.admin.domain.OperationResult

/**
 * Legacy Phase 1 contract for future managed-device caching.
 *
 * Phase 2.1 does not persist managed-device business records. The concrete
 * Room persistence foundation is provided by LocalStateRepository.
 */
interface LocalDataStore {
    fun readManagedDevices(): OperationResult<List<ManagedDevice>>
    fun saveManagedDevices(devices: List<ManagedDevice>): OperationResult<Unit>
}
