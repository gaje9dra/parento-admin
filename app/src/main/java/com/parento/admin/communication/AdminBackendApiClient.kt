package com.parento.admin.communication

import com.parento.admin.auth.AuthenticationRepository
import com.parento.admin.auth.AuthenticationSession
import com.parento.admin.config.AppConfig
import com.parento.admin.device.AdminCommand
import com.parento.admin.device.DeviceListPage
import com.parento.admin.device.AdminCommandType
import com.parento.admin.device.CommandStatus
import com.parento.admin.device.DeviceMonitoring
import com.parento.admin.device.EnrollmentDeviceReference
import com.parento.admin.device.ManagedDeviceStatus
import com.parento.admin.device.ManagementMode
import com.parento.admin.device.MonitoringFreshness
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.ConnectionState
import com.parento.admin.domain.DeviceStatus
import com.parento.admin.domain.EnrollmentState
import com.parento.admin.domain.OperationResult
import com.parento.admin.security.SessionStore
import com.parento.admin.screensharing.ScreenSharingSession
import com.parento.admin.screensharing.ScreenSharingSessionStatus
import com.parento.admin.screensharing.ScreenTransportState
import com.parento.admin.audio.AudioAccessSession
import com.parento.admin.audio.AudioAccessSessionStatus
import com.parento.admin.audio.AudioTransportState
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

