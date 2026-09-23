package com.parento.admin.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

class SecureSessionStore(context: Context) : SecurityStore, SessionStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun hasAuthenticatedSession(): Boolean =
        readSession() != null

    override fun readSession(): AuthenticationSession? =
        preferences.getString(SESSION_KEY, null)?.let { decrypt(it) }

    override fun saveSession(session: AuthenticationSession) {
        preferences.edit().putString(SESSION_KEY, encrypt(session)).apply()
    }

    override fun clearSession() {
        preferences.edit().remove(SESSION_KEY).apply()
    }

    private fun encrypt(session: AuthenticationSession): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = JSONObject().apply {
            put("id", session.admin.id)
            put("email", session.admin.email)
            put("status", session.admin.status)
            put("lastAuthenticatedAt", session.admin.lastAuthenticatedAt)
            put("accessToken", session.accessToken)
            put("refreshToken", session.refreshToken)
            put("accessTokenExpiresAt", session.accessTokenExpiresAtEpochMillis)
            put("sessionExpiresAt", session.sessionExpiresAtEpochMillis)
        }.toString().toByteArray(StandardCharsets.UTF_8)
        val ciphertext = cipher.doFinal(payload)
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): AuthenticationSession? = runCatching {
        val parts = encoded.split('.', limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        val json = JSONObject(
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8),
        )
        AuthenticationSession(
            admin = AuthenticatedAdmin(
                id = json.getString("id"),
                email = json.getString("email"),
                status = json.getString("status"),
                lastAuthenticatedAt = json.optString("lastAuthenticatedAt", null),
            ),
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            accessTokenExpiresAtEpochMillis = json.getLong("accessTokenExpiresAt"),
            sessionExpiresAtEpochMillis = json.getLong("sessionExpiresAt"),
        )
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "parento_admin_secure_session"
        private const val SESSION_KEY = "encrypted_session"
        private const val KEY_ALIAS = "parento_admin_session_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
