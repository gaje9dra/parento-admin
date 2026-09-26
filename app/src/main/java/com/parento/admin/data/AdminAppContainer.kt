package com.parento.admin.data

import android.content.Context
import com.parento.admin.communication.AdminBackendApiClient
import com.parento.admin.communication.AuthenticationApiClient
import com.parento.admin.config.AdminApplicationConfig
import com.parento.admin.data.local.LocalDatabaseFactory
import com.parento.admin.data.local.ParentoAdminDatabase
import com.parento.admin.device.ManagedDeviceRepository
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

    val adminBackendApiClient: AdminBackendApiClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AdminBackendApiClient(
            config = AdminApplicationConfig.get(),
            authenticationRepository = authenticationRepository,
            sessionStore = secureSessionStore,
        )
    }

    val managedDeviceRepository: ManagedDeviceRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ManagedDeviceRepositoryImpl(adminBackendApiClient)
    }

    override fun close() {
        if (database.isOpen) {
            database.close()
        }
    }
}
