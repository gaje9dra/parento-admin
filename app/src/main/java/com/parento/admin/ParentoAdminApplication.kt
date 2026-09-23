package com.parento.admin

import android.app.Application
import com.parento.admin.config.AdminApplicationConfig
import com.parento.admin.data.AdminAppContainer
import com.parento.admin.domain.OperationResult
import com.parento.admin.logging.AndroidAdminLogger
import com.parento.admin.logging.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ParentoAdminApplication : Application() {
    lateinit var appContainer: AdminAppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AdminApplicationConfig.initialize()
        appContainer = AdminAppContainer(this)

        val config = AdminApplicationConfig.get()
        val logger = AndroidAdminLogger(config)
        logger.log(LogLevel.INFO, "Parento Admin initialized.")

        applicationScope.launch {
            when (
                val result = appContainer.localStateRepository.initializeLocalState(
                    initializedAtEpochMillis = System.currentTimeMillis(),
                )
            ) {
                is OperationResult.Success ->
                    logger.log(LogLevel.INFO, "Local application state initialized.")
                is OperationResult.Failure ->
                    logger.log(LogLevel.ERROR, "Local application state initialization failed.")
            }
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        appContainer.close()
        super.onTerminate()
    }
}
