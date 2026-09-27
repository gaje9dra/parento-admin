package com.parento.admin.data

import com.parento.admin.application.*
import com.parento.admin.communication.AdminBackendApiClient
import com.parento.admin.domain.OperationResult

class ApplicationManagementRepositoryImpl(private val api: AdminBackendApiClient) : ApplicationManagementRepository {
    override suspend fun getInventory(deviceId: String, cursor: String?) = api.listApplicationInventory(deviceId, cursor)
    override suspend fun getApplication(deviceId: String, packageName: String) = api.getApplicationInventoryItem(deviceId, packageName)
    override suspend fun requestInventory(deviceId: String) = api.requestApplicationInventory(deviceId)
    override suspend fun listPolicies(cursor: String?) = api.listApplicationPolicies(cursor)
    override suspend fun getPolicy(policyId: String) = api.getApplicationPolicy(policyId)
    override suspend fun createPolicy(name: String, description: String?, rules: List<ApplicationPolicyRule>) = api.createApplicationPolicy(name, description, rules)
    override suspend fun updatePolicy(policy: ApplicationPolicy, expectedVersion: Int) = api.updateApplicationPolicy(policy, expectedVersion)
    override suspend fun getPolicyState(deviceId: String) = api.getApplicationPolicyState(deviceId)
    override suspend fun assignPolicy(deviceId: String, policyId: String) = api.assignApplicationPolicy(deviceId, policyId)
    override suspend fun removePolicy(deviceId: String, policyId: String) = api.removeApplicationPolicy(deviceId, policyId)
    override suspend fun syncPolicy(deviceId: String) = api.syncApplicationPolicy(deviceId)
}
