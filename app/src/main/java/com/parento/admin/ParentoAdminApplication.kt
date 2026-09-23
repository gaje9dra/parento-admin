package com.parento.admin

import android.app.Application
import com.parento.admin.config.AdminApplicationConfig
import com.parento.admin.data.AdminAppContainer
import com.parento.admin.logging.AndroidAdminLogger
import com.parento.admin.logging.LogLevel

class ParentoAdminApplication : Application() {
    lateinit var appContainer: AdminAppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        AdminApplicationConfig.initialize()
        appContainer = AdminAppContainer(this)

        val config = AdminApplicationConfig.get()
        AndroidAdminLogger(config).log(
            LogLevel.INFO,
            "Parento Admin initialized.",
        )
    }

    override fun onTerminate() {
        appContainer.close()
        super.onTerminate()
    }
}
