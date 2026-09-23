package com.parento.admin

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textview.MaterialTextView
import com.parento.admin.navigation.AdminDestination
import com.parento.admin.navigation.AdminNavigator
import com.parento.admin.ui.AdminHomeScreen
import com.parento.admin.ui.AdminHomeViewModel
import com.parento.admin.ui.AdminUiState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel: AdminHomeViewModel by viewModels()
    private val navigator = AdminNavigator()

    private lateinit var contentRoot: FrameLayout
    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navigator.currentDestination != AdminDestination.HOME) {
                    navigator.navigate(AdminDestination.HOME)
                    renderDestination()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (navigator.currentDestination == AdminDestination.HOME) {
                        renderDestination(state)
                    }
                }
            }
        }

        renderDestination()
    }

    private fun renderDestination(state: AdminUiState = viewModel.uiState.value) {
        contentRoot.removeAllViews()

        when (navigator.currentDestination) {
            AdminDestination.HOME -> {
                toolbar.title = getString(R.string.dashboard_title)
                contentRoot.addView(FrameLayout(this).also { frame ->
                    AdminHomeScreen(frame, viewModel) { target ->
                        navigator.navigate(target)
                        renderDestination()
                    }.render(state)
                })
            }

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
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.screen_padding),
                    resources.getDimensionPixelSize(R.dimen.screen_padding),
                    resources.getDimensionPixelSize(R.dimen.screen_padding),
                    resources.getDimensionPixelSize(R.dimen.screen_padding),
                )
            },
        )
    }
}
