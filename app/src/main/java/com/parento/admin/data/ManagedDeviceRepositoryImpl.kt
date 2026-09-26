package com.parento.admin.data

import com.parento.admin.communication.AdminBackendApiClient
import com.parento.admin.device.AdminCommand
import com.parento.admin.device.EnrollmentDeviceReference
import com.parento.admin.device.ManagedDeviceRepository
import com.parento.admin.device.ManagedDeviceStatus
import com.parento.admin.domain.OperationResult

class ManagedDeviceRepositoryImpl(
    private val api: AdminBackendApiClient,
) : ManagedDeviceRepository {
    override suspend fun listDevices(): OperationResult<List<EnrollmentDeviceReference>> = api.listEnrollmentDevices()
    override suspend fun getDeviceStatus(deviceId: String): OperationResult<ManagedDeviceStatus> = api.getDeviceStatus(deviceId)
    override suspend fun createFutureCommand(deviceId: String, idempotencyKey: String): OperationResult<AdminCommand> =
        api.createFutureCommand(deviceId, idempotencyKey)
    override suspend fun getCommand(deviceId: String, commandId: String): OperationResult<AdminCommand> =
        api.getCommand(deviceId, commandId)
    override suspend fun cancelCommand(deviceId: String, commandId: String): OperationResult<AdminCommand> =
        api.cancelCommand(deviceId, commandId)
}
