package com.parento.admin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.parento.admin.R
import com.parento.admin.domain.EnrollmentSessionStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EnrollmentScreen(
    private val root: FrameLayout,
    private val viewModel: EnrollmentViewModel,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault())

    fun render(state: EnrollmentUiState) {
        root.removeAllViews()
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
            text = root.context.getString(R.string.enrollment_title)
            textSize = 28f
        })
        content.addView(MaterialTextView(root.context).apply {
            text = root.context.getString(R.string.enrollment_subtitle)
            textSize = 16f
        })
        when (state) {
            EnrollmentUiState.Ready -> addAction(content, R.string.create_enrollment) { viewModel.createEnrollment() }
            EnrollmentUiState.Creating -> addMessage(content, root.context.getString(R.string.enrollment_creating))
            is EnrollmentUiState.Active -> renderActive(content, state)
            is EnrollmentUiState.Completed -> renderCompleted(content, state.enrollment)
            is EnrollmentUiState.Terminal -> renderTerminal(content, state.enrollment)
            is EnrollmentUiState.Error -> renderError(content, state)
        }
        root.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun renderActive(container: LinearLayout, state: EnrollmentUiState.Active) {
        addStatus(container, state.enrollment.status)
        addMessage(container, root.context.getString(R.string.enrollment_waiting))
        addMessage(container, root.context.getString(R.string.enrollment_expires, formatter.format(state.enrollment.expiresAt)))
        state.authorizationSecret?.let { secret ->
            addMessage(container, root.context.getString(R.string.enrollment_secret_warning))
            container.addView(TextView(root.context).apply {
                text = secret
                setTextIsSelectable(true)
                contentDescription = root.context.getString(R.string.enrollment_secret_content_description)
                textSize = 20f
                setPadding(0, 16, 0, 16)
            })
            addAction(container, R.string.copy_enrollment_code) { copySecret(secret) }
        }
        addAction(container, R.string.refresh_enrollment) { viewModel.refresh() }
        addAction(container, R.string.cancel_enrollment) { viewModel.cancelEnrollment() }
    }

    private fun renderCompleted(container: LinearLayout, enrollment: com.parento.admin.domain.EnrollmentSession) {
        addStatus(container, enrollment.status)
        addMessage(container, root.context.getString(R.string.enrollment_completed))
        enrollment.managedDeviceId?.let { addMessage(container, root.context.getString(R.string.managed_device_id, it)) }
        addAction(container, R.string.refresh_enrollment) { viewModel.refresh() }
    }

    private fun renderTerminal(container: LinearLayout, enrollment: com.parento.admin.domain.EnrollmentSession) {
        addStatus(container, enrollment.status)
        addMessage(container, terminalMessage(enrollment.status))
        addAction(container, R.string.create_enrollment) { viewModel.createEnrollment() }
    }

    private fun renderError(container: LinearLayout, state: EnrollmentUiState.Error) {
        addMessage(container, state.message)
        state.enrollment?.let {
            addStatus(container, it.status)
            addAction(container, R.string.refresh_enrollment) { viewModel.refresh() }
            if (it.status.isTerminal) addAction(container, R.string.create_enrollment) { viewModel.createEnrollment() }
        } ?: addAction(container, R.string.create_enrollment) { viewModel.createEnrollment() }
    }

    private fun addStatus(container: LinearLayout, status: EnrollmentSessionStatus) =
        addMessage(container, root.context.getString(R.string.enrollment_status, status.name))

    private fun addMessage(container: LinearLayout, message: String) {
        container.addView(MaterialTextView(root.context).apply {
            text = message
            textSize = 16f
            setPadding(0, 12, 0, 12)
        })
    }

    private fun addAction(container: LinearLayout, label: Int, action: () -> Unit) {
        container.addView(MaterialButton(root.context).apply {
            text = root.context.getString(label)
            minHeight = resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
            contentDescription = root.context.getString(label)
            setOnClickListener { action() }
        })
    }

    private fun copySecret(secret: String) {
        val clipboard = root.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Parento enrollment authorization", secret))
        handler.postDelayed({
            if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.label == "Parento enrollment authorization") {
                clipboard.clearPrimaryClip()
            }
        }, CLIPBOARD_CLEAR_DELAY_MS)
    }

    private fun terminalMessage(status: EnrollmentSessionStatus): String = when (status) {
        EnrollmentSessionStatus.EXPIRED -> root.context.getString(R.string.enrollment_expired)
        EnrollmentSessionStatus.CANCELLED -> root.context.getString(R.string.enrollment_cancelled)
        EnrollmentSessionStatus.REVOKED -> root.context.getString(R.string.enrollment_revoked)
        EnrollmentSessionStatus.FAILED -> root.context.getString(R.string.enrollment_failed)
        else -> root.context.getString(R.string.enrollment_terminal)
    }

    companion object { private const val CLIPBOARD_CLEAR_DELAY_MS = 60_000L }
}
