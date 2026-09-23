package com.parento.admin.data

import com.parento.admin.domain.ManagedDevice
import com.parento.admin.domain.OperationResult

/**
 * Future local persistence boundary for cached application state.
 *
 * Phase 1.2 deliberately does not persist credentials or implement a database.
 */
interface LocalDataStore {
    fun readManagedDevices(): OperationResult<List<ManagedDevice>>
    fun saveManagedDevices(devices: List<ManagedDevice>): OperationResult<Unit>
}
