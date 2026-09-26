package com.parento.admin.location

import com.parento.admin.domain.DeviceLocation
import com.parento.admin.domain.OperationResult

interface DeviceLocationRepository {
    suspend fun getLatest(deviceId: String): OperationResult<DeviceLocation?>
}