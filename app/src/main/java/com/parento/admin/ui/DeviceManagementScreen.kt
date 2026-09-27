package com.parento.admin.ui

import com.parento.admin.BuildConfig

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.parento.admin.device.CommandStatus
import com.parento.admin.device.ManagedDeviceStatus

class DeviceManagementScreen(
    private val root: ViewGroup,
    private val viewModel: DeviceManagementViewModel,
) {
    fun renderList(state: DeviceListUiState, onOpenDevice: (String) -> Unit) {
        root.removeAllViews()
        val column = column()
        column.addView(title("Managed devices"))
        column.addView(text("Operational status and monitoring are shown from the backend's latest authoritative observation."))

        when (state) {
            DeviceListUiState.Loading -> column.addView(text("Loading managed devices…"))
            DeviceListUiState.Empty -> column.addView(text("No managed devices are available."))
            is DeviceListUiState.Error -> {
                column.addView(text(state.message))
                if (state.canRetry) column.addView(button("Retry") { viewModel.loadDevices(refresh = true) })
            }
            is DeviceListUiState.Content -> {
                state.devices.forEach { device ->
                    column.addView(deviceCard(device) { onOpenDevice(device.deviceId) })
                }
                column.addView(button("Refresh") { viewModel.loadDevices(refresh = true) })
                state.nextCursor?.let {
                    column.addView(
                        button(if (state.loadingMore) "Loading…" else "Load more") {
                            viewModel.loadMoreDevices()
                        }.apply { isEnabled = !state.loadingMore },
                    )
                }
            }
        }
        root.addView(ScrollView(root.context).apply { addView(column) })
    }

    fun renderDetail(
        state: DeviceDetailUiState,
        onBack: () -> Unit,
        onStartScreenSharing: (ManagedDeviceStatus) -> Unit,
        onStartAudioAccess: (ManagedDeviceStatus) -> Unit,
    ) {
        root.removeAllViews()
        val column = column()
        column.addView(button("Back to devices", onClick = onBack))
        column.addView(title("Device information & monitoring"))

        when (state) {
            DeviceDetailUiState.Idle -> column.addView(text("Select a managed device."))
            DeviceDetailUiState.Loading -> column.addView(text("Loading device information…"))
            DeviceDetailUiState.Refreshing -> column.addView(text("Refreshing device information…"))
            is DeviceDetailUiState.Error -> {
                column.addView(text(state.message))
                if (state.canRetry) column.addView(button("Retry") { viewModel.refreshSelectedDevice() })
            }
            is DeviceDetailUiState.Content -> {
                renderStatus(column, state.status)
                column.addView(button("Refresh status") { viewModel.refreshSelectedDevice() })
                renderCommand(column, state)
                renderScreenSharing(column, state.status, onStartScreenSharing)
                renderAudioAccess(column, state.status, onStartAudioAccess)
            }
        }
        root.addView(ScrollView(root.context).apply { addView(column) })
    }

    private fun renderStatus(column: LinearLayout, status: ManagedDeviceStatus) {
        column.addView(section("Overview"))
        column.addView(text("Name: " + status.displayName))
        column.addView(text("Managed device ID: " + status.deviceId))
        column.addView(text("Enrollment: " + status.enrollmentState.name))
        column.addView(text("Device status: " + status.deviceStatus.name))
        column.addView(text("Management mode: " + (status.monitoring?.managementMode?.let { MonitoringStatusLabels.management(it) } ?: "Unknown")))
        column.addView(statusIndicator("Communication", MonitoringStatusLabels.connection(status.connectionState)))
        column.addView(statusIndicator("Monitoring", MonitoringStatusLabels.freshness(status.monitoringFreshness)))

        column.addView(section("Connection"))
        column.addView(text("Last connected: " + MonitoringFormatters.timestamp(status.monitoring?.lastSuccessfulCommunicationAt)))
        column.addView(text("Last seen: " + MonitoringFormatters.timestamp(status.lastSeenAt)))
        column.addView(text(MonitoringFormatters.relativeAge(status.lastSeenAt)))

        column.addView(section("Android & Parento"))
        val monitoring = status.monitoring
        column.addView(text("Android version: " + (monitoring?.androidVersion ?: "Unavailable")))
        column.addView(text("API level: " + (monitoring?.apiLevel?.takeIf { it > 0 }?.toString() ?: "Unavailable")))
        column.addView(text("Parento app: " + (monitoring?.appVersion ?: "Unavailable")))
        column.addView(text("First enrolled: " + MonitoringFormatters.timestamp(status.firstEnrolledAt)))

        column.addView(section("Battery"))
        column.addView(text("Battery: " + (monitoring?.batteryPercentage?.let { "$it%" } ?: "Unavailable")))
        column.addView(text("Charging: " + (monitoring?.chargingState ?: "Unavailable")))
        column.addView(text("Battery status: " + (monitoring?.batteryStatus ?: "Unavailable")))
        column.addView(text("Telemetry: " + MonitoringFormatters.relativeAge(monitoring?.lastMonitoringUpdateAt)))

        column.addView(section("Network"))
        column.addView(text("Connection: " + (monitoring?.networkState ?: "Unavailable")))
        column.addView(text("Last successful communication: " + MonitoringFormatters.timestamp(monitoring?.lastSuccessfulCommunicationAt)))

        column.addView(section("Storage"))
        column.addView(text("Total: " + MonitoringFormatters.bytes(monitoring?.storageTotalBytes)))
        column.addView(text("Used: " + MonitoringFormatters.bytes(monitoring?.storageUsedBytes)))
        column.addView(text("Available: " + MonitoringFormatters.bytes(monitoring?.storageAvailableBytes)))

        column.addView(section("Memory"))
        column.addView(text("Total: " + MonitoringFormatters.bytes(monitoring?.memoryTotalBytes)))
        column.addView(text("Available: " + MonitoringFormatters.bytes(monitoring?.memoryAvailableBytes)))
        column.addView(text("Low-memory state: " + (monitoring?.memoryLow?.toString() ?: "Unavailable")))

        if (monitoring == null) {
            column.addView(section("Monitoring"))
            column.addView(text("No telemetry snapshot has been reported."))
        } else {
            column.addView(section("Telemetry synchronization"))
            column.addView(text("Last telemetry: " + MonitoringFormatters.timestamp(monitoring.lastMonitoringUpdateAt)))
            column.addView(text(MonitoringFormatters.relativeAge(monitoring.lastMonitoringUpdateAt)))
            monitoring.serverReceivedAt?.let {
                column.addView(text("Server received: " + MonitoringFormatters.timestamp(it)))
            }
        }
    }

    private fun renderCommand(column: LinearLayout, state: DeviceDetailUiState.Content) {
        column.addView(section("Command management"))
        column.addView(text("Existing allowlisted command type: FUTURE_COMMAND"))
        val command = state.command
        if (command == null) {
            column.addView(button("Create FUTURE_COMMAND") {
                viewModel.createFutureCommand()
            }.apply { isEnabled = !state.commandBusy })
            return
        }
        column.addView(text("Command ID: " + command.id))
        column.addView(text("Status: " + command.status.name))
        column.addView(text("Created: " + MonitoringFormatters.timestamp(command.createdAt)))
        column.addView(text("Expires: " + MonitoringFormatters.timestamp(command.expiresAt)))
        command.resultCode?.let { column.addView(text("Result: " + it)) }
        command.errorCategory?.let { column.addView(text("Error category: " + it)) }
        if (command.status !in terminalStatuses) {
            column.addView(button("Refresh command") { viewModel.refreshCommand() })
            column.addView(button("Cancel command") { viewModel.cancelCommand() })
        }
    }

    private fun renderScreenSharing(
        column: LinearLayout,
        status: ManagedDeviceStatus,
        onStart: (ManagedDeviceStatus) -> Unit,
    ) {
        column.addView(section("Screen sharing"))
        val eligible = status.enrollmentState == com.parento.admin.domain.EnrollmentState.ENROLLED &&
            status.enrollmentState != com.parento.admin.domain.EnrollmentState.REVOKED &&
            status.deviceStatus != com.parento.admin.domain.DeviceStatus.REVOKED &&
            status.connectionState == com.parento.admin.domain.ConnectionState.CONNECTED &&
            status.deviceStatus in setOf(
                com.parento.admin.domain.DeviceStatus.AVAILABLE,
                com.parento.admin.domain.DeviceStatus.CONNECTED,
            )
        column.addView(
            button(if (eligible) "Start screen sharing" else "Screen sharing unavailable") {
                onStart(status)
            }.apply { isEnabled = eligible },
        )
        column.addView(text(
            if (eligible) "Only an enrolled, authorized and connected device can start a screen-sharing session."
            else "Screen sharing requires an enrolled, non-revoked device with an active communication session."
        ))
    }

    private fun renderAudioAccess(
        column: LinearLayout,
        status: ManagedDeviceStatus,
        onStart: (ManagedDeviceStatus) -> Unit,
    ) {
        column.addView(section("Audio access"))
        val eligible = BuildConfig.PARENTO_FEATURE_AUDIO &&
            status.enrollmentState == com.parento.admin.domain.EnrollmentState.ENROLLED &&
            status.deviceStatus != com.parento.admin.domain.DeviceStatus.REVOKED &&
            status.connectionState == com.parento.admin.domain.ConnectionState.CONNECTED &&
            status.deviceStatus in setOf(
                com.parento.admin.domain.DeviceStatus.AVAILABLE,
                com.parento.admin.domain.DeviceStatus.CONNECTED,
            )
        column.addView(
            button(if (eligible) "Open audio access" else "Audio access unavailable") {
                onStart(status)
            }.apply { isEnabled = eligible && BuildConfig.PARENTO_FEATURE_AUDIO },
        )
        column.addView(
            text(
                if (eligible) "Audio access requires an enrolled, authorized and connected device. Starting is always explicit."
                else "Audio access requires an enrolled, non-revoked device with an active communication session.",
            ),
        )
    }

    private fun deviceCard(device: ManagedDeviceStatus, onClick: () -> Unit): LinearLayout =
        LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
            addView(button(device.displayName, onClick))
            addView(text("Communication: " + MonitoringStatusLabels.connection(device.connectionState)))
            addView(text("Monitoring: " + MonitoringStatusLabels.freshness(device.monitoringFreshness)))
            addView(text("Battery: " + (device.monitoring?.batteryPercentage?.let { "$it%" } ?: "Unavailable")))
            addView(text("Network: " + (device.monitoring?.networkState ?: "Unavailable")))
            addView(text("Last telemetry: " + MonitoringFormatters.relativeAge(device.monitoring?.lastMonitoringUpdateAt)))
            addView(text("Parento: " + (device.monitoring?.appVersion ?: "Unavailable")))
        }

    private fun statusIndicator(label: String, value: String) =
        text("$label: $value").apply {
            contentDescription = "$label status: $value"
        }

    private fun title(value: String) = TextView(root.context).apply {
        text = value
        textSize = 26f
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun section(value: String) = TextView(root.context).apply {
        text = value
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        val p = root.resources.getDimensionPixelSize(com.parento.admin.R.dimen.section_spacing)
        setPadding(0, p, 0, p / 2)
    }

    private fun text(value: String) = TextView(root.context).apply {
        text = value
        textSize = 15f
        setPadding(0, 4, 0, 4)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(root.context).apply {
        text = label
        minHeight = root.resources.getDimensionPixelSize(com.parento.admin.R.dimen.minimum_touch_target)
        contentDescription = label
        setOnClickListener { onClick() }
    }

    private fun column() = LinearLayout(root.context).apply {
        orientation = LinearLayout.VERTICAL
        val p = root.resources.getDimensionPixelSize(com.parento.admin.R.dimen.screen_padding)
        setPadding(p, p, p, p)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }


    private val terminalStatuses = setOf(
        CommandStatus.SUCCEEDED,
        CommandStatus.FAILED,
        CommandStatus.EXPIRED,
        CommandStatus.CANCELLED,
        CommandStatus.REJECTED,
    )
}
