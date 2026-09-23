package com.parento.admin.policy

import com.parento.admin.domain.ManagedDevice
import com.parento.admin.domain.OperationResult
import com.parento.admin.domain.Policy

/**
 * Boundary for future administrator policy-management operations.
 *
 * Policy enforcement remains outside this phase.
 */
interface PolicyManager {
    fun getPolicies(device: ManagedDevice): OperationResult<List<Policy>>
    fun savePolicy(policy: Policy): OperationResult<Policy>
}
