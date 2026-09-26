package com.parento.admin.ui

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.parento.admin.audio.AudioAccessSessionStatus
import com.parento.admin.audio.AudioPlaybackState
import com.parento.admin.device.ManagedDeviceStatus

class AudioAccessScreen(
    private val root: ViewGroup,
    private val viewModel: AudioAccessViewModel,
) {
    fun render(state: AudioAccessUiState, device: ManagedDeviceStatus?) {
        root.removeAllViews()
        val column = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            val p = root.resources.getDimensionPixelSize(com.parento.admin.R.dimen.screen_padding)
            setPadding(p, p, p, p)
        }

        column.addView(title("Live audio access"))
        column.addView(text("Device: " + (device?.displayName ?: "Unavailable")))
        device?.let {
            column.addView(text("Managed device ID: " + it.deviceId))
        }

        when (state) {
            AudioAccessUiState.Idle -> {
                column.addView(section("Audio access"))
                column.addView(text("Audio access available"))
                column.addView(text("Starting requires an explicit administrator action."))
                column.addView(button("Start audio access") { viewModel.start() })
            }
            AudioAccessUiState.Loading -> {
                column.addView(section("Starting"))
                column.addView(text("Requesting audio access…"))
            }
            is AudioAccessUiState.Error -> {
                column.addView(section("Error"))
                column.addView(text(state.message))
                if (state.canRetry) column.addView(button("Retry") { viewModel.start() })
            }
            is AudioAccessUiState.Session -> {
                val session = state.value
                column.addView(section("Session"))
                column.addView(text("Session status: " + session.status.name))
                column.addView(text("Created: " + session.createdAt))
                column.addView(text("Expires: " + session.expiresAt))
                session.terminationReason?.let { column.addView(text("Termination: $it")) }
                column.addView(text("Transport state: " + (session.transportState["state"] ?: "Unavailable")))
                column.addView(text("Playback state: " + state.playback.name))

                if (session.status == AudioAccessSessionStatus.ACTIVE) {
                    column.addView(
                        text(
                            when (state.playback) {
                                AudioPlaybackState.PLAYING -> "Audio active"
                                AudioPlaybackState.CONNECTING -> "Connecting to device…"
                                AudioPlaybackState.ERROR -> "Audio transport/playback is unavailable."
                                else -> "Session active; waiting for transport."
                            },
                        ),
                    )
                } else if (!session.status.isTerminal) {
                    column.addView(
                        text(
                            when (session.status) {
                                AudioAccessSessionStatus.REQUESTED -> "Requesting audio access…"
                                AudioAccessSessionStatus.AUTHORIZED -> "Authorized; waiting for managed device."
                                AudioAccessSessionStatus.STARTING -> "Starting microphone access on the managed device…"
                                AudioAccessSessionStatus.STOPPING -> "Stopping audio access…"
                                else -> "Waiting for session state…"
                            },
                        ),
                    )
                }

                if (!session.status.isTerminal) {
                    column.addView(button("Stop audio access") { viewModel.stop() })
                } else if (session.status == AudioAccessSessionStatus.EXPIRED) {
                    column.addView(text("This session expired. Start a new authorized session to try again."))
                }
                column.addView(button("Refresh session") { viewModel.refresh() })
            }
        }

        column.addView(button("Back") { viewModel.clearDevice() })
        root.addView(ScrollView(root.context).apply { addView(column) })
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
        contentDescription = label
        minHeight = root.resources.getDimensionPixelSize(com.parento.admin.R.dimen.minimum_touch_target)
        setOnClickListener { onClick() }
    }
}
