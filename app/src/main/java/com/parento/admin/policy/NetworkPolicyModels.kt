package com.parento.admin.policy

data class NetworkPolicyRule(
    val id: String?,
    val domain: String,
    val action: NetworkRuleAction,
    val enabled: Boolean = true,
    val validationError: String? = null,
)

enum class NetworkRuleAction { ALLOW, BLOCK }

enum class NetworkPolicyStatus { ACTIVE, DISABLED }

enum class NetworkEnforcementStatus {
    UNKNOWN, PENDING, APPLIED, PARTIALLY_APPLIED, FAILED, UNSUPPORTED, STALE, REVOKED
}

enum class NetworkPolicyFreshness { FRESH, STALE, VERY_STALE, UNKNOWN, NEVER_REPORTED }

enum class NetworkCapabilityMode { UNKNOWN, UNSUPPORTED, SUPPORTED }

data class NetworkPolicy(
    val id: String,
    val adminId: String,
    val name: String,
    val description: String?,
    val status: NetworkPolicyStatus,
    val version: Long,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: String,
    val updatedBy: String,
    val rules: List<NetworkPolicyRule>,
)

data class NetworkPolicyAssignment(
    val managedDeviceId: String,
    val policyId: String,
    val policyVersion: Long,
    val assignedAt: String,
    val updatedAt: String,
    val assignedBy: String,
)

data class NetworkPolicySyncState(
    val managedDeviceId: String,
    val desiredPolicyId: String?,
    val desiredPolicyVersion: Long?,
    val reportedPolicyId: String?,
    val reportedPolicyVersion: Long?,
    val status: NetworkEnforcementStatus,
    val lastRequestedAt: String?,
    val lastReportedAt: String?,
    val lastErrorCode: String?,
    val updatedAt: String,
) {
    fun freshness(nowEpochMillis: Long = System.currentTimeMillis()): NetworkPolicyFreshness {
        val reported = lastReportedAt ?: return NetworkPolicyFreshness.NEVER_REPORTED
        val age = runCatching { java.time.Instant.parse(reported).toEpochMilli() }.getOrNull()
            ?: return NetworkPolicyFreshness.UNKNOWN
        return when {
            age > nowEpochMillis -> NetworkPolicyFreshness.UNKNOWN
            nowEpochMillis - age <= 5 * 60_000L -> NetworkPolicyFreshness.FRESH
            nowEpochMillis - age <= 30 * 60_000L -> NetworkPolicyFreshness.STALE
            else -> NetworkPolicyFreshness.VERY_STALE
        }
    }
}

data class NetworkPolicyCapability(
    val managedDeviceId: String,
    val supported: Boolean,
    val mode: NetworkCapabilityMode,
    val capabilityVersion: Long?,
    val reportedAt: String,
    val updatedAt: String,
)

data class NetworkPolicyDeviceState(
    val effectivePolicy: NetworkPolicy?,
    val synchronization: NetworkPolicySyncState?,
    val capability: NetworkPolicyCapability?,
    val command: com.parento.admin.device.AdminCommand? = null,
    val cachedAtEpochMillis: Long? = null,
    val offline: Boolean = false,
)
