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
    private val onBack: () -> Unit,
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
        if (device == null) {
            column.addView(text("The selected managed device is no longer available."))
        }

        when (state) {
            AudioAccessUiState.Idle -> {
                column.addView(section("Audio access"))
                column.addView(text("Audio access is available for this selected device."))
                column.addView(text("Starting requires an explicit administrator action."))
                column.addView(button("Start audio access") { viewModel.start() })
            }

            AudioAccessUiState.Loading -> {
                column.addView(section("Requesting"))
                column.addView(stateText("Requesting audio access…"))
            }

            is AudioAccessUiState.Error -> {
                column.addView(section("Audio access error"))
                column.addView(stateText(state.message))
                if (state.canRetry) {
                    column.addView(button("Retry audio access") { viewModel.retry() })
                }
            }

            is AudioAccessUiState.Session -> {
                val session = state.value
                column.addView(section("Session"))
                column.addView(stateText("Session status: " + session.status.name))
                column.addView(text("Created: " + session.createdAt))
                column.addView(text("Expires: " + session.expiresAt))
                session.terminationReason?.let { column.addView(text("Termination: $it")) }
                column.addView(text("Transport state: " + session.transportState.name))
                column.addView(stateText("Playback state: " + state.playback.name))

                column.addView(section("Current state"))
                column.addView(stateText(statusDescription(session.status, state.playback)))

                if (!session.status.isTerminal) {
                    column.addView(
                        button("Stop audio access") { viewModel.stop() }.apply {
                            isEnabled = state.playback != AudioPlaybackState.STOPPING
                        },
                    )
                } else {
                    when (session.status) {
                        AudioAccessSessionStatus.EXPIRED ->
                            column.addView(text("This session expired. A new authorized session is required."))

                        AudioAccessSessionStatus.REJECTED ->
                            column.addView(text("Audio access was rejected by the backend authorization policy."))

                        AudioAccessSessionStatus.FAILED ->
                            column.addView(text("This audio session failed and cannot be resumed."))

                        AudioAccessSessionStatus.STOPPED ->
                            column.addView(text("This audio session has stopped and cannot be resumed."))

                        else -> Unit
                    }
                }

                if (session.status.isTerminal) {
                    column.addView(button("Start a new audio session") { viewModel.startNewSession() })
                } else if (state.playback == AudioPlaybackState.ERROR &&
                    session.status == AudioAccessSessionStatus.ACTIVE
                ) {
                    column.addView(button("Retry playback") { viewModel.retryPlayback() })
                }

                column.addView(button("Refresh session") { viewModel.refresh() })
            }
        }

        column.addView(button("Back to device") { onBack() })
        root.addView(ScrollView(root.context).apply { addView(column) })
    }

    private fun statusDescription(
        status: AudioAccessSessionStatus,
        playback: AudioPlaybackState,
    ): String = when (status) {
        AudioAccessSessionStatus.REQUESTED -> "Requesting access from the backend."
        AudioAccessSessionStatus.AUTHORIZED -> "Authorized; waiting for the managed device."
        AudioAccessSessionStatus.STARTING -> "The managed device is starting microphone access."
        AudioAccessSessionStatus.ACTIVE -> when (playback) {
            AudioPlaybackState.CONNECTING -> "Session is active; connecting playback."
            AudioPlaybackState.PLAYING -> "Live audio is actively playing."
            AudioPlaybackState.ERROR -> "Session is active, but the approved audio transport is unavailable."
            AudioPlaybackState.STOPPING -> "Stopping local audio playback."
            else -> "Session is active; audio playback is not currently running."
        }
        AudioAccessSessionStatus.STOPPING -> "The backend is stopping audio access."
        AudioAccessSessionStatus.STOPPED -> "Audio access has stopped."
        AudioAccessSessionStatus.EXPIRED -> "Audio access has expired."
        AudioAccessSessionStatus.FAILED -> "Audio access failed."
        AudioAccessSessionStatus.REJECTED -> "Audio access was rejected."
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

    private fun stateText(value: String) = TextView(root.context).apply {
        text = value
        textSize = 15f
        contentDescription = value
        setPadding(0, 4, 0, 4)
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
