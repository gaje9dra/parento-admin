package com.parento.admin.security

/**
 * Security boundary for authenticated administrator session state.
 *
 * Implementations keep sensitive session material outside the Room database.
 */
interface SecurityStore {
    fun hasAuthenticatedSession(): Boolean
}
