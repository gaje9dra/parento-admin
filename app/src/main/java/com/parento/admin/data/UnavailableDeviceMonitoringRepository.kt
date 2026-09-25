package com.parento.admin.data

import com.parento.admin.domain.AdminError
import com.parento.admin.domain.DeviceMonitoringRepository
import com.parento.admin.domain.ManagedDeviceMonitoring
import com.parento.admin.domain.OperationResult

/**
 * Explicit dependency state used until the backend exposes the authenticated
 * administrator device-list/detail monitoring contract.
 *
 * It prevents the Admin UI from inventing device data or silently using the
 * Managed app as a data source.
 */
class UnavailableDeviceMonitoringRepository : DeviceMonitoringRepository {
    override suspend fun listDevices(): OperationResult<List<ManagedDeviceMonitoring>> =
        OperationResult.Failure(AdminError.Backend)

    override suspend fun getDevice(deviceId: String): OperationResult<ManagedDeviceMonitoring> =
        OperationResult.Failure(AdminError.Backend)

    override suspend fun refresh(): OperationResult<List<ManagedDeviceMonitoring>> =
        OperationResult.Failure(AdminError.Backend)
}
