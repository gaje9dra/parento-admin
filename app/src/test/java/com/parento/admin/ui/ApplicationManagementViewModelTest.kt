package com.parento.admin.ui

import com.parento.admin.application.*
import com.parento.admin.device.CommandStatus
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationManagementViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    @Test
    fun refreshUsesAuthoritativeEnforcementEndpoint() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val repository = FakeApplicationRepository(
            enforcement = ApplicationSynchronization(
                desiredPolicyId = "p1",
                desiredPolicyVersion = 3,
                reportedPolicyId = "p1",
                reportedPolicyVersion = 2,
                status = EnforcementStatus.PENDING,
                lastRequestedAt = "requested",
                lastReportedAt = "reported",
                updatedAt = "updated",
                errorCode = null,
            ),
        )
        val viewModel = ApplicationManagementViewModel(repository) {}
        viewModel.open("device-1", "Device")
        advanceUntilIdle()

        val state = viewModel.uiState.value as ApplicationManagementUiState.Content
        assertEquals(EnforcementStatus.PENDING, state.policyState?.synchronization?.status)
        assertEquals(2, state.policyState?.synchronization?.reportedPolicyVersion)
        assertEquals(1, repository.enforcementCalls)
    }

    @Test
    fun duplicateInventoryRequestsAreSuppressed() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val repository = FakeApplicationRepository()
        val viewModel = ApplicationManagementViewModel(repository) {}
        viewModel.open("device-1", "Device")
        advanceUntilIdle()

        viewModel.requestInventory()
        viewModel.requestInventory()
        advanceUntilIdle()

        assertEquals(1, repository.inventoryRequestCalls)
        val state = viewModel.uiState.value as ApplicationManagementUiState.Content
        assertEquals(CommandStatus.DELIVERED, state.command?.status)
    }

    @Test
    fun networkFailureKeepsLastLoadedStateAndMarksItOffline() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val repository = FakeApplicationRepository()
        val viewModel = ApplicationManagementViewModel(repository) {}
        viewModel.open("device-1", "Device")
        advanceUntilIdle()

        repository.failRefresh = true
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value as ApplicationManagementUiState.Content
        assertEquals(ApplicationDataConnectionState.OFFLINE, state.connection)
        assertTrue(state.inventory != null)
    }

    private class FakeApplicationRepository(
        private val enforcement: ApplicationSynchronization? = null,
    ) : ApplicationManagementRepository {
        var inventoryRequestCalls = 0
        var enforcementCalls = 0
        var failRefresh = false

        private val policy = ApplicationPolicy(
            id = "p1",
            name = "Test policy",
            description = null,
            status = PolicyStatus.ACTIVE,
            version = 3,
            createdAt = "created",
            updatedAt = "updated",
            createdBy = null,
            updatedBy = null,
            rules = listOf(ApplicationPolicyRule("com.example.app", PolicyAction.BLOCK)),
        )

        override suspend fun getInventory(deviceId: String, cursor: String?) =
            if (failRefresh) OperationResult.Failure(AdminError.Network)
            else OperationResult.Success(
                ApplicationInventoryPage(
                    deviceId = deviceId,
                    applications = emptyList(),
                    nextCursor = null,
                    observedAt = "observed",
                    receivedAt = "received",
                    freshness = InventoryFreshness.FRESH,
                ),
            )

        override suspend fun getApplication(deviceId: String, packageName: String) =
            OperationResult.Success(
                ApplicationInventoryItem(
                    deviceId = deviceId,
                    packageName = packageName,
                    displayName = packageName,
                    versionName = "1.0",
                    versionCode = 1,
                    installState = "INSTALLED",
                    enabled = true,
                    observedAt = "observed",
                    receivedAt = "received",
                    freshness = InventoryFreshness.FRESH,
                ),
            )

        override suspend fun requestInventory(deviceId: String) = run {
            inventoryRequestCalls++
            OperationResult.Success(
                ApplicationManagementCommand(
                    id = "command-1",
                    type = "SYNC_APPLICATION_INVENTORY",
                    status = CommandStatus.DELIVERED,
                    createdAt = "created",
                    deliveryAt = "delivered",
                    acknowledgedAt = null,
                    completedAt = null,
                    failureCode = null,
                    errorCategory = null,
                ),
            )
        }

        override suspend fun listPolicies(cursor: String?) =
            OperationResult.Success(listOf(policy) to null)

        override suspend fun getPolicy(policyId: String) = OperationResult.Success(policy)

        override suspend fun createPolicy(name: String, description: String?, rules: List<ApplicationPolicyRule>) =
            OperationResult.Success(policy)

        override suspend fun updatePolicy(policy: ApplicationPolicy, expectedVersion: Int) =
            OperationResult.Success(policy.copy(version = expectedVersion + 1))

        override suspend fun getPolicyState(deviceId: String) =
            OperationResult.Success(
                ApplicationPolicyState(
                    policy = policy,
                    assignment = PolicyAssignment(deviceId, policy.id, policy.version, null, null),
                    synchronization = null,
                ),
            )

        override suspend fun getEnforcementStatus(deviceId: String): OperationResult<ApplicationSynchronization?> {
            enforcementCalls++
            return OperationResult.Success(enforcement)
        }

        override suspend fun assignPolicy(deviceId: String, policyId: String) =
            getPolicyState(deviceId)

        override suspend fun removePolicy(deviceId: String, policyId: String) =
            getPolicyState(deviceId)

        override suspend fun syncPolicy(deviceId: String) =
            OperationResult.Success(enforcement)
    }
}
