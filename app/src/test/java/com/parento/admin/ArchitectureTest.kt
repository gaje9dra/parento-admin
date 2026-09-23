package com.parento.admin

import com.parento.admin.domain.AdminError
import com.parento.admin.domain.ConnectionState
import com.parento.admin.domain.DeviceStatus
import com.parento.admin.domain.EnrollmentState
import com.parento.admin.domain.ManagedDevice
import com.parento.admin.domain.OperationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureTest {
    @Test
    fun managedDevice_representsExpectedLifecycleState() {
        val device = ManagedDevice(
            deviceId = "device-1",
            displayName = "Test Device",
            connectionState = ConnectionState.CONNECTED,
            enrollmentState = EnrollmentState.ENROLLED,
            status = DeviceStatus.CONNECTED,
            batteryPercent = 80,
            lastSynchronizedAtEpochMillis = 1_000L
        )

        assertEquals("device-1", device.deviceId)
        assertEquals(DeviceStatus.CONNECTED, device.status)
        assertEquals(EnrollmentState.ENROLLED, device.enrollmentState)
        assertEquals(ConnectionState.CONNECTED, device.connectionState)
    }

    @Test
    fun operationResult_representsDomainFailure() {
        val result: OperationResult<ManagedDevice> =
            OperationResult.Failure(AdminError.Network)

        assertTrue(result is OperationResult.Failure)
        assertEquals(AdminError.Network, (result as OperationResult.Failure).error)
    }

    @Test
    fun navigationDestination_keepsDeviceContext() {
        val destination = com.parento.admin.presentation.NavigationDestination.DeviceDetails("device-42")

        assertEquals("device-42", destination.deviceId)
    }
}
