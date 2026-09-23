package com.parento.admin.domain

/**
 * Minimal administrator identity used by future authenticated application flows.
 *
 * Authentication and credential handling are intentionally outside this model.
 */
data class Administrator(
    val administratorId: String,
    val displayName: String
)
