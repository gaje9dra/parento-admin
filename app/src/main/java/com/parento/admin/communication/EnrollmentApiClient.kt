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
import java.util.UUID
import org.json.JSONObject

class EnrollmentApiClient(private val config: AppConfig) : EnrollmentApi {
    override suspend fun create(accessToken: String): OperationResult<EnrollmentCreation> =
        execute("POST", "/api/v1/devices/enrollments", accessToken) { json ->
            val data = json.getJSONObject("data")
            val secret = data.getString("authorizationSecret").takeIf { it.length in 43..256 }
                ?: throw IllegalArgumentException("Invalid enrollment authorization secret")
            EnrollmentCreation(
                enrollment = parseEnrollment(data.getJSONObject("enrollment")),
                authorizationSecret = secret,
            )
        }

    override suspend fun list(accessToken: String): OperationResult<List<EnrollmentSession>> =
        execute("GET", "/api/v1/devices/enrollments", accessToken) { json ->
            val array = json.getJSONObject("data").getJSONArray("enrollments")
            buildList {
                for (i in 0 until array.length()) {
                    add(parseEnrollment(array.getJSONObject(i)))
                }
            }
        }

    override suspend fun get(
        accessToken: String,
        enrollmentId: String,
    ): OperationResult<EnrollmentSession> {
        if (!isValidUuid(enrollmentId)) {
            return OperationResult.Failure(AdminError.Validation)
        }
        return execute(
            "GET",
            "/api/v1/devices/enrollments/$enrollmentId",
            accessToken,
        ) { json ->
            parseEnrollment(json.getJSONObject("data").getJSONObject("enrollment"))
        }
    }

    override suspend fun cancel(
        accessToken: String,
        enrollmentId: String,
    ): OperationResult<EnrollmentSession> {
        if (!isValidUuid(enrollmentId)) {
            return OperationResult.Failure(AdminError.Validation)
        }
        return execute(
            "POST",
            "/api/v1/devices/enrollments/$enrollmentId/cancel",
            accessToken,
        ) { json ->
            parseEnrollment(json.getJSONObject("data").getJSONObject("enrollment"))
        }
    }

    private suspend fun <T> execute(
        method: String,
        path: String,
        bearerToken: String,
        parse: (JSONObject) -> T,
    ): OperationResult<T> {
        if (bearerToken.isBlank()) {
            return OperationResult.Failure(AdminError.SessionExpired)
        }

        var connection: HttpURLConnection? = null
        return try {
            connection =
                (URL(config.backendBaseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer $bearerToken")
                    if (method == "POST") {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        outputStream.use {
                            it.write("{}\n".toByteArray(StandardCharsets.UTF_8))
                        }
                    }
                }

            val status = connection.responseCode
            val stream =
                if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let(::readBoundedBody).orEmpty()

            if (status in 200..299) {
                if (body.isBlank()) {
                    OperationResult.Failure(AdminError.Backend)
                } else {
                    OperationResult.Success(parse(JSONObject(body)))
                }
            } else {
                OperationResult.Failure(mapError(status, body))
            }
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
        val id = json.getString("id").takeIf(::isValidUuid)
            ?: throw IllegalArgumentException("Invalid enrollment id")
        val status = runCatching {
            EnrollmentSessionStatus.valueOf(json.getString("status"))
        }.getOrElse {
            throw IllegalArgumentException("Invalid enrollment status")
        }
        val createdAt = Instant.parse(json.getString("createdAt"))
        val updatedAt = Instant.parse(json.getString("updatedAt"))
        val expiresAt = Instant.parse(json.getString("expiresAt"))
        require(!expiresAt.isBefore(createdAt))
        require(!updatedAt.isBefore(createdAt))

        val attempts = json.getInt("verificationAttempts")
        require(attempts >= 0)

        val managedDeviceId =
            json.optString("managedDeviceId", "").takeIf { it.isNotBlank() }
        if (status == EnrollmentSessionStatus.COMPLETED) {
            require(managedDeviceId != null)
            require(json.optString("completedAt", "").isNotBlank())
        }

        return EnrollmentSession(
            id = id,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            expiresAt = expiresAt,
            verifiedAt = json.optString("verifiedAt", "").takeIf { it.isNotBlank() }
                ?.let(Instant::parse),
            completedAt = json.optString("completedAt", "").takeIf { it.isNotBlank() }
                ?.let(Instant::parse),
            cancelledAt = json.optString("cancelledAt", "").takeIf { it.isNotBlank() }
                ?.let(Instant::parse),
            managedDeviceId = managedDeviceId,
            verificationAttempts = attempts,
        )
    }

    private fun mapError(status: Int, body: String): AdminError {
        val code = runCatching {
            JSONObject(body).getJSONObject("error").getString("code")
        }.getOrNull()

        return when {
            status == 401 -> AdminError.SessionExpired
            status == 403 -> AdminError.Authorization
            status == 404 || code == "ENROLLMENT_NOT_FOUND" -> AdminError.EnrollmentNotFound
            status == 409 && code == "ENROLLMENT_ALREADY_CONSUMED" ->
                AdminError.EnrollmentAlreadyConsumed
            status == 409 || code == "ENROLLMENT_STATE_CONFLICT" ->
                AdminError.EnrollmentStateConflict
            status == 410 || code == "ENROLLMENT_EXPIRED" -> AdminError.EnrollmentExpired
            status == 429 || code == "RATE_LIMITED" -> AdminError.EnrollmentRateLimited
            status == 400 -> AdminError.Validation
            status >= 500 -> AdminError.ServerUnavailable
            else -> AdminError.Backend
        }
    }

    private fun readBoundedBody(stream: java.io.InputStream): String {
        stream.use {
            val buffer = ByteArray(BODY_CHUNK_SIZE)
            val output = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_RESPONSE_BYTES) {
                    throw IOException("Enrollment response exceeds maximum size")
                }
                output.write(buffer, 0, read)
            }
            return output.toString(StandardCharsets.UTF_8.name())
        }
    }

    private fun isValidUuid(value: String): Boolean =
        runCatching { UUID.fromString(value) }.isSuccess

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_RESPONSE_BYTES = 64 * 1024
        private const val BODY_CHUNK_SIZE = 8 * 1024
    }
}
