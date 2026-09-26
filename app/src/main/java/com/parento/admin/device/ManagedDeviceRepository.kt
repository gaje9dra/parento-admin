package com.parento.admin.device

import com.parento.admin.domain.OperationResult

interface ManagedDeviceRepository {
    suspend fun listDevices(): OperationResult<List<EnrollmentDeviceReference>>
    suspend fun getDeviceStatus(deviceId: String): OperationResult<ManagedDeviceStatus>
    suspend fun createFutureCommand(deviceId: String, idempotencyKey: String): OperationResult<AdminCommand>
    suspend fun getCommand(deviceId: String, commandId: String): OperationResult<AdminCommand>
    suspend fun cancelCommand(deviceId: String, commandId: String): OperationResult<AdminCommand>
}
