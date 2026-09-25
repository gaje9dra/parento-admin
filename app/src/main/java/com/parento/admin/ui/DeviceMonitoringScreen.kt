package com.parento.admin.ui

import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.parento.admin.R
import com.parento.admin.domain.DeviceFreshness
import com.parento.admin.domain.DeviceMonitoringFormatting
import com.parento.admin.domain.ManagedDeviceMonitoring
import com.parento.admin.domain.ManagementMode
import com.parento.admin.domain.NetworkState
import com.parento.admin.domain.NetworkTransport

class DeviceMonitoringScreen(
    private val root: android.widget.FrameLayout,
    private val viewModel: DeviceMonitoringViewModel,
    private val onBack: () -> Unit,
    private val onDeviceSelected: (String) -> Unit,
) {
    fun render(state: DeviceMonitoringUiState) {
        root.removeAllViews()
        val scroll = ScrollView(root.context)
        val content = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.screen_padding),
                resources.getDimensionPixelSize(R.dimen.screen_padding),
                resources.getDimensionPixelSize(R.dimen.screen_padding),
                resources.getDimensionPixelSize(R.dimen.screen_padding),
            )
        }
        content.addView(MaterialTextView(root.context).apply {
            text = "Device monitoring"
            textSize = 28f
        })
        content.addView(MaterialTextView(root.context).apply {
            text = "Operational device information. Server freshness is authoritative; unavailable values are never fabricated."
            textSize = 16f
        })
        content.addView(MaterialButton(root.context).apply {
            text = "Refresh"
            minHeight = resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
            contentDescription = "Refresh device monitoring"
            setOnClickListener { viewModel.refresh() }
        })
        when (state) {
            DeviceMonitoringUiState.Loading -> content.addView(AdminStateViews.loading(root.context))
            DeviceMonitoringUiState.Empty -> content.addView(stateCard("No managed devices", "The backend returned no authorized managed devices."))
            is DeviceMonitoringUiState.Content -> renderList(content, state.devices)
            is DeviceMonitoringUiState.Stale -> {
                content.addView(stateCard("Stale monitoring data", "One or more devices have telemetry older than the backend freshness threshold."))
                renderList(content, state.devices)
            }
            is DeviceMonitoringUiState.Offline -> content.addView(stateCard("Monitoring unavailable", state.message))
            is DeviceMonitoringUiState.Unauthorized -> content.addView(stateCard("Unauthorized", state.message))
            is DeviceMonitoringUiState.SessionExpired -> content.addView(stateCard("Session expired", state.message))
            is DeviceMonitoringUiState.UnavailableDependency -> content.addView(stateCard("Backend dependency unavailable", state.message))
            is DeviceMonitoringUiState.Error -> content.addView(stateCard("Monitoring error", state.message))
        }
        content.addView(MaterialButton(root.context).apply {
            text = "Back"
            minHeight = resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
            setOnClickListener { onBack() }
        })
        scroll.addView(content)
        root.addView(scroll)
    }

    private fun renderList(container: LinearLayout, devices: List<ManagedDeviceMonitoring>) {
        devices.forEach { monitoring ->
            val device = monitoring.device
            val card = MaterialCardView(root.context).apply {
                isClickable = true
                isFocusable = true
                contentDescription = "Open monitoring details for ${device.displayName}"
                setOnClickListener { onDeviceSelected(device.deviceId) }
            }
            val body = LinearLayout(root.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.card_padding),
                    resources.getDimensionPixelSize(R.dimen.card_padding),
                    resources.getDimensionPixelSize(R.dimen.card_padding),
                    resources.getDimensionPixelSize(R.dimen.card_padding),
                )
            }
            body.addView(label(device.displayName, 20f, true))
            body.addView(label("Enrollment: ${device.enrollmentState}", 14f))
            body.addView(label("Communication: ${device.connectionState}", 14f))
            body.addView(label("Management: ${managementLabel(monitoring.managementMode)}", 14f))
            body.addView(label("Battery: ${DeviceMonitoringFormatting.percentage(monitoring.batteryPercent)}", 14f))
            body.addView(label("Network: ${networkLabel(monitoring)}", 14f))
            body.addView(label("Freshness: ${freshnessLabel(monitoring.freshness)}", 14f))
            body.addView(label("Last seen: ${DeviceMonitoringFormatting.timestamp(monitoring.lastSeenAtEpochMillis)}", 14f))
            card.addView(body)
            container.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = resources.getDimensionPixelSize(R.dimen.section_spacing) })
        }
    }

    fun renderDetail(monitoring: ManagedDeviceMonitoring) {
        root.removeAllViews()
        val scroll = ScrollView(root.context)
        val content = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.screen_padding),
                resources.getDimensionPixelSize(R.dimen.screen_padding),
                resources.getDimensionPixelSize(R.dimen.screen_padding),
                resources.getDimensionPixelSize(R.dimen.screen_padding),
            )
        }
        content.addView(label(monitoring.device.displayName, 28f, true))
        section(content, "Identity", listOf(
            "Device ID" to monitoring.device.deviceId,
            "Enrollment" to monitoring.device.enrollmentState.toString(),
            "Management mode" to managementLabel(monitoring.managementMode),
        ))
        section(content, "Connection", listOf(
            "Communication" to monitoring.device.connectionState.toString(),
            "Freshness" to freshnessLabel(monitoring.freshness),
            "Last seen" to DeviceMonitoringFormatting.timestamp(monitoring.lastSeenAtEpochMillis),
            "Last connected" to DeviceMonitoringFormatting.timestamp(monitoring.lastConnectedAtEpochMillis),
            "Last telemetry" to DeviceMonitoringFormatting.timestamp(monitoring.lastTelemetryAtEpochMillis),
        ))
        section(content, "Android", listOf(
            "Android version" to (monitoring.androidVersion ?: "Unavailable"),
            "API level" to (monitoring.apiLevel?.toString() ?: "Unavailable"),
            "Parento app" to (monitoring.appVersion ?: "Unavailable"),
            "Management mode" to managementLabel(monitoring.managementMode),
        ))
        section(content, "Battery", listOf(
            "Level" to DeviceMonitoringFormatting.percentage(monitoring.batteryPercent),
            "Charging" to (monitoring.isCharging?.let { if (it) "Charging" else "Not charging" } ?: "Unavailable"),
            "Observed" to DeviceMonitoringFormatting.timestamp(monitoring.batteryObservedAtEpochMillis),
        ))
        section(content, "Network", listOf(
            "State" to networkLabel(monitoring),
            "Transport" to monitoring.networkTransport.toString(),
            "Last successful communication" to DeviceMonitoringFormatting.timestamp(monitoring.lastSuccessfulSyncAtEpochMillis),
        ))
        section(content, "Storage", listOf(
            "Total" to DeviceMonitoringFormatting.bytes(monitoring.storageTotalBytes),
            "Used" to DeviceMonitoringFormatting.bytes(monitoring.storageUsedBytes),
            "Available" to DeviceMonitoringFormatting.bytes(monitoring.storageAvailableBytes),
            "Used percentage" to (DeviceMonitoringFormatting.storageUsedPercent(monitoring.storageTotalBytes, monitoring.storageUsedBytes)?.toString()?.plus("%") ?: "Unavailable"),
        ))
        section(content, "Memory", listOf(
            "Total" to DeviceMonitoringFormatting.bytes(monitoring.memoryTotalBytes),
            "Available" to DeviceMonitoringFormatting.bytes(monitoring.memoryAvailableBytes),
            "Observed" to DeviceMonitoringFormatting.timestamp(monitoring.memoryObservedAtEpochMillis),
        ))
        content.addView(MaterialButton(root.context).apply {
            text = "Back"
            minHeight = resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
            setOnClickListener { onBack() }
        })
        scroll.addView(content)
        root.addView(scroll)
    }

    private fun section(container: LinearLayout, title: String, rows: List<Pair<String, String>>) {
        container.addView(label(title, 20f, true))
        rows.forEach { (key, value) -> container.addView(label("$key: $value", 15f)) }
    }

    private fun stateCard(title: String, message: String): View = MaterialCardView(root.context).apply {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.card_padding),
                resources.getDimensionPixelSize(R.dimen.card_padding),
                resources.getDimensionPixelSize(R.dimen.card_padding),
                resources.getDimensionPixelSize(R.dimen.card_padding),
            )
            addView(label(title, 20f, true))
            addView(label(message, 15f))
        }
        addView(body)
    }

    private fun label(text: String, size: Float, bold: Boolean = false): TextView =
        MaterialTextView(root.context).apply {
            this.text = text
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, resources.getDimensionPixelSize(R.dimen.item_spacing), 0, resources.getDimensionPixelSize(R.dimen.item_spacing))
        }

    private fun freshnessLabel(value: DeviceFreshness): String = when (value) {
        DeviceFreshness.FRESH -> "Fresh"
        DeviceFreshness.STALE -> "Stale"
        DeviceFreshness.VERY_STALE -> "Very stale"
        DeviceFreshness.NEVER_REPORTED -> "Never reported"
        DeviceFreshness.OFFLINE -> "Offline"
        DeviceFreshness.REVOKED -> "Revoked"
        DeviceFreshness.UNKNOWN -> "Unknown"
    }

    private fun managementLabel(value: ManagementMode): String = when (value) {
        ManagementMode.UNMANAGED -> "Unmanaged"
        ManagementMode.PROFILE_OWNER -> "Profile Owner"
        ManagementMode.DEVICE_OWNER -> "Device Owner"
        ManagementMode.UNKNOWN -> "Unknown"
    }

    private fun networkLabel(value: ManagedDeviceMonitoring): String = when (value.networkState) {
        NetworkState.ONLINE -> when (value.networkTransport) {
            NetworkTransport.WIFI -> "Online · Wi-Fi"
            NetworkTransport.CELLULAR -> "Online · Cellular"
            NetworkTransport.UNKNOWN -> "Online"
        }
        NetworkState.OFFLINE -> "Offline"
        NetworkState.UNKNOWN -> "Unknown"
    }
}
