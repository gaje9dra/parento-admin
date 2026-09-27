package com.parento.admin.policy

import com.parento.admin.domain.OperationResult

interface NetworkPolicyRepository {
    suspend fun listPolicies(cursor: String? = null): OperationResult<Pair<List<NetworkPolicy>, String?>>
    suspend fun getPolicy(policyId: String): OperationResult<NetworkPolicy>
    suspend fun createPolicy(name: String, description: String?, rules: List<NetworkPolicyRule>): OperationResult<NetworkPolicy>
    suspend fun updatePolicy(policy: NetworkPolicy): OperationResult<NetworkPolicy>
    suspend fun getDeviceState(deviceId: String): OperationResult<NetworkPolicyDeviceState>
    suspend fun assignPolicy(deviceId: String, policyId: String): OperationResult<NetworkPolicyDeviceState>
    suspend fun removePolicy(deviceId: String, policyId: String): OperationResult<NetworkPolicyDeviceState>
    suspend fun requestSync(deviceId: String): OperationResult<NetworkPolicyDeviceState>
    suspend fun requestStatus(deviceId: String): OperationResult<NetworkPolicyDeviceState>
}
