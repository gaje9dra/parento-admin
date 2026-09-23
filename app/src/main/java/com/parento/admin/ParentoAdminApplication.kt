package com.parento.admin

import android.app.Application
import com.parento.admin.config.AdminApplicationConfig
import com.parento.admin.logging.AndroidAdminLogger
import com.parento.admin.logging.LogLevel

class ParentoAdminApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AdminApplicationConfig.initialize()
        val config = AdminApplicationConfig.get()
        AndroidAdminLogger(config).log(
            LogLevel.INFO,
            "Parento Admin initialized.",
        )
    }
}
