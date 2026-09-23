package com.parento.admin.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parento.admin.auth.AuthenticatedAdmin
import com.parento.admin.auth.AuthenticationSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureSessionStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun encryptedSessionRoundTripsWithoutPlaintextStorage() {
        val store = SecureSessionStore(context)
        val session = session()

        store.saveSession(session)

        val preferences = context.getSharedPreferences(
            "parento_admin_secure_session",
            Context.MODE_PRIVATE,
        )
        val raw = preferences.getString("encrypted_session", null)

        assertEquals(session, store.readSession())
        assertEquals(false, raw?.contains(session.accessToken) == true)
        assertEquals(false, raw?.contains(session.refreshToken) == true)

        store.clearSession()
        assertNull(store.readSession())
    }

    @Test
    fun malformedStoredSessionIsRemoved() {
        val preferences = context.getSharedPreferences(
            "parento_admin_secure_session",
            Context.MODE_PRIVATE,
        )
        preferences.edit().putString("encrypted_session", "not-a-valid-session").commit()

        val store = SecureSessionStore(context)

        assertNull(store.readSession())
        assertNull(preferences.getString("encrypted_session", null))
    }

    private fun session() = AuthenticationSession(
        admin = AuthenticatedAdmin(
            id = "admin-1",
            email = "admin@example.com",
            status = "ACTIVE",
            lastAuthenticatedAt = null,
        ),
        accessToken = "access-token-12345678901234567890",
        refreshToken = "refresh-token-12345678901234567890",
        accessTokenExpiresAtEpochMillis = System.currentTimeMillis() + 60_000,
        sessionExpiresAtEpochMillis = System.currentTimeMillis() + 3_600_000,
    )
}
