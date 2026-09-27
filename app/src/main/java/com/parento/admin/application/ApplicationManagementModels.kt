package com.parento.admin.application

enum class InventoryFreshness { FRESH, STALE, VERY_STALE, NEVER_REPORTED, DISCONNECTED, REVOKED, UNKNOWN }

enum class PolicyStatus { ACTIVE, DISABLED }
enum class PolicyAction { ALLOW, BLOCK }
enum class EnforcementStatus { UNKNOWN, PENDING, APPLIED, PARTIALLY_APPLIED, FAILED, STALE }

data class ApplicationInventoryItem(
    val deviceId: String,
    val packageName: String,
    val displayName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val installState: String,
    val enabled: Boolean?,
    val observedAt: String?,
    val receivedAt: String?,
    val freshness: InventoryFreshness,
    val policyAction: PolicyAction? = null,
    val enforcementStatus: EnforcementStatus = EnforcementStatus.UNKNOWN,
)

data class ApplicationInventoryPage(
    val deviceId: String,
    val applications: List<ApplicationInventoryItem>,
    val nextCursor: String?,
    val observedAt: String?,
    val receivedAt: String?,
    val freshness: InventoryFreshness,
)

data class ApplicationPolicyRule(
    val packageName: String,
    val action: PolicyAction,
)

data class ApplicationPolicy(
    val id: String,
    val name: String,
    val description: String?,
    val status: PolicyStatus,
    val version: Int,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: String?,
    val updatedBy: String?,
    val rules: List<ApplicationPolicyRule>,
)

data class PolicyAssignment(
    val deviceId: String,
    val policyId: String?,
    val policyVersion: Int?,
    val assignedAt: String?,
    val updatedAt: String?,
)

data class ApplicationSynchronization(
    val desiredPolicyId: String?,
    val desiredPolicyVersion: Int?,
    val reportedPolicyId: String?,
    val reportedPolicyVersion: Int?,
    val status: EnforcementStatus,
    val lastRequestedAt: String?,
    val lastReportedAt: String?,
    val updatedAt: String?,
    val errorCode: String?,
)

data class ApplicationPolicyState(
    val policy: ApplicationPolicy?,
    val assignment: PolicyAssignment?,
    val synchronization: ApplicationSynchronization?,
)

fun validAndroidPackageName(value: String): Boolean =
    value.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+$"))
