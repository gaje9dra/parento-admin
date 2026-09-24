package com.parento.admin.ui

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.parento.admin.R
import com.parento.admin.auth.AuthenticatedAdmin
import com.parento.admin.navigation.AdminDestination

class AdminHomeScreen(
    private val root: FrameLayout,
    private val viewModel: AdminHomeViewModel,
    private val admin: AuthenticatedAdmin,
    private val onNavigate: (AdminDestination) -> Unit,
) {
    fun render(state: AdminUiState) {
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
            text = root.context.getString(R.string.dashboard_title)
            textSize = 28f
        })
        content.addView(MaterialTextView(root.context).apply {
            text = root.context.getString(R.string.dashboard_subtitle)
            textSize = 16f
            setPadding(0, resources.getDimensionPixelSize(R.dimen.item_spacing), 0, resources.getDimensionPixelSize(R.dimen.section_spacing))
        })

        renderAdminProfile(content, admin)

        when (state) {
            AdminUiState.Loading -> content.addView(AdminStateViews.loading(root.context))
            AdminUiState.Empty -> content.addView(AdminStateViews.empty(root.context))
            is AdminUiState.Content -> renderContent(content, state)
            is AdminUiState.Error -> content.addView(
                AdminStateViews.error(root.context, state.message, state.canRetry) {
                    viewModel.showEmpty()
                },
            )
        }

        content.addView(navigationButton(AdminDestination.DEVICES, R.string.nav_devices))
        content.addView(navigationButton(AdminDestination.POLICIES, R.string.nav_policies))
        content.addView(navigationButton(AdminDestination.SETTINGS, R.string.nav_settings))

        root.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ))
    }

    private fun renderAdminProfile(container: LinearLayout, admin: AuthenticatedAdmin) {
        val profile = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.card_padding),
                resources.getDimensionPixelSize(R.dimen.card_padding),
                resources.getDimensionPixelSize(R.dimen.card_padding),
                resources.getDimensionPixelSize(R.dimen.card_padding),
            )
        }
        profile.addView(MaterialTextView(root.context).apply {
            text = root.context.getString(R.string.admin_profile_title)
            textSize = 20f
        })
        profile.addView(MaterialTextView(root.context).apply {
            text = root.context.getString(R.string.admin_profile_email, admin.email)
            textSize = 16f
        })
        profile.addView(MaterialTextView(root.context).apply {
            text = root.context.getString(R.string.admin_profile_status, admin.status)
            textSize = 16f
        })
        container.addView(profile)
    }

    private fun renderContent(container: LinearLayout, state: AdminUiState.Content) {
        container.addView(MaterialTextView(root.context).apply {
            text = root.context.getString(R.string.devices_available, state.managedDevices.size)
            textSize = 16f
        })
    }

    private fun navigationButton(destination: AdminDestination, label: Int): View =
        MaterialButton(root.context).apply {
            text = root.context.getString(label)
            minHeight = resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
            contentDescription = root.context.getString(label)
            setOnClickListener { onNavigate(destination) }
        }
}
