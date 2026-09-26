package com.parento.admin.location

import com.parento.admin.domain.DeviceLocation
import com.parento.admin.domain.OperationResult

class DeviceLocationUseCase(
    private val repository: DeviceLocationRepository,
) {
    suspend operator fun invoke(deviceId: String): OperationResult<DeviceLocation?> =
        repository.getLatest(deviceId.trim())
}