class AdminBackendApiClient(
    private val config: AppConfig,
    private val authenticationRepository: AuthenticationRepository,
    private val sessionStore: SessionStore,
) : BackendClient {
    override fun listDevices(): OperationResult<List<com.parento.admin.domain.ManagedDevice>> =
        OperationResult.Failure(AdminError.Backend)

    override fun getDevice(deviceId: String): OperationResult<com.parento.admin.domain.ManagedDevice> =
        OperationResult.Failure(AdminError.DeviceNotFound(deviceId))

    override fun managePolicy(policy: com.parento.admin.domain.Policy): OperationResult<com.parento.admin.domain.Policy> =
        OperationResult.Failure(AdminError.Policy)

    override fun sendEvent(event: AdminEvent): OperationResult<Unit> =
        OperationResult.Failure(AdminError.Backend)

    suspend fun listDevices(cursor: String? = null): OperationResult<DeviceListPage> {
        val path = buildString {
            append("/api/v1/devices?limit=50")
            if (!cursor.isNullOrBlank()) append("&cursor=").append(java.net.URLEncoder.encode(cursor, "UTF-8"))
        }
        return execute("GET", path) { root ->
            val data = root.getJSONObject("data")
            val items = data.getJSONArray("items")
            val devices = buildList {
                for (i in 0 until items.length()) {
                    add(parseDeviceListItem(items.getJSONObject(i)))
                }
            }
            DeviceListPage(
                devices = devices,
                nextCursor = data.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }

    suspend fun listEnrollmentDevices(): OperationResult<List<EnrollmentDeviceReference>> =
        execute("GET", "/api/v1/devices/enrollments") { root ->
            val enrollments = root.getJSONObject("data").getJSONArray("enrollments")
            val result = linkedMapOf<String, EnrollmentDeviceReference>()
            for (i in 0 until enrollments.length()) {
                val item = enrollments.getJSONObject(i)
                val deviceId = item.optString("managedDeviceId").takeIf { it.isNotBlank() }
                val status = item.optString("status", "UNKNOWN")
                if (deviceId != null && status == "COMPLETED") {
                    result.putIfAbsent(deviceId, EnrollmentDeviceReference(deviceId, status))
                }
            }
            result.values.toList()
        }

    private fun parseDeviceListItem(item: JSONObject): com.parento.admin.device.ManagedDeviceStatus {
        val device = item.getJSONObject("device")
        val connection = item.getJSONObject("connection")
        val monitoring = item.getJSONObject("monitoring")
        return ManagedDeviceStatus(
            deviceId = device.getString("id"),
            displayName = device.optString("name").ifBlank { device.optString("stableIdentifier").ifBlank { device.getString("id") } },
            enrollmentState = enrollmentState(device.optString("enrollmentStatus")),
            deviceStatus = deviceStatus(device.optString("operationalStatus")),
            connectionState = connectionState(connection.optString("state")),
            firstEnrolledAt = nullableString(device, "firstEnrolledAt"),
            lastSeenAt = nullableString(connection, "lastSeenAt"),
            lastSeenAgeMs = null,
            expiresAt = nullableString(connection, "expiresAt"),
            monitoringFreshness = freshness(monitoring.optString("freshness")),
            monitoring = DeviceMonitoring(
                androidVersion = nullableString(monitoring, "androidVersion") ?: "Unknown",
                apiLevel = nullableInt(monitoring, "apiLevel") ?: 0,
                appVersion = nullableString(monitoring, "appVersion") ?: "Unknown",
                appVersionCode = 0,
                managementMode = managementMode(monitoring.optString("managementMode")),
                batteryPercentage = nullableInt(monitoring, "batteryPercentage"),
                chargingState = nullableString(monitoring, "chargingState") ?: "UNKNOWN",
                batteryStatus = "UNKNOWN",
                networkState = nullableString(monitoring, "networkState") ?: "UNKNOWN",
                storageTotalBytes = null,
                storageAvailableBytes = nullableLong(monitoring, "storageAvailableBytes"),
                storageUsedBytes = null,
                memoryTotalBytes = null,
                memoryAvailableBytes = nullableLong(monitoring, "memoryAvailableBytes"),
                memoryLow = null,
                lastSuccessfulInitializationAt = null,
                lastSuccessfulCommunicationAt = null,
                lastMonitoringUpdateAt = nullableString(monitoring, "lastTelemetryAt") ?: "",
            ),
        )
    }

    suspend fun getDeviceStatus(deviceId: String): OperationResult<ManagedDeviceStatus> =
        execute("GET", "/api/v1/devices/" + deviceId + "/status") { root ->
            parseDeviceStatus(root.getJSONObject("data"))
        }

    suspend fun createScreenSharingSession(
        deviceId: String,
        correlationId: String,
    ): OperationResult<ScreenSharingSession> =
        execute(
            "POST",
            "/api/v1/devices/" + deviceId + "/screen-sessions",
            JSONObject().apply { put("correlationId", correlationId) }.toString(),
        ) { root ->
            parseScreenSharingSession(root.getJSONObject("data").getJSONObject("session"))
        }

    suspend fun getScreenSharingSession(
        sessionId: String,
    ): OperationResult<ScreenSharingSession> =
        execute("GET", "/api/v1/screen-sessions/" + sessionId) { root ->
            parseScreenSharingSession(root.getJSONObject("data").getJSONObject("session"))
        }

    suspend fun stopScreenSharingSession(
        sessionId: String,
    ): OperationResult<ScreenSharingSession> =
        execute("POST", "/api/v1/screen-sessions/" + sessionId + "/stop") { root ->
            parseScreenSharingSession(root.getJSONObject("data").getJSONObject("session"))
        }

    suspend fun createAudioAccessSession(
        deviceId: String,
        correlationId: String,
    ): OperationResult<AudioAccessSession> =
        execute(
            "POST",
            "/api/v1/devices/" + deviceId + "/audio-sessions",
            JSONObject().apply { put("correlationId", correlationId) }.toString(),
        ) { root ->
            parseAudioAccessSession(root.getJSONObject("data").getJSONObject("session"))
        }

    suspend fun getAudioAccessSession(
        sessionId: String,
    ): OperationResult<AudioAccessSession> {
        if (sessionId.isBlank()) return OperationResult.Failure(AdminError.Validation)
        return execute("GET", "/api/v1/audio-sessions/" + encodePathSegment(sessionId)) { root ->
            parseAudioAccessSession(root.getJSONObject("data").getJSONObject("session"))
        }
    }

    suspend fun stopAudioAccessSession(
        sessionId: String,
    ): OperationResult<AudioAccessSession> {
        if (sessionId.isBlank()) return OperationResult.Failure(AdminError.Validation)
        return execute("POST", "/api/v1/audio-sessions/" + encodePathSegment(sessionId) + "/stop") { root ->
            parseAudioAccessSession(root.getJSONObject("data").getJSONObject("session"))
        }
    }

    suspend fun createFutureCommand(deviceId: String, idempotencyKey: String): OperationResult<AdminCommand> =
        execute(
            "POST",
            "/api/v1/devices/" + deviceId + "/commands",
            JSONObject().apply {
                put("type", AdminCommandType.FUTURE_COMMAND.wireValue)
                put("version", 1)
                put("payload", JSONObject())
                put("idempotencyKey", idempotencyKey)
            }.toString(),
        ) { root -> parseCommand(root.getJSONObject("data").getJSONObject("command")) }

    suspend fun getCommand(deviceId: String, commandId: String): OperationResult<AdminCommand> =
        execute("GET", "/api/v1/devices/" + deviceId + "/commands/" + commandId) { root ->
            parseCommand(root.getJSONObject("data").getJSONObject("command"))
        }

    suspend fun cancelCommand(deviceId: String, commandId: String): OperationResult<AdminCommand> =
        execute("POST", "/api/v1/devices/" + deviceId + "/commands/" + commandId + "/cancel") { root ->
            parseCommand(root.getJSONObject("data").getJSONObject("command"))
        }

    private suspend fun <T> execute(
        method: String,
        path: String,
        body: String? = null,
        retryAfterRefresh: Boolean = true,
        parse: (JSONObject) -> T,
    ): OperationResult<T> {
        val session = sessionStore.readSession()
            ?: return OperationResult.Failure(AdminError.SessionExpired)
        return try {
            val response = request(session, method, path, body)
            if (response.status == 401 && retryAfterRefresh) {
                when (val refreshed = authenticationRepository.getCurrentAuthenticatedAdmin()) {
                    is OperationResult.Success -> return execute(method, path, body, false, parse)
                    is OperationResult.Failure -> return OperationResult.Failure(refreshed.error)
                }
            }
            if (response.status !in 200..299) {
                OperationResult.Failure(mapHttpError(response.status, response.body))
            } else {
                OperationResult.Success(parse(JSONObject(response.body.ifBlank { "{}" })))
            }
        } catch (_: java.net.SocketTimeoutException) {
            OperationResult.Failure(AdminError.Timeout)
        } catch (_: IOException) {
            OperationResult.Failure(AdminError.Network)
        } catch (_: IllegalArgumentException) {
            OperationResult.Failure(AdminError.Backend)
        } catch (_: Exception) {
            OperationResult.Failure(AdminError.Backend)
        }
    }

    private fun request(session: AuthenticationSession, method: String, path: String, body: String?): HttpResponse {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(config.backendBaseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection)
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer " + session.accessToken)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponse(status, responseBody)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseDeviceStatus(data: JSONObject): ManagedDeviceStatus {
        val device = data.getJSONObject("device")
        val connection = data.getJSONObject("connection")
        val monitoring = data.getJSONObject("monitoring")
        val snapshot = monitoring.optJSONObject("snapshot")
        return ManagedDeviceStatus(
            deviceId = device.getString("id"),
            displayName = device.optString("name").ifBlank { device.getString("id") },
            enrollmentState = enrollmentState(device.optString("enrollmentStatus")),
            deviceStatus = deviceStatus(device.optString("operationalStatus")),
            connectionState = connectionState(connection.optString("state")),
            lastSeenAt = nullableString(connection, "lastSeenAt"),
            lastSeenAgeMs = nullableLong(connection, "lastSeenAgeMs"),
            expiresAt = nullableString(connection, "expiresAt"),
            monitoringFreshness = freshness(monitoring.optString("freshness")),
            monitoring = snapshot?.let(::parseMonitoring),
        )
    }

    private fun parseMonitoring(json: JSONObject) = DeviceMonitoring(
        androidVersion = json.optString("androidVersion", "Unknown"),
        apiLevel = json.optInt("apiLevel", 0),
        appVersion = json.optString("appVersion", "Unknown"),
        appVersionCode = json.optInt("appVersionCode", 0),
        managementMode = managementMode(json.optString("managementMode")),
        batteryPercentage = nullableInt(json, "batteryPercentage"),
        chargingState = json.optString("chargingState", "UNKNOWN"),
        batteryStatus = json.optString("batteryStatus", "UNKNOWN"),
        networkState = json.optString("networkState", "UNKNOWN"),
        storageTotalBytes = nullableLong(json, "storageTotalBytes"),
        storageAvailableBytes = nullableLong(json, "storageAvailableBytes"),
        storageUsedBytes = nullableLong(json, "storageUsedBytes"),
        memoryTotalBytes = nullableLong(json, "memoryTotalBytes"),
        memoryAvailableBytes = nullableLong(json, "memoryAvailableBytes"),
        memoryLow = if (json.has("memoryLow") && !json.isNull("memoryLow")) json.getBoolean("memoryLow") else null,
        lastSuccessfulInitializationAt = nullableString(json, "lastSuccessfulInitializationAt"),
        lastSuccessfulCommunicationAt = nullableString(json, "lastSuccessfulCommunicationAt"),
        lastMonitoringUpdateAt = json.optString("lastMonitoringUpdateAt", ""),
        serverReceivedAt = nullableString(json, "serverReceivedAt"),
    )

    private fun parseScreenSharingSession(json: JSONObject): ScreenSharingSession {
        val transport = json.optJSONObject("transportState")
        val details = linkedMapOf<String, String>()
        transport?.keys()?.forEach { key ->
            if (!transport.isNull(key)) details[key] = transport.optString(key)
        }
        return ScreenSharingSession(
            sessionId = json.getString("sessionId"),
            managedDeviceId = json.getString("deviceId"),
            status = ScreenSharingSessionStatus.valueOf(json.getString("status")),
            createdAt = json.getString("createdAt"),
            authorizedAt = nullableString(json, "authorizedAt"),
            startedAt = nullableString(json, "startedAt"),
            expiresAt = json.getString("expiresAt"),
            stoppedAt = nullableString(json, "stoppedAt"),
            lastActivityAt = json.getString("lastActivityAt"),
            terminationReason = nullableString(json, "terminationReason"),
            correlationId = json.getString("correlationId"),
            transportState = when (transport?.optString("state")?.uppercase()) {
                "CONNECTED", "ACTIVE" -> ScreenTransportState.CONNECTED
                "CONNECTING", "STARTING" -> ScreenTransportState.CONNECTING
                "DISCONNECTED" -> ScreenTransportState.DISCONNECTED
                "ERROR", "FAILED" -> ScreenTransportState.ERROR
                else -> ScreenTransportState.UNAVAILABLE
            },
            transportStateDetails = details,
        )
    }

    private fun parseAudioAccessSession(json: JSONObject): AudioAccessSession {
        val transport = json.optJSONObject("transportState")
        val transportState = when (transport?.optString("state")?.uppercase()) {
            "CONNECTING", "STARTING" -> AudioTransportState.CONNECTING
            "ACTIVE", "CONNECTED" -> AudioTransportState.ACTIVE
            "DISCONNECTED", "STOPPING", "STOPPED" -> AudioTransportState.DISCONNECTED
            "ERROR", "FAILED" -> AudioTransportState.ERROR
            else -> AudioTransportState.UNAVAILABLE
        }
        return AudioAccessSession(
            sessionId = json.getString("sessionId"),
            managedDeviceId = json.getString("deviceId"),
            status = AudioAccessSessionStatus.valueOf(json.getString("status")),
            createdAt = json.getString("createdAt"),
            authorizedAt = nullableString(json, "authorizedAt"),
            startedAt = nullableString(json, "startedAt"),
            stoppedAt = nullableString(json, "stoppedAt"),
            expiresAt = json.getString("expiresAt"),
            lastActivityAt = json.getString("lastActivityAt"),
            terminationReason = nullableString(json, "terminationReason"),
            correlationId = json.getString("correlationId"),
            transportState = transportState,
        )
    }

    private fun parseCommand(json: JSONObject) = AdminCommand(
        id = json.getString("id"),
        deviceId = json.getString("managedDeviceId"),
        type = AdminCommandType.values().firstOrNull { it.wireValue == json.optString("type") }
            ?: throw IllegalArgumentException("Unsupported command type"),
        version = json.getInt("version"),
        status = CommandStatus.values().firstOrNull { it.name == json.optString("status") }
            ?: throw IllegalArgumentException("Unsupported command status"),
        createdAt = json.getString("createdAt"),
        expiresAt = json.getString("expiresAt"),
        deliveryAt = nullableString(json, "deliveryAt"),
        acknowledgedAt = nullableString(json, "acknowledgedAt"),
        startedAt = nullableString(json, "startedAt"),
        completedAt = nullableString(json, "completedAt"),
        cancelledAt = nullableString(json, "cancelledAt"),
        failureCode = nullableString(json, "failureCode"),
        errorCategory = nullableString(json, "errorCategory"),
        resultCode = nullableString(json, "resultCode"),
    )

    private fun mapHttpError(status: Int, body: String): AdminError {
        val code = runCatching { JSONObject(body).getJSONObject("error").getString("code") }.getOrNull()
        return when {
            status == 401 -> AdminError.SessionExpired
            status == 403 -> AdminError.Authorization
            status == 404 && code == "DEVICE_NOT_FOUND" -> AdminError.DeviceNotFound("")
            status == 404 -> AdminError.Backend
            status == 409 -> AdminError.InvalidState
            status == 410 -> AdminError.ResourceGone
            status == 429 -> AdminError.RateLimited
            status >= 500 -> AdminError.ServerUnavailable
            status == 400 -> AdminError.Validation
            else -> AdminError.Backend
        }
    }

    private fun enrollmentState(value: String) = when (value) {
        "ACTIVE" -> EnrollmentState.ENROLLED
        "REVOKED" -> EnrollmentState.REVOKED
        else -> EnrollmentState.ENROLLED
    }

    private fun deviceStatus(value: String) = when (value) {
        "ACTIVE" -> DeviceStatus.AVAILABLE
        "REVOKED" -> DeviceStatus.REVOKED
        "PENDING" -> DeviceStatus.UNKNOWN
        else -> DeviceStatus.UNKNOWN
    }

    private fun connectionState(value: String) = when (value) {
        "CONNECTED" -> ConnectionState.CONNECTED
        "STALE" -> ConnectionState.RECONNECTING
        "EXPIRED", "DISCONNECTED" -> ConnectionState.DISCONNECTED
        else -> ConnectionState.ERROR
    }

    private fun freshness(value: String) = when (value) {
        "FRESH" -> MonitoringFreshness.CURRENT
        "STALE" -> MonitoringFreshness.STALE
        "VERY_STALE" -> MonitoringFreshness.VERY_STALE
        "NEVER_REPORTED" -> MonitoringFreshness.NEVER_REPORTED
        "DISCONNECTED" -> MonitoringFreshness.DISCONNECTED
        "REVOKED" -> MonitoringFreshness.REVOKED
        else -> MonitoringFreshness.UNKNOWN
    }

    private fun managementMode(value: String) = when (value) {
        "PROFILE_OWNER" -> ManagementMode.PROFILE_OWNER
        "DEVICE_OWNER" -> ManagementMode.DEVICE_OWNER
        "NOT_MANAGED" -> ManagementMode.UNMANAGED
        else -> ManagementMode.UNKNOWN
    }

    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun nullableString(json: JSONObject, key: String): String? =
        json.optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun nullableInt(json: JSONObject, key: String): Int? =
        if (json.has(key) && !json.isNull(key)) json.getInt(key) else null

    private fun nullableLong(json: JSONObject, key: String): Long? =
        if (json.has(key) && !json.isNull(key)) json.getLong(key) else null

    private data class HttpResponse(val status: Int, val body: String)

    companion object {
        private const val TIMEOUT_MS = 15_000
    }
}
