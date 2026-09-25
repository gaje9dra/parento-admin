package com.parento.admin.ui

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.parento.admin.device.CommandStatus
import com.parento.admin.device.ManagedDeviceStatus
import com.parento.admin.device.ManagementMode
import com.parento.admin.device.MonitoringFreshness
import com.parento.admin.domain.ConnectionState
import com.parento.admin.domain.DeviceStatus
import java.util.Locale

class DeviceManagementScreen(
    private val root: ViewGroup,
    private val viewModel: DeviceManagementViewModel,
) {
    fun renderList(state: DeviceListUiState, onOpenDevice: (String) -> Unit) {
        root.removeAllViews()
        val column = column()
        column.addView(title("Managed devices"))
        column.addView(text("Refreshes are explicit and lifecycle-scoped. The current backend exposes enrolled-device references rather than a canonical device-list endpoint."))

        when (state) {
            DeviceListUiState.Loading -> column.addView(text("Loading managed devices…"))
            DeviceListUiState.Empty -> column.addView(text("No completed enrollments with a managed-device ID are available."))
            is DeviceListUiState.Error -> {
                column.addView(text(state.message))
                if (state.canRetry) column.addView(button("Retry") { viewModel.loadDevices(refresh = true) })
            }
            is DeviceListUiState.Content -> {
                state.devices.forEach { device ->
                    column.addView(deviceCard(device) { onOpenDevice(device.deviceId) })
                }
                column.addView(button("Refresh") { viewModel.loadDevices(refresh = true) })
            }
        }

        val idInput = EditText(root.context).apply {
            hint = "Managed device ID (UUID)"
            singleLine = true
        }
        column.addView(idInput)
        column.addView(button("Open device") {
            onOpenDevice(idInput.text.toString())
        })
        root.addView(ScrollView(root.context).apply { addView(column) })
    }

    fun renderDetail(
        state: DeviceDetailUiState,
        onBack: () -> Unit,
    ) {
        root.removeAllViews()
        val column = column()
        column.addView(button("Back to devices", onClick = onBack))
        column.addView(title("Device status"))

        when (state) {
            DeviceDetailUiState.Idle -> column.addView(text("Select a managed device."))
            DeviceDetailUiState.Loading -> column.addView(text("Loading device status…"))
            DeviceDetailUiState.Refreshing -> column.addView(text("Refreshing device status…"))
            is DeviceDetailUiState.Error -> {
                column.addView(text(state.message))
                if (state.canRetry) column.addView(button("Retry") { viewModel.refreshSelectedDevice() })
            }
            is DeviceDetailUiState.Content -> {
                renderStatus(column, state.status)
                column.addView(button("Refresh status") { viewModel.refreshSelectedDevice() })
                renderCommand(column, state)
            }
        }
        root.addView(ScrollView(root.context).apply { addView(column) })
    }

    private fun renderStatus(column: LinearLayout, status: ManagedDeviceStatus) {
        column.addView(section("Identity"))
        column.addView(text("Name: " + status.displayName))
        column.addView(text("Managed device ID: " + status.deviceId))

        column.addView(section("Enrollment and management"))
        column.addView(text("Enrollment: " + status.enrollmentState.name))
        column.addView(text("Device status: " + status.deviceStatus.name))
        column.addView(text("Connection: " + connectionLabel(status.connectionState)))
        column.addView(text("Last seen: " + (status.lastSeenAt ?: "Unavailable")))
        column.addView(text("Monitoring freshness: " + freshnessLabel(status.monitoringFreshness)))

        val monitoring = status.monitoring
        if (monitoring == null) {
            column.addView(section("Monitoring"))
            column.addView(text("Monitoring snapshot unavailable."))
            return
        }

        column.addView(section("Device information"))
        column.addView(text("Android: " + monitoring.androidVersion + " (API " + monitoring.apiLevel + ")"))
        column.addView(text("Parento app: " + monitoring.appVersion + " (" + monitoring.appVersionCode + ")"))
        column.addView(text("Management mode: " + monitoring.managementMode.name))

        column.addView(section("Health"))
        column.addView(text("Battery: " + (monitoring.batteryPercentage?.let { it.toString() + "%" } ?: "Unavailable")))
        column.addView(text("Charging: " + monitoring.chargingState))
        column.addView(text("Battery status: " + monitoring.batteryStatus))
        column.addView(text("Network: " + monitoring.networkState))
        column.addView(text("Storage: " + formatBytes(monitoring.storageAvailableBytes) + " available / " + formatBytes(monitoring.storageTotalBytes)))
        column.addView(text("Memory: " + formatBytes(monitoring.memoryAvailableBytes) + " available / " + formatBytes(monitoring.memoryTotalBytes)))
        column.addView(text("Low memory: " + (monitoring.memoryLow?.toString() ?: "Unknown")))
        column.addView(text("Last monitoring update: " + monitoring.lastMonitoringUpdateAt))
    }

    private fun renderCommand(column: LinearLayout, state: DeviceDetailUiState.Content) {
        column.addView(section("Command management"))
        column.addView(text("Allowlisted command type: FUTURE_COMMAND"))
        column.addView(text("No arbitrary command text or executable instructions are accepted."))
        val command = state.command
        if (command == null) {
            column.addView(button("Create FUTURE_COMMAND") {
                viewModel.createFutureCommand()
            }.apply { isEnabled = !state.commandBusy })
            return
        }

        column.addView(text("Command ID: " + command.id))
        column.addView(text("Status: " + command.status.name))
        column.addView(text("Created: " + command.createdAt))
        column.addView(text("Expires: " + command.expiresAt))
        command.resultCode?.let { column.addView(text("Result: " + it)) }
        command.errorCategory?.let { column.addView(text("Error category: " + it)) }

        if (command.status !in terminalStatuses) {
            column.addView(button("Refresh command") { viewModel.refreshCommand() })
            column.addView(button("Cancel command") { viewModel.cancelCommand() })
        }
    }

    private fun deviceCard(device: ManagedDeviceStatus, onClick: () -> Unit) =
        button(
            device.displayName + " • " + device.connectionState.name +
                " • " + device.monitoringFreshness.name,
            onClick,
        )

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

    private fun connectionLabel(value: ConnectionState) = when (value) {
        ConnectionState.CONNECTED -> "Connected"
        ConnectionState.DISCONNECTED -> "Disconnected / recently seen"
        ConnectionState.CONNECTING -> "Connecting"
        ConnectionState.ERROR -> "Unavailable"
    }

    private fun freshnessLabel(value: MonitoringFreshness) = when (value) {
        MonitoringFreshness.CURRENT -> "Current"
        MonitoringFreshness.STALE -> "Stale"
        MonitoringFreshness.UNKNOWN -> "Unknown"
    }

    private fun formatBytes(value: Long?): String {
        if (value == null) return "Unavailable"
        if (value < 1024L) return value.toString() + " B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var amount = value.toDouble()
        var index = -1
        while (amount >= 1024 && index < units.lastIndex) {
            amount /= 1024
            index++
        }
        return String.format(Locale.US, "%.1f %s", amount, units[index])
    }

    private val terminalStatuses = setOf(
        CommandStatus.SUCCEEDED,
        CommandStatus.FAILED,
        CommandStatus.EXPIRED,
        CommandStatus.CANCELLED,
        CommandStatus.REJECTED,
    )
}
