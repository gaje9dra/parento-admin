package com.parento.admin

import com.parento.admin.domain.AdminError
import com.parento.admin.domain.ManagedDevice
import com.parento.admin.domain.ConnectionState
import com.parento.admin.domain.DeviceStatus
import com.parento.admin.domain.EnrollmentState
import com.parento.admin.ui.AdminHomeViewModel
import com.parento.admin.ui.AdminUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminHomeViewModelTest {
    @Test
    fun defaultStateIsEmpty() {
        assertEquals(AdminUiState.Empty, AdminHomeViewModel().uiState.value)
    }

    @Test
    fun loadingStateIsRepresented() {
        val viewModel = AdminHomeViewModel()
        viewModel.showLoading()
        assertEquals(AdminUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun devicesProduceContentState() {
        val viewModel = AdminHomeViewModel()
        val device = ManagedDevice(
            deviceId = "test-device",
            displayName = "Test Device",
            connectionState = ConnectionState.CONNECTED,
            enrollmentState = EnrollmentState.ENROLLED,
            status = DeviceStatus.CONNECTED,
        )
        viewModel.showDevices(listOf(device))
        assertEquals(1, (viewModel.uiState.value as AdminUiState.Content).managedDevices.size)
    }

    @Test
    fun emptyDeviceListProducesEmptyState() {
        val viewModel = AdminHomeViewModel()
        viewModel.showDevices(emptyList())
        assertEquals(AdminUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun errorsExposeSafeUserMessage() {
        val viewModel = AdminHomeViewModel()
        viewModel.showError(AdminError.Backend)
        assertEquals(
            "The management service is temporarily unavailable.",
            (viewModel.uiState.value as AdminUiState.Error).message,
        )
    }
}
