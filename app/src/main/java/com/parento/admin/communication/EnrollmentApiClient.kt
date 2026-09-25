package com.parento.admin.communication

import com.parento.admin.config.AppConfig
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.EnrollmentCreation
import com.parento.admin.domain.EnrollmentSession
import com.parento.admin.domain.EnrollmentSessionStatus
import com.parento.admin.domain.OperationResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.json.JSONObject

class EnrollmentApiClient(private val config: AppConfig) : EnrollmentApi {
    override suspend fun create(accessToken: String): OperationResult<EnrollmentCreation> =
        execute("POST", "/api/v1/devices/enrollments", accessToken) { json ->
            val data = json.getJSONObject("data")
            val secret = data.getString("authorizationSecret").takeIf { it.length in 20..256 }
                ?: throw IllegalArgumentException("Invalid enrollment authorization secret")
            EnrollmentCreation(parseEnrollment(data.getJSONObject("enrollment")), secret)
        }

    override suspend fun list(accessToken: String): OperationResult<List<EnrollmentSession>> =
        execute("GET", "/api/v1/devices/enrollments", accessToken) { json ->
            val array = json.getJSONObject("data").getJSONArray("enrollments")
            buildList { for (i in 0 until array.length()) add(parseEnrollment(array.getJSONObject(i))) }
        }

    override suspend fun get(accessToken: String, enrollmentId: String): OperationResult<EnrollmentSession> =
        execute("GET", "/api/v1/devices/enrollments/$enrollmentId", accessToken) { json ->
            parseEnrollment(json.getJSONObject("data").getJSONObject("enrollment"))
        }

    override suspend fun cancel(accessToken: String, enrollmentId: String): OperationResult<EnrollmentSession> =
        execute("POST", "/api/v1/devices/enrollments/$enrollmentId/cancel", accessToken) { json ->
            parseEnrollment(json.getJSONObject("data").getJSONObject("enrollment"))
        }

    private suspend fun <T> execute(
        method: String,
        path: String,
        bearerToken: String,
        parse: (JSONObject) -> T,
    ): OperationResult<T> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(config.backendBaseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $bearerToken")
                if (method == "POST") {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write("{}\n".toByteArray(StandardCharsets.UTF_8)) }
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status in 200..299) OperationResult.Success(parse(JSONObject(body)))
            else OperationResult.Failure(mapError(status, body))
        } catch (_: java.net.SocketTimeoutException) {
            OperationResult.Failure(AdminError.Timeout)
        } catch (_: IOException) {
            OperationResult.Failure(AdminError.Network)
        } catch (_: Exception) {
            OperationResult.Failure(AdminError.Unknown)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseEnrollment(json: JSONObject): EnrollmentSession {
        val status = EnrollmentSessionStatus.valueOf(json.getString("status"))
        val id = json.getString("id").takeIf { it.isNotBlank() } ?: error("Missing enrollment id")
        val attempts = json.getInt("verificationAttempts")
        require(attempts >= 0)
        return EnrollmentSession(
            id = id,
            status = status,
            createdAt = Instant.parse(json.getString("createdAt")),
            updatedAt = Instant.parse(json.getString("updatedAt")),
            expiresAt = Instant.parse(json.getString("expiresAt")),
            verifiedAt = json.optString("verifiedAt", "").takeIf { it.isNotBlank() }?.let(Instant::parse),
            completedAt = json.optString("completedAt", "").takeIf { it.isNotBlank() }?.let(Instant::parse),
            cancelledAt = json.optString("cancelledAt", "").takeIf { it.isNotBlank() }?.let(Instant::parse),
            managedDeviceId = json.optString("managedDeviceId", "").takeIf { it.isNotBlank() },
            verificationAttempts = attempts,
        )
    }

    private fun mapError(status: Int, body: String): AdminError {
        val code = runCatching { JSONObject(body).getJSONObject("error").getString("code") }.getOrNull()
        return when {
            status == 401 -> AdminError.SessionExpired
            status == 403 -> AdminError.Authorization
            status == 404 || code == "ENROLLMENT_NOT_FOUND" -> AdminError.EnrollmentNotFound
            code == "ENROLLMENT_EXPIRED" -> AdminError.EnrollmentExpired
            code == "ENROLLMENT_STATE_CONFLICT" -> AdminError.EnrollmentStateConflict
            code == "ENROLLMENT_ALREADY_CONSUMED" -> AdminError.EnrollmentAlreadyConsumed
            status == 429 || code == "RATE_LIMITED" -> AdminError.EnrollmentRateLimited
            status == 400 -> AdminError.Validation
            status >= 500 -> AdminError.ServerUnavailable
            else -> AdminError.Backend
        }
    }

    companion object {
        private const val TIMEOUT_MS = 15_000
    }
}
