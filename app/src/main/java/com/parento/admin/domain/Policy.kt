package com.parento.admin.domain

/**
 * Minimal policy identity for future policy-management flows.
 *
 * Policy creation, editing, enforcement, and synchronization are not implemented here.
 */
data class Policy(
    val policyId: String,
    val displayName: String
)
