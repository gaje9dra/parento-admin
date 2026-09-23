package com.parento.admin.logging

import android.util.Log
import com.parento.admin.config.AppConfig

class AndroidAdminLogger(
    private val config: AppConfig,
) : AdminLogger {
    override val enabled: Boolean
        get() = config.logging.enabled

    override fun log(level: LogLevel, message: String) {
        if (!enabled || level.ordinal < config.logging.minimumLevel.ordinal) {
            return
        }

        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARNING -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
    }

    private companion object {
        const val TAG = "ParentoAdmin"
    }
}
