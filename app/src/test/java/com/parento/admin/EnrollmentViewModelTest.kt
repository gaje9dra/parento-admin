package com.parento.admin

import androidx.lifecycle.SavedStateHandle
import com.parento.admin.domain.EnrollmentCreation
import com.parento.admin.domain.EnrollmentRepository
import com.parento.admin.domain.EnrollmentSession
import com.parento.admin.domain.EnrollmentSessionStatus
import com.parento.admin.domain.OperationResult
import com.parento.admin.ui.EnrollmentUiState
import com.parento.admin.ui.EnrollmentViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnrollmentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeEnrollmentRepository

    @Before fun setUp() { Dispatchers.setMain(dispatcher); repository = FakeEnrollmentRepository() }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun createEnrollment_exposesSecretOnlyInActiveMemoryState() = runTest {
        val vm = EnrollmentViewModel(repository)
        vm.createEnrollment()
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value as EnrollmentUiState.Active
        assertEquals("secret-value", state.authorizationSecret)
    }

    @Test fun refreshCompleted_showsManagedDevice() = runTest {
        val vm = EnrollmentViewModel(repository)
        vm.createEnrollment()
        dispatcher.scheduler.advanceUntilIdle()
        repository.status = EnrollmentSessionStatus.COMPLETED
        repository.deviceId = "device-123"
        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("device-123", (vm.uiState.value as EnrollmentUiState.Completed).enrollment.managedDeviceId)
    }

    @Test fun restoreAfterProcessDeath_keepsOnlyEnrollmentIdAndRequiresServerRefresh() {
        val vm = EnrollmentViewModel(repository, SavedStateHandle(mapOf("active_enrollment_id" to "enrollment-1")))
        val state = vm.uiState.value as EnrollmentUiState.Error
        assertTrue(state.message.contains("Restoring"))
        assertEquals("enrollment-1", state.enrollment?.id)
    }

    private class FakeEnrollmentRepository : EnrollmentRepository {
        var status = EnrollmentSessionStatus.PENDING
        var deviceId: String? = null
        private val session: EnrollmentSession
            get() = EnrollmentSession(
                "enrollment-1", status, Instant.now(), Instant.now(), Instant.now().plusSeconds(900),
                null, if (status == EnrollmentSessionStatus.COMPLETED) Instant.now() else null,
                null, deviceId, 0,
            )
        override suspend fun create() = OperationResult.Success(EnrollmentCreation(session, "secret-value"))
        override suspend fun get(enrollmentId: String) = OperationResult.Success(session)
        override suspend fun list() = OperationResult.Success(listOf(session))
        override suspend fun cancel(enrollmentId: String) = OperationResult.Success(session.copy(status = EnrollmentSessionStatus.CANCELLED))
    }
}
