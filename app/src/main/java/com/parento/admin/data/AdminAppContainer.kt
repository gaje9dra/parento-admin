package com.parento.admin.data

import android.content.Context
import com.parento.admin.communication.AuthenticationApiClient
import com.parento.admin.config.AdminApplicationConfig
import com.parento.admin.data.local.LocalDatabaseFactory
import com.parento.admin.data.local.ParentoAdminDatabase
import com.parento.admin.domain.DeviceMonitoringRepository
import com.parento.admin.security.SecureSessionStore

class AdminAppContainer(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext

    val database: ParentoAdminDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalDatabaseFactory.create(applicationContext)
    }

    val localStateRepository: LocalStateRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomLocalStateRepository(database.localApplicationStateDao())
    }

    val secureSessionStore: SecureSessionStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SecureSessionStore(applicationContext)
    }

    val authenticationRepository: AuthenticationRepositoryImpl by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        AuthenticationRepositoryImpl(
            api = AuthenticationApiClient(AdminApplicationConfig.get()),
            secureStore = secureSessionStore,
        )
    }

    /**
     * Phase 7.3 monitoring boundary. The backend currently has no authenticated
     * Admin device-list/detail telemetry endpoints, so this remains explicit
     * rather than inventing a network contract or returning fake data.
     */
    val deviceMonitoringRepository: DeviceMonitoringRepository by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        UnavailableDeviceMonitoringRepository()
    }

    override fun close() {
        if (database.isOpen) {
            database.close()
        }
    }
}
