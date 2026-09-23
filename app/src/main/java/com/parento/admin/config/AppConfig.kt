package com.parento.admin.config

import com.parento.admin.logging.LogLevel
import java.net.URI

data class LoggingConfig(
    val minimumLevel: LogLevel,
    val enabled: Boolean,
)

data class FeatureFlags(
    val authentication: Boolean = false,
    val enrollment: Boolean = false,
    val deviceCommunication: Boolean = false,
    val location: Boolean = false,
    val screenSharing: Boolean = false,
    val audio: Boolean = false,
    val applicationManagement: Boolean = false,
    val websiteFiltering: Boolean = false,
    val deviceRestrictions: Boolean = false,
)

data class SecurityConfig(
    val requireHttps: Boolean,
    val allowDebugDiagnostics: Boolean,
)

data class AppConfig(
    val environment: AppEnvironment,
    val backendBaseUrl: String,
    val logging: LoggingConfig,
    val featureFlags: FeatureFlags,
    val security: SecurityConfig,
) {
    init {
        require(backendBaseUrl.isNotBlank()) {
            "Backend base URL must not be blank."
        }

        val uri = try {
            URI.create(backendBaseUrl)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Backend base URL is invalid.")
        }

        require(uri.scheme == "https" || uri.scheme == "http") {
            "Backend base URL must use HTTP(S)."
        }
        require(uri.userInfo.isNullOrBlank()) {
            "Backend base URL must not contain embedded credentials."
        }

        require(uri.host != null) {
            "Backend base URL must contain a valid host."
        }

        if (security.requireHttps) {
            require(uri.scheme == "https") {
                "Production backend base URL must use HTTPS."
            }
        }
    }

    val isDevelopment: Boolean
        get() = environment == AppEnvironment.DEVELOPMENT

    val isTest: Boolean
        get() = environment == AppEnvironment.TEST

    val isProduction: Boolean
        get() = environment == AppEnvironment.PRODUCTION
}
