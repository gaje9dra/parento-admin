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
    ): OperationResult<AudioAccessSession> =
        execute("GET", "/api/v1/audio-sessions/" + sessionId) { root ->
            parseAudioAccessSession(root.getJSONObject("data").getJSONObject("session"))
        }

    suspend fun stopAudioAccessSession(
        sessionId: String,
    ): OperationResult<AudioAccessSession> =
        execute("POST", "/api/v1/audio-sessions/" + sessionId + "/stop") { root ->
            parseAudioAccessSession(root.getJSONObject("data").getJSONObject("session"))
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


    suspend fun listApplicationInventory(deviceId: String, cursor: String? = null): OperationResult<com.parento.admin.application.ApplicationInventoryPage> {
        val path = buildString {
            append("/api/v1/admin/devices/").append(deviceId).append("/applications?limit=100")
            if (!cursor.isNullOrBlank()) append("&cursor=").append(java.net.URLEncoder.encode(cursor, "UTF-8"))
        }
        return execute("GET", path) { root ->
            val data = root.getJSONObject("data")
            val freshness = inventoryFreshness(data.optString("freshness"))
            val items = data.optJSONArray("applications") ?: org.json.JSONArray()
            val apps = buildList {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    add(com.parento.admin.application.ApplicationInventoryItem(
                        deviceId = deviceId,
                        packageName = item.getString("packageName"),
                        displayName = nullableString(item, "label"),
                        versionName = nullableString(item, "versionName"),
                        versionCode = nullableLong(item, "versionCode"),
                        installState = item.optString("installState", "UNKNOWN"),
                        enabled = if (item.has("enabled") && !item.isNull("enabled")) item.getBoolean("enabled") else null,
                        observedAt = nullableString(data, "observedAt"),
                        receivedAt = nullableString(data, "receivedAt"),
                        freshness = freshness,
                    ))
                }
            }
            com.parento.admin.application.ApplicationInventoryPage(deviceId, apps, nullableString(data, "nextCursor"), nullableString(data, "observedAt"), nullableString(data, "receivedAt"), freshness)
        }
    }

    suspend fun getApplicationInventoryItem(deviceId: String, packageName: String): OperationResult<com.parento.admin.application.ApplicationInventoryItem> =
        execute("GET", "/api/v1/admin/devices/" + deviceId + "/applications/" + java.net.URLEncoder.encode(packageName, "UTF-8")) { root ->
            val item = root.getJSONObject("data").getJSONObject("application")
            com.parento.admin.application.ApplicationInventoryItem(
                deviceId = deviceId,
                packageName = item.getString("packageName"),
                displayName = nullableString(item, "label"),
                versionName = nullableString(item, "versionName"),
                versionCode = nullableLong(item, "versionCode"),
                installState = item.optString("installState", "UNKNOWN"),
                enabled = if (item.has("enabled") && !item.isNull("enabled")) item.getBoolean("enabled") else null,
                observedAt = nullableString(item, "lastObservedAt"),
                receivedAt = nullableString(item, "lastReceivedAt"),
                freshness = com.parento.admin.application.InventoryFreshness.UNKNOWN,
            )
        }

    suspend fun requestApplicationInventory(deviceId: String): OperationResult<String> =
        execute("POST", "/api/v1/admin/devices/" + deviceId + "/applications/inventory/request") { root ->
            root.getJSONObject("data").getJSONObject("command").getString("id")
        }

    suspend fun listApplicationPolicies(cursor: String? = null): OperationResult<Pair<List<com.parento.admin.application.ApplicationPolicy>, String?>> {
        val path = buildString {
            append("/api/v1/admin/application-policies?limit=100")
            if (!cursor.isNullOrBlank()) append("&cursor=").append(java.net.URLEncoder.encode(cursor, "UTF-8"))
        }
        return execute("GET", path) { root ->
            val data = root.getJSONObject("data")
            val policies = data.optJSONArray("policies") ?: org.json.JSONArray()
            buildList<com.parento.admin.application.ApplicationPolicy> {
                for (i in 0 until policies.length()) add(parseApplicationPolicy(policies.getJSONObject(i)))
            } to nullableString(data, "nextCursor")
        }
    }

    suspend fun getApplicationPolicy(policyId: String): OperationResult<com.parento.admin.application.ApplicationPolicy> =
        execute("GET", "/api/v1/admin/application-policies/" + policyId) { root ->
            parseApplicationPolicy(root.getJSONObject("data").getJSONObject("policy"))
        }

    suspend fun createApplicationPolicy(name: String, description: String?, rules: List<com.parento.admin.application.ApplicationPolicyRule>): OperationResult<com.parento.admin.application.ApplicationPolicy> =
        execute("POST", "/api/v1/admin/application-policies", JSONObject().apply {
            put("name", name)
            if (description == null) put("description", JSONObject.NULL) else put("description", description)
            put("rules", org.json.JSONArray().apply { rules.forEach { put(JSONObject().put("packageName", it.packageName).put("action", it.action.name)) } })
        }.toString()) { root -> parseApplicationPolicy(root.getJSONObject("data").getJSONObject("policy")) }

    suspend fun updateApplicationPolicy(policy: com.parento.admin.application.ApplicationPolicy, expectedVersion: Int): OperationResult<com.parento.admin.application.ApplicationPolicy> =
        execute("PATCH", "/api/v1/admin/application-policies/" + policy.id, JSONObject().apply {
            put("name", policy.name)
            if (policy.description == null) put("description", JSONObject.NULL) else put("description", policy.description)
            put("status", policy.status.name)
            put("expectedVersion", expectedVersion)
            put("rules", org.json.JSONArray().apply { policy.rules.forEach { put(JSONObject().put("packageName", it.packageName).put("action", it.action.name)) } })
        }.toString()) { root -> parseApplicationPolicy(root.getJSONObject("data").getJSONObject("policy")) }

    suspend fun getApplicationPolicyState(deviceId: String): OperationResult<com.parento.admin.application.ApplicationPolicyState> =
        execute("GET", "/api/v1/admin/devices/" + deviceId + "/application-policy") { root -> parseApplicationPolicyState(root.getJSONObject("data")) }

    suspend fun assignApplicationPolicy(deviceId: String, policyId: String): OperationResult<com.parento.admin.application.ApplicationPolicyState> =
        execute("POST", "/api/v1/admin/devices/" + deviceId + "/application-policy", JSONObject().put("policyId", policyId).toString()) { root -> parseApplicationPolicyState(root.getJSONObject("data")) }

    suspend fun removeApplicationPolicy(deviceId: String, policyId: String): OperationResult<com.parento.admin.application.ApplicationPolicyState> =
        execute("DELETE", "/api/v1/admin/devices/" + deviceId + "/application-policy", JSONObject().put("policyId", policyId).toString()) { root -> parseApplicationPolicyState(root.getJSONObject("data")) }

    suspend fun syncApplicationPolicy(deviceId: String): OperationResult<com.parento.admin.application.ApplicationSynchronization?> =
        execute("POST", "/api/v1/admin/devices/" + deviceId + "/application-policy/sync") { root ->
            parseSynchronization(root.getJSONObject("data").optJSONObject("synchronization"))
        }

    suspend fun getApplicationEnforcementStatus(deviceId: String): OperationResult<com.parento.admin.application.ApplicationSynchronization?> =
        execute("GET", "/api/v1/admin/devices/" + deviceId + "/application-policy/status") { root ->
            parseSynchronization(root.getJSONObject("data").optJSONObject("synchronization"))
        }

    private fun parseApplicationPolicy(json: JSONObject): com.parento.admin.application.ApplicationPolicy {
        val rules = json.optJSONArray("rules") ?: org.json.JSONArray()
        return com.parento.admin.application.ApplicationPolicy(
            id = json.getString("id"),
            name = json.getString("name"),
            description = nullableString(json, "description"),
            status = com.parento.admin.application.PolicyStatus.valueOf(json.optString("status", "ACTIVE")),
            version = json.optInt("version", 1),
            createdAt = json.optString("createdAt", ""),
            updatedAt = json.optString("updatedAt", ""),
            createdBy = nullableString(json, "createdBy"),
            updatedBy = nullableString(json, "updatedBy"),
            rules = buildList {
                for (i in 0 until rules.length()) {
                    val r = rules.getJSONObject(i)
                    add(com.parento.admin.application.ApplicationPolicyRule(r.getString("packageName"), com.parento.admin.application.PolicyAction.valueOf(r.getString("action"))))
                }
            },
        )
    }

    private fun parseApplicationPolicyState(data: JSONObject): com.parento.admin.application.ApplicationPolicyState {
        val policy = data.optJSONObject("policy")?.let(::parseApplicationPolicy)
        val assignmentJson = data.optJSONObject("assignment")
        val assignment = assignmentJson?.let {
            com.parento.admin.application.PolicyAssignment(
                deviceId = it.optString("deviceId"),
                policyId = nullableString(it, "policyId"),
                policyVersion = if (it.has("policyVersion") && !it.isNull("policyVersion")) it.getInt("policyVersion") else null,
                assignedAt = nullableString(it, "assignedAt"),
                updatedAt = nullableString(it, "updatedAt"),
            )
        }
        val sync = parseSynchronization(data.optJSONObject("synchronization"))
        return com.parento.admin.application.ApplicationPolicyState(policy, assignment, sync)
    }

    private fun parseSynchronization(json: JSONObject?): com.parento.admin.application.ApplicationSynchronization? =
        json?.let {
            com.parento.admin.application.ApplicationSynchronization(
                desiredPolicyId = nullableString(it, "desiredPolicyId"),
                desiredPolicyVersion = if (it.has("desiredPolicyVersion") && !it.isNull("desiredPolicyVersion")) it.getInt("desiredPolicyVersion") else null,
                reportedPolicyId = nullableString(it, "reportedPolicyId"),
                reportedPolicyVersion = if (it.has("reportedPolicyVersion") && !it.isNull("reportedPolicyVersion")) it.getInt("reportedPolicyVersion") else null,
                status = com.parento.admin.application.EnforcementStatus.valueOf(it.optString("status", "UNKNOWN")),
                lastRequestedAt = nullableString(it, "lastRequestedAt"),
                lastReportedAt = nullableString(it, "lastReportedAt"),
                updatedAt = nullableString(it, "updatedAt"),
                errorCode = nullableString(it, "errorCode"),
            )
        }

    private fun inventoryFreshness(value: String) = when (value) {
        "FRESH" -> com.parento.admin.application.InventoryFreshness.FRESH
        "STALE" -> com.parento.admin.application.InventoryFreshness.STALE
        "VERY_STALE" -> com.parento.admin.application.InventoryFreshness.VERY_STALE
        "NEVER_REPORTED" -> com.parento.admin.application.InventoryFreshness.NEVER_REPORTED
        "DISCONNECTED" -> com.parento.admin.application.InventoryFreshness.DISCONNECTED
        "REVOKED" -> com.parento.admin.application.InventoryFreshness.REVOKED
        else -> com.parento.admin.application.InventoryFreshness.UNKNOWN
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
        val details = linkedMapOf<String, String>()
        transport?.keys()?.forEach { key ->
            if (!transport.isNull(key)) {
                val value = transport.optString(key)
                if (value.length <= 128) details[key] = value
            }
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
            transportState = details,
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
