package com.parento.admin.security

/**
 * Security boundary for future token/session/device-authorization storage.
 *
 * No credentials, tokens, keys, or authentication state are persisted in Phase 1.2.
 */
interface SecurityStore {
    fun hasAuthenticatedSession(): Boolean
}
