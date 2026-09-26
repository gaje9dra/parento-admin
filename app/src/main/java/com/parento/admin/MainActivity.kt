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
import com.google.android.gms.maps.MapView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textview.MaterialTextView
import com.parento.admin.auth.AuthenticationState
import com.parento.admin.ui.AudioAccessViewModel
import com.parento.admin.ui.AudioAccessViewModelFactory
import com.parento.admin.ui.AudioAccessScreen
import com.parento.admin.location.DeviceLocationUseCase
import com.parento.admin.navigation.AdminDestination
import com.parento.admin.navigation.AdminNavigator
import com.parento.admin.ui.AdminHomeScreen
import com.parento.admin.ui.AdminHomeViewModel
import com.parento.admin.ui.AdminLoginScreen
import com.parento.admin.ui.AuthenticationViewModel
import com.parento.admin.ui.AuthenticationViewModelFactory
import com.parento.admin.ui.DeviceManagementScreen
import com.parento.admin.ui.DeviceManagementViewModel
import com.parento.admin.ui.DeviceManagementViewModelFactory
import com.parento.admin.ui.LocationScreen
import com.parento.admin.ui.LocationViewModel
import com.parento.admin.ui.LocationViewModelFactory
import com.parento.admin.ui.ScreenSharingScreen
import com.parento.admin.ui.ScreenSharingViewModel
import com.parento.admin.ui.ScreenSharingViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var hasCompletedInitialStart = false
    private var selectedLocationDeviceId: String? = null
    private var locationMapView: MapView? = null

    private val audioAccessViewModel: AudioAccessViewModel by lazy {
        ViewModelProvider(
            this,
            AudioAccessViewModelFactory(
                repository = appContainer.audioAccessRepository,
                transport = appContainer.audioTransport,
                playback = com.parento.admin.audio.SessionBoundAudioPlaybackController(),
                onSessionExpired = { authViewModel.validateCurrentSession() },
            ),
        )[AudioAccessViewModel::class.java]
    }

    private val screenSharingViewModel: ScreenSharingViewModel by lazy {
        ViewModelProvider(
            this,
            ScreenSharingViewModelFactory(
                repository = appContainer.screenSharingRepository,
                onSessionExpired = { authViewModel.validateCurrentSession() },
            ),
        )[ScreenSharingViewModel::class.java]
    }

    private val appContainer
        get() = (application as ParentoAdminApplication).appContainer

    private val authViewModel: AuthenticationViewModel by lazy {
        ViewModelProvider(
            this,
            AuthenticationViewModelFactory(appContainer.authenticationRepository),
        )[AuthenticationViewModel::class.java]
    }

    private val homeViewModel: AdminHomeViewModel by lazy {
        ViewModelProvider(this)[AdminHomeViewModel::class.java]
    }

    private val deviceViewModel: DeviceManagementViewModel by lazy {
        ViewModelProvider(
            this,
            DeviceManagementViewModelFactory(
                repository = appContainer.managedDeviceRepository,
                onSessionExpired = { authViewModel.validateCurrentSession() },
            ),
        )[DeviceManagementViewModel::class.java]
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
                launch {
                    authViewModel.state.collect { state ->
                        renderAuthenticationState(state)
                    }
                }
                launch {
                    deviceViewModel.listState.collect {
                        if (navigator.currentDestination == AdminDestination.DEVICES &&
                            deviceViewModel.detailState.value is com.parento.admin.ui.DeviceDetailUiState.Idle
                        ) {
                            renderDeviceList()
                        }
                    }
                }
                launch {
                    audioAccessViewModel.uiState.collect {
                        if (navigator.currentDestination == AdminDestination.AUDIO_ACCESS) {
                            renderAudioAccess()
                        }
                    }
                }
                launch {
                    screenSharingViewModel.uiState.collect {
                        if (navigator.currentDestination == AdminDestination.SCREEN_SHARING) {
                            renderScreenSharing()
                        }
                    }
                }
                launch {
                    deviceViewModel.detailState.collect {
                        if (navigator.currentDestination == AdminDestination.DEVICES) {
                            renderDeviceDetailIfSelected()
                        }
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (authViewModel.state.value is AuthenticationState.Authenticated &&
                        navigator.currentDestination == AdminDestination.AUDIO_ACCESS
                    ) {
                        audioAccessViewModel.clearDevice()
                        navigator.navigate(AdminDestination.DEVICES)
                        renderDeviceDetailIfSelected()
                        return
                    }

                    if (authViewModel.state.value is AuthenticationState.Authenticated &&
                        navigator.currentDestination == AdminDestination.SCREEN_SHARING
                    ) {
                        navigator.navigate(AdminDestination.DEVICES)
                        screenSharingViewModel.clearDevice()
                        renderDeviceDetailIfSelected()
                        return
                    }

                    if (authViewModel.state.value is AuthenticationState.Authenticated &&
                        navigator.currentDestination == AdminDestination.DEVICES &&
                        deviceViewModel.detailState.value !is com.parento.admin.ui.DeviceDetailUiState.Idle
                    ) {
                        deviceViewModel.clearSelection()
                        renderDeviceList()
                        return
                    }

                    if (authViewModel.state.value is AuthenticationState.Authenticated &&
                        navigator.currentDestination != AdminDestination.HOME
                    ) {
                        navigator.navigate(AdminDestination.HOME)
                        selectedLocationDeviceId = null
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
        locationMapView?.onStart()
        if (hasCompletedInitialStart) {
            authViewModel.validateCurrentSession()
            screenSharingViewModel.onForeground()
        } else {
            hasCompletedInitialStart = true
        }
    }

    override fun onResume() {
        super.onResume()
        locationMapView?.onResume()
    }

    override fun onPause() {
        locationMapView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        screenSharingViewModel.onBackground()
        audioAccessViewModel.onBackground()
        locationMapView?.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        locationMapView?.onDestroy()
        locationMapView = null
        super.onDestroy()
    }

    override fun onLowMemory() {
        locationMapView?.onLowMemory()
        super.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        locationMapView?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
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
                navigator.navigate(AdminDestination.HOME)
                selectedLocationDeviceId = null
                screenSharingViewModel.clearDevice()
                audioAccessViewModel.handleAdminLogout()
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
        val admin = (authViewModel.state.value as? AuthenticationState.Authenticated)?.admin
            ?: return
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
            AdminHomeScreen(frame, homeViewModel, admin) { target, deviceId ->
                selectedLocationDeviceId = deviceId
                navigator.navigate(target)
                renderAuthenticatedDestination()
            }.render(homeViewModel.uiState.value)
        })
    }

    private fun renderAuthenticatedDestination() {
        contentRoot.removeAllViews()
        when (navigator.currentDestination) {
            AdminDestination.HOME -> renderAuthenticatedState()
            AdminDestination.DEVICES -> {
                toolbar.title = getString(R.string.nav_devices)
                renderDeviceList()
                if (deviceViewModel.listState.value is com.parento.admin.ui.DeviceListUiState.Loading) {
                    deviceViewModel.loadDevices()
                }
            }
            AdminDestination.POLICIES -> renderPlaceholder(
                R.string.nav_policies,
                R.string.policies_placeholder,
            )
            AdminDestination.SETTINGS -> renderPlaceholder(
                R.string.nav_settings,
                R.string.settings_placeholder,
            )
            AdminDestination.LOCATION -> renderLocation()
            AdminDestination.SCREEN_SHARING -> renderScreenSharing()
            AdminDestination.AUDIO_ACCESS -> renderAudioAccess()
        }
    }

    private fun renderDeviceList() {
        if (navigator.currentDestination != AdminDestination.DEVICES) return
        toolbar.title = getString(R.string.nav_devices)
        contentRoot.removeAllViews()
        contentRoot.addView(FrameLayout(this).also { frame ->
            DeviceManagementScreen(frame, deviceViewModel).renderList(
                deviceViewModel.listState.value,
            ) { deviceId ->
                deviceViewModel.openDevice(deviceId)
                renderDeviceDetailIfSelected()
            }
        })
    }

    private fun renderDeviceDetailIfSelected() {
        if (navigator.currentDestination != AdminDestination.DEVICES) return
        val state = deviceViewModel.detailState.value
        if (state is com.parento.admin.ui.DeviceDetailUiState.Idle) {
            renderDeviceList()
            return
        }
        toolbar.title = getString(R.string.nav_devices)
        contentRoot.removeAllViews()
        contentRoot.addView(FrameLayout(this).also { frame ->
            DeviceManagementScreen(frame, deviceViewModel).renderDetail(
                state = state,
                onStartScreenSharing = { status ->
                    screenSharingViewModel.bindDevice(status)
                    navigator.navigate(AdminDestination.SCREEN_SHARING)
                    renderScreenSharing()
                },
                onStartAudioAccess = { status ->
                    audioAccessViewModel.bindDevice(status)
                    navigator.navigate(AdminDestination.AUDIO_ACCESS)
                    renderAudioAccess()
                },
                onBack = {
                    deviceViewModel.clearSelection()
                    renderDeviceList()
                },
            )
        })
    }

    private fun renderAudioAccess() {
        if (navigator.currentDestination != AdminDestination.AUDIO_ACCESS) return
        toolbar.title = getString(R.string.audio_access_title)
        contentRoot.removeAllViews()
        contentRoot.addView(FrameLayout(this).also { frame ->
            AudioAccessScreen(
                root = frame,
                viewModel = audioAccessViewModel,
            ).render(audioAccessViewModel.uiState.value, deviceViewModel.currentSelectedDeviceStatus())
        })
    }

    private fun renderScreenSharing() {
        if (navigator.currentDestination != AdminDestination.SCREEN_SHARING) return
        toolbar.title = getString(R.string.screen_sharing_title)
        contentRoot.removeAllViews()
        contentRoot.addView(FrameLayout(this).also { frame ->
            ScreenSharingScreen(
                root = frame,
                viewModel = screenSharingViewModel,
                onBack = {
                    screenSharingViewModel.clearDevice()
                    navigator.navigate(AdminDestination.DEVICES)
                    renderDeviceDetailIfSelected()
                },
            ).render(screenSharingViewModel.uiState.value)
        })
    }

    private fun renderLocation() {
        val deviceId = selectedLocationDeviceId
        if (deviceId.isNullOrBlank()) {
            renderPlaceholder(R.string.location_title, R.string.location_device_missing)
            return
        }

        toolbar.title = getString(R.string.location_title)
        val useCase = DeviceLocationUseCase(appContainer.deviceLocationRepository)
        val viewModel = ViewModelProvider(
            this,
            LocationViewModelFactory(deviceId, useCase),
        ).get("location:$deviceId", LocationViewModel::class.java)

        if (BuildConfig.PARENTO_MAPS_API_KEY.isNotBlank() && locationMapView == null) {
            locationMapView = MapView(this).also { it.onCreate(null) }
        }

        contentRoot.addView(FrameLayout(this).also { frame ->
            LocationScreen(
                root = frame,
                viewModel = viewModel,
                mapView = locationMapView,
                onBack = {
                    navigator.navigate(AdminDestination.HOME)
                    selectedLocationDeviceId = null
                    renderAuthenticatedState()
                },
            ).render(viewModel.uiState.value)
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (navigator.currentDestination == AdminDestination.LOCATION) {
                        contentRoot.removeAllViews()
                        contentRoot.addView(FrameLayout(this@MainActivity).also { frame ->
                            LocationScreen(
                                root = frame,
                                viewModel = viewModel,
                                mapView = locationMapView,
                                onBack = {
                                    navigator.navigate(AdminDestination.HOME)
                                    selectedLocationDeviceId = null
                                    renderAuthenticatedState()
                                },
                            ).render(state)
                        })
                    }
                }
            }
        }

        if (viewModel.uiState.value is com.parento.admin.ui.LocationUiState.Loading) {
            viewModel.load()
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
