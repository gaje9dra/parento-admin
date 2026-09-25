package com.parento.admin

import androidx.lifecycle.SavedStateHandle
import com.parento.admin.domain.AdminError
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeEnrollmentRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createEnrollment_exposesSecretOnlyInActiveMemoryState() = runTest {
        val vm = EnrollmentViewModel(repository)
        vm.createEnrollment()
        advanceUntilIdle()

        val state = vm.uiState.value as EnrollmentUiState.Active
        assertEquals("secret-value", state.authorizationSecret)
        assertEquals("enrollment-1", state.enrollment.id)
    }

    
    @Test
    fun ambiguousCreate_reconcilesExistingEnrollment_insteadOfCreatingAgain() = runTest {
        repository.nextCreateResult =
            OperationResult.Failure(AdminError.Timeout)
        repository.listResult = listOf(repository.sessionFor(EnrollmentSessionStatus.PENDING))

        val vm = EnrollmentViewModel(repository)
        vm.createEnrollment()
        advanceUntilIdle()

        val state = vm.uiState.value as EnrollmentUiState.Error
        assertEquals("enrollment-1", state.enrollment?.id)
        assertTrue(!state.canRetry)
        assertEquals(1, repository.createCalls)

        vm.reconcileCreateFailure()
        advanceUntilIdle()

        assertEquals(1, repository.createCalls)
    }

    @Test
    fun polling_stopsWhenServerReportsTerminalState() = runTest {
        val vm = EnrollmentViewModel(repository)
        vm.createEnrollment()
        advanceUntilIdle()

        repository.status = EnrollmentSessionStatus.EXPIRED
        vm.startPolling()
        advanceTimeBy(5_000)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is EnrollmentUiState.Terminal)
    }

    @Test
    fun completedStatus_isServerAuthoritativeAndShowsManagedDevice() = runTest {
        val vm = EnrollmentViewModel(repository)
        vm.createEnrollment()
        advanceUntilIdle()

        repository.status = EnrollmentSessionStatus.COMPLETED
        repository.deviceId = "device-123"
        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value as EnrollmentUiState.Completed
        assertEquals("device-123", state.enrollment.managedDeviceId)
    }

    @Test
    fun processDeath_restoresOnlyEnrollmentId() {
        val vm = EnrollmentViewModel(
            repository,
            SavedStateHandle(mapOf("active_enrollment_id" to "enrollment-1")),
        )

        val state = vm.uiState.value as EnrollmentUiState.Restoring
        assertEquals("enrollment-1", state.enrollmentId)
    }

    @Test
    fun sessionExpiry_invokesAuthenticationBoundary() = runTest {
        var sessionExpired = false
        repository.nextGetResult = OperationResult.Failure(AdminError.SessionExpired)
        val vm = EnrollmentViewModel(
            repository,
            onSessionExpired = { sessionExpired = true },
        )

        vm.createEnrollment()
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        assertTrue(sessionExpired)
        assertTrue(vm.uiState.value is EnrollmentUiState.Error)
    }

    @Test
    fun cancellation_isTerminalAndStopsActiveEnrollment() = runTest {
        val vm = EnrollmentViewModel(repository)
        vm.createEnrollment()
        advanceUntilIdle()

        vm.cancelEnrollment()
        advanceUntilIdle()

        val state = vm.uiState.value as EnrollmentUiState.Terminal
        assertEquals(EnrollmentSessionStatus.CANCELLED, state.enrollment.status)
    }

    @Test
    fun invalidEnrollmentResponse_isNotMarkedCompleted() = runTest {
        repository.nextGetResult = OperationResult.Failure(AdminError.EnrollmentStateConflict)
        val vm = EnrollmentViewModel(repository)
        vm.createEnrollment()
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value as EnrollmentUiState.Error
        assertTrue(state.message.isNotBlank())
    }

    private class FakeEnrollmentRepository : EnrollmentRepository {
        var status = EnrollmentSessionStatus.PENDING
        var deviceId: String? = null
        var nextGetResult: OperationResult<EnrollmentSession>? = null
        var nextCreateResult: OperationResult<EnrollmentCreation>? = null
        var listResult: List<EnrollmentSession>? = null
        var createCalls = 0

        fun sessionFor(state: EnrollmentSessionStatus): EnrollmentSession {
            val previous = status
            status = state
            val result = session()
            status = previous
            return result
        }

        private fun session(): EnrollmentSession =
            EnrollmentSession(
                id = "enrollment-1",
                status = status,
                createdAt = Instant.parse("2026-09-25T10:00:00Z"),
                updatedAt = Instant.parse("2026-09-25T10:00:00Z"),
                expiresAt = Instant.parse("2026-09-25T10:15:00Z"),
                verifiedAt = null,
                completedAt = if (status == EnrollmentSessionStatus.COMPLETED) {
                    Instant.parse("2026-09-25T10:05:00Z")
                } else {
                    null
                },
                cancelledAt = if (status == EnrollmentSessionStatus.CANCELLED) {
                    Instant.parse("2026-09-25T10:05:00Z")
                } else {
                    null
                },
                managedDeviceId = deviceId,
                verificationAttempts = 0,
            )

        override suspend fun create(): OperationResult<EnrollmentCreation> {
            createCalls++
            return nextCreateResult ?: OperationResult.Success(
                EnrollmentCreation(session(), "secret-value"),
            ).also { nextCreateResult = null }
        }

        override suspend fun get(enrollmentId: String): OperationResult<EnrollmentSession> =
            nextGetResult ?: OperationResult.Success(session()).also { nextGetResult = null }

        override suspend fun list(): OperationResult<List<EnrollmentSession>> =
            OperationResult.Success(listResult ?: listOf(session()))

        override suspend fun cancel(enrollmentId: String): OperationResult<EnrollmentSession> {
            status = EnrollmentSessionStatus.CANCELLED
            return OperationResult.Success(session())
        }
    }
}
