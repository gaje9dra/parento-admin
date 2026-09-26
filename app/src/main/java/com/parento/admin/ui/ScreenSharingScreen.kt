package com.parento.admin.ui

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.parento.admin.screensharing.ScreenSharingSessionStatus

class ScreenSharingScreen(
    private val root: ViewGroup,
    private val viewModel: ScreenSharingViewModel,
    private val onBack: () -> Unit,
) {
    fun render(state: ScreenSharingUiState) {
        root.removeAllViews()
        val column = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            val p = resources.getDimensionPixelSize(com.parento.admin.R.dimen.screen_padding)
            setPadding(p, p, p, p)
        }
        column.addView(button("Back to device", onBack))
        column.addView(title("Screen sharing"))

        when (state) {
            ScreenSharingUiState.Idle -> column.addView(text("Select a managed device to request screen sharing."))
            ScreenSharingUiState.Starting -> {
                column.addView(text("Requesting an authorized screen-sharing session…"))
                column.addView(text("The viewer will become available only after the backend reports ACTIVE."))
            }
            is ScreenSharingUiState.Error -> {
                column.addView(text(state.message))
                if (state.canRetry) column.addView(button("Try again") { viewModel.start() })
            }
            is ScreenSharingUiState.Session -> renderSession(column, state.value)
        }

        root.addView(ScrollView(root.context).apply { addView(column) })
    }

    private fun renderSession(column: LinearLayout, session: com.parento.admin.screensharing.ScreenSharingSession) {
        column.addView(section("Session"))
        column.addView(text("Session ID: " + session.sessionId))
        column.addView(text("Managed device: " + session.managedDeviceId))
        column.addView(text("Status: " + session.status.name))
        column.addView(text("Created: " + session.createdAt))
        column.addView(text("Expires: " + session.expiresAt))
        session.authorizedAt?.let { column.addView(text("Authorized: $it")) }
        session.startedAt?.let { column.addView(text("Started: $it")) }
        session.stoppedAt?.let { column.addView(text("Stopped: $it")) }
        session.terminationReason?.let { column.addView(text("Termination: $it")) }

        column.addView(section("Transport"))
        column.addView(text("Transport: " + session.transportState.name))
        column.addView(text("Screen frames are not available because Phase 9.1 exposes lifecycle/signaling only; no media-frame transport contract is implemented."))

        if (session.status == ScreenSharingSessionStatus.ACTIVE) {
            column.addView(section("Viewer"))
            column.addView(text("Session is ACTIVE, but no approved frame transport is exposed by the current backend contract. The viewer therefore remains blank rather than fabricating or showing stale content."))
        } else {
            column.addView(section("Viewer"))
            column.addView(text(statusMessage(session.status)))
        }

        if (!session.status.isTerminal) {
            column.addView(button("Refresh session") { viewModel.refresh() })
            column.addView(button("Stop screen sharing") { viewModel.stop() })
        }
    }

    private fun statusMessage(status: ScreenSharingSessionStatus): String = when (status) {
        ScreenSharingSessionStatus.REQUESTED -> "Screen-sharing request is pending."
        ScreenSharingSessionStatus.AUTHORIZED -> "Screen-sharing request is authorized and waiting to start."
        ScreenSharingSessionStatus.STARTING -> "Managed Android is starting screen capture."
        ScreenSharingSessionStatus.ACTIVE -> "Screen sharing is active."
        ScreenSharingSessionStatus.STOPPING -> "Screen sharing is stopping."
        ScreenSharingSessionStatus.STOPPED -> "Screen sharing has stopped."
        ScreenSharingSessionStatus.EXPIRED -> "The screen-sharing session has expired."
        ScreenSharingSessionStatus.FAILED -> "The screen-sharing session failed."
        ScreenSharingSessionStatus.REJECTED -> "The screen-sharing request was rejected."
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
}
