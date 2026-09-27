package com.parento.admin.application

import com.parento.admin.domain.OperationResult

interface ApplicationManagementRepository {
    suspend fun getInventory(deviceId: String, cursor: String? = null): OperationResult<ApplicationInventoryPage>
    suspend fun getApplication(deviceId: String, packageName: String): OperationResult<ApplicationInventoryItem>
    suspend fun requestInventory(deviceId: String): OperationResult<String>
    suspend fun listPolicies(cursor: String? = null): OperationResult<Pair<List<ApplicationPolicy>, String?>>
    suspend fun getPolicy(policyId: String): OperationResult<ApplicationPolicy>
    suspend fun createPolicy(name: String, description: String?, rules: List<ApplicationPolicyRule>): OperationResult<ApplicationPolicy>
    suspend fun updatePolicy(policy: ApplicationPolicy, expectedVersion: Int): OperationResult<ApplicationPolicy>
    suspend fun getPolicyState(deviceId: String): OperationResult<ApplicationPolicyState>
    suspend fun assignPolicy(deviceId: String, policyId: String): OperationResult<ApplicationPolicyState>
    suspend fun removePolicy(deviceId: String, policyId: String): OperationResult<ApplicationPolicyState>
    suspend fun syncPolicy(deviceId: String): OperationResult<ApplicationSynchronization?>
}
