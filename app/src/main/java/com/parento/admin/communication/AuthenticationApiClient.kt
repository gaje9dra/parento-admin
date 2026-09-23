package com.parento.admin.communication

import com.parento.admin.auth.AdminLoginCredentials
import com.parento.admin.auth.AuthenticatedAdmin
import com.parento.admin.auth.AuthenticationSession
import com.parento.admin.config.AppConfig
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

class AuthenticationApiClient(
    private val config: AppConfig,
) : AuthenticationApi {
    override suspend fun login(credentials: AdminLoginCredentials): OperationResult<AuthenticationSession> =
        execute(
            method = "POST",
            path = "/api/v1/auth/admin/login",
            body = JSONObject().apply {
                put("email", credentials.email.trim())
                put("password", credentials.password)
            }.toString(),
        ) { json ->
            parseSession(json)
        }

    override suspend fun refresh(session: AuthenticationSession): OperationResult<AuthenticationSession> =
        execute(
            method = "POST",
            path = "/api/v1/auth/admin/refresh",
            body = JSONObject().put("refreshToken", session.refreshToken).toString(),
        ) { json ->
            val data = json.getJSONObject("data")
            session.copy(
                accessToken = data.getString("accessToken").takeIf { it.length in 20..256 }
                    ?: throw IllegalArgumentException("Invalid access token"),
                refreshToken = data.getString("refreshToken").takeIf { it.length in 20..256 }
                    ?: throw IllegalArgumentException("Invalid refresh token"),
                accessTokenExpiresAtEpochMillis =
                    parseTimestamp(data.getString("accessTokenExpiresAt")),
                sessionExpiresAtEpochMillis =
                    parseTimestamp(data.getString("sessionExpiresAt")),
            )
        }

    override suspend fun current(session: AuthenticationSession): OperationResult<AuthenticatedAdmin> =
        execute(
            method = "GET",
            path = "/api/v1/auth/admin/me",
            bearerToken = session.accessToken,
        ) { json ->
            parseAdmin(json.getJSONObject("data").getJSONObject("admin"))
        }

    override suspend fun logout(session: AuthenticationSession): OperationResult<Unit> =
        execute(
            method = "POST",
            path = "/api/v1/auth/admin/logout",
            bearerToken = session.accessToken,
            parse = { Unit },
        )

    private suspend fun <T> execute(
        method: String,
        path: String,
        body: String? = null,
        bearerToken: String? = null,
        parse: (JSONObject) -> T,
    ): OperationResult<T> {
        return try {
            val connection = (URL(config.backendBaseUrl.trimEnd('/') + path).openConnection()
                as HttpURLConnection)
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            if (bearerToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            }

            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status in 200..299) {
                OperationResult.Success(
                    if (status == HttpURLConnection.HTTP_NO_CONTENT) JSONObject()
                    else JSONObject(responseBody),
                ).let { result -> OperationResult.Success(parse(result.value)) }
            } else {
                OperationResult.Failure(mapHttpError(status, responseBody))
            }
        } catch (_: java.net.SocketTimeoutException) {
            OperationResult.Failure(AdminError.Timeout)
        } catch (_: IOException) {
            OperationResult.Failure(AdminError.Network)
        } catch (_: Exception) {
            OperationResult.Failure(AdminError.UnknownAuthentication)
        }
    }

    private fun parseSession(json: JSONObject): AuthenticationSession {
        val data = json.getJSONObject("data")
        return AuthenticationSession(
            admin = parseAdmin(data.getJSONObject("admin")),
            accessToken = data.getString("accessToken").takeIf { it.length in 20..256 }
                ?: throw IllegalArgumentException("Invalid access token"),
            refreshToken = data.getString("refreshToken").takeIf { it.length in 20..256 }
                ?: throw IllegalArgumentException("Invalid refresh token"),
            accessTokenExpiresAtEpochMillis =
                parseTimestamp(data.getString("accessTokenExpiresAt")),
            sessionExpiresAtEpochMillis =
                parseTimestamp(data.getString("sessionExpiresAt")),
        )
    }

    private fun parseAdmin(json: JSONObject): AuthenticatedAdmin =
        AuthenticatedAdmin(
            id = json.getString("id"),
            email = json.getString("email"),
            status = json.getString("status"),
            lastAuthenticatedAt = json.optString("lastAuthenticatedAt", null),
        )

    private fun parseTimestamp(value: String): Long =
        java.time.Instant.parse(value).toEpochMilli()

    private fun mapHttpError(status: Int, body: String): AdminError {
        val code = runCatching {
            JSONObject(body).getJSONObject("error").getString("code")
        }.getOrNull()
        return when {
            status == 401 && code == "INVALID_CREDENTIALS" -> AdminError.InvalidCredentials
            status == 401 && code == "SESSION_REVOKED" -> AdminError.SessionRevoked
            status == 401 -> AdminError.SessionExpired
            status == 403 && code == "ACCOUNT_DISABLED" -> AdminError.AccountDisabled
            status == 403 -> AdminError.Authorization
            status == 400 -> AdminError.Validation
            status == 429 -> AdminError.ServerUnavailable
            status >= 500 -> AdminError.ServerUnavailable
            else -> AdminError.UnknownAuthentication
        }
    }

    companion object {
        private const val TIMEOUT_MS = 15_000
    }
}
