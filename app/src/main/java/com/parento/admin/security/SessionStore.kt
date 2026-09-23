package com.parento.admin.security

import com.parento.admin.auth.AuthenticationSession

interface SessionStore {
    fun readSession(): AuthenticationSession?
    fun saveSession(session: AuthenticationSession)
    fun clearSession()
}
