package com.parento.admin

import android.os.Bundle
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textview.MaterialTextView
import com.parento.admin.auth.AuthenticationState
import com.parento.admin.navigation.AdminDestination
import com.parento.admin.navigation.AdminNavigator
import com.parento.admin.ui.AdminHomeScreen
import com.parento.admin.ui.AdminHomeViewModel
import com.parento.admin.ui.AdminLoginScreen
import com.parento.admin.ui.AdminUiState
import com.parento.admin.ui.AuthenticationViewModel
import com.parento.admin.ui.AuthenticationViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var hasCompletedInitialStart = false

    private val authViewModel: AuthenticationViewModel by lazy {
        ViewModelProvider(
            this,
            AuthenticationViewModelFactory(
                (application as ParentoAdminApplication)
                    .appContainer.authenticationRepository,
            ),
        )[AuthenticationViewModel::class.java]
    }

    private val homeViewModel: AdminHomeViewModel by lazy {
        ViewModelProvider(this)[AdminHomeViewModel::class.java]
    }

    private val navigator = AdminNavigator()

    private lateinit var contentRoot: FrameLayout
    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        val root = FrameLayout(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.app_name)
        }
        contentRoot = FrameLayout(this)

        column.addView(toolbar)
        column.addView(
            contentRoot,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        root.addView(column)
        setContentView(root)

        authViewModel.restoreSession()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.state.collect { state ->
                    renderAuthenticationState(state)
                }
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (authViewModel.state.value is AuthenticationState.Authenticated &&
                        navigator.currentDestination != AdminDestination.HOME
                    ) {
                        navigator.navigate(AdminDestination.HOME)
                        renderAuthenticatedState()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )
    }

    override fun onStart() {
        super.onStart()
        if (hasCompletedInitialStart) {
            authViewModel.validateCurrentSession()
        } else {
            hasCompletedInitialStart = true
        }
    }

    private fun renderAuthenticationState(state: AuthenticationState) {
        toolbar.menu.clear()

        when (state) {
            AuthenticationState.Unauthenticated,
            AuthenticationState.Authenticating,
            is AuthenticationState.AuthenticationError,
            AuthenticationState.SessionExpired,
            AuthenticationState.SessionRevoked,
            AuthenticationState.AccountDisabled -> {
                toolbar.title = getString(R.string.login_title)
                contentRoot.removeAllViews()
                contentRoot.addView(FrameLayout(this).also { frame ->
                    AdminLoginScreen(frameAsColumn(frame), authViewModel).render(state)
                })
            }

            is AuthenticationState.Authenticated -> {
                renderAuthenticatedState()
            }
        }
    }

    private fun frameAsColumn(frame: FrameLayout): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        frame.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        return column
    }

    private fun renderAuthenticatedState() {
        toolbar.menu.clear()
        toolbar.title = getString(R.string.dashboard_title)
        toolbar.menu.add(R.string.logout).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener {
                authViewModel.logout()
                true
            }
        }

        contentRoot.removeAllViews()
        contentRoot.addView(FrameLayout(this).also { frame ->
            AdminHomeScreen(frame, homeViewModel, (state as AuthenticationState.Authenticated).admin) { target ->
                navigator.navigate(target)
                renderAuthenticatedDestination()
            }.render(homeViewModel.uiState.value)
        })
    }

    private fun renderAuthenticatedDestination() {
        contentRoot.removeAllViews()
        when (navigator.currentDestination) {
            AdminDestination.HOME -> renderAuthenticatedState()
            AdminDestination.DEVICES -> renderPlaceholder(
                R.string.nav_devices,
                R.string.devices_placeholder,
            )
            AdminDestination.POLICIES -> renderPlaceholder(
                R.string.nav_policies,
                R.string.policies_placeholder,
            )
            AdminDestination.SETTINGS -> renderPlaceholder(
                R.string.nav_settings,
                R.string.settings_placeholder,
            )
        }
    }

    private fun renderPlaceholder(titleRes: Int, messageRes: Int) {
        toolbar.title = getString(titleRes)
        contentRoot.addView(
            MaterialTextView(this).apply {
                text = getString(messageRes)
                textSize = 18f
                val padding = resources.getDimensionPixelSize(R.dimen.screen_padding)
                setPadding(padding, padding, padding, padding)
            },
        )
    }
}
