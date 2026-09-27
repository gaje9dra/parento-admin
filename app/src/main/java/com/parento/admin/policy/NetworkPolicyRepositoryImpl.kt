package com.parento.admin.policy

import com.parento.admin.communication.AdminBackendApiClient
import com.parento.admin.domain.OperationResult

class NetworkPolicyRepositoryImpl(private val api: AdminBackendApiClient) : NetworkPolicyRepository {
    override suspend fun listPolicies(cursor: String?) = api.listNetworkPolicies(cursor)
    override suspend fun getPolicy(policyId: String) = api.getNetworkPolicy(policyId)
    override suspend fun createPolicy(name: String, description: String?, rules: List<NetworkPolicyRule>) =
        api.createNetworkPolicy(name, description, rules)
    override suspend fun updatePolicy(policy: NetworkPolicy) = api.updateNetworkPolicy(policy)
    override suspend fun getDeviceState(deviceId: String) = api.getNetworkPolicyDeviceState(deviceId)
    override suspend fun assignPolicy(deviceId: String, policyId: String) = api.assignNetworkPolicy(deviceId, policyId)
    override suspend fun removePolicy(deviceId: String, policyId: String) = api.removeNetworkPolicy(deviceId, policyId)
    override suspend fun requestSync(deviceId: String) = api.requestNetworkPolicySync(deviceId)
    override suspend fun requestStatus(deviceId: String) = api.requestNetworkPolicyStatus(deviceId)
}
