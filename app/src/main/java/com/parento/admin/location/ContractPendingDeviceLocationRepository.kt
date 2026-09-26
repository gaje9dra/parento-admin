package com.parento.admin.location

import com.parento.admin.domain.AdminError
import com.parento.admin.domain.DeviceLocation
import com.parento.admin.domain.OperationResult

/**
 * Safe adapter used until the Phase 8.1 backend location-read contract is present.
 *
 * This deliberately does not invent an endpoint or fabricate location data.
 * Replace this adapter with the authenticated backend implementation once the
 * backend exposes the documented Phase 8.1 read contract.
 */
class ContractPendingDeviceLocationRepository : DeviceLocationRepository {
    override suspend fun getLatest(deviceId: String): OperationResult<DeviceLocation?> {
        require(deviceId.isNotBlank()) { "Device id must not be blank." }
        return OperationResult.Failure(AdminError.Backend)
    }
}