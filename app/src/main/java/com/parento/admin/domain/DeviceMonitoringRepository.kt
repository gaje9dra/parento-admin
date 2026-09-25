package com.parento.admin.domain

interface DeviceMonitoringRepository {
    suspend fun listDevices(): OperationResult<List<ManagedDeviceMonitoring>>
    suspend fun getDevice(deviceId: String): OperationResult<ManagedDeviceMonitoring>
    suspend fun refresh(): OperationResult<List<ManagedDeviceMonitoring>>
}
