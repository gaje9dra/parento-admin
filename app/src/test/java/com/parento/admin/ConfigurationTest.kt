package com.parento.admin

import com.parento.admin.config.AppConfig
import com.parento.admin.config.AppEnvironment
import com.parento.admin.config.FeatureFlags
import com.parento.admin.config.LoggingConfig
import com.parento.admin.config.SecurityConfig
import com.parento.admin.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationTest {
    private fun config(
        environment: AppEnvironment = AppEnvironment.DEVELOPMENT,
        backendBaseUrl: String = "https://backend.example.invalid",
        requireHttps: Boolean = false,
        debugDiagnostics: Boolean = environment == AppEnvironment.DEVELOPMENT,
    ) = AppConfig(
        environment = environment,
        backendBaseUrl = backendBaseUrl,
        logging = LoggingConfig(LogLevel.DEBUG, enabled = true),
        featureFlags = FeatureFlags(),
        security = SecurityConfig(
            requireHttps = requireHttps,
            allowDebugDiagnostics = debugDiagnostics,
        ),
    )

    @Test
    fun validConfigurationLoads() {
        val result = config()
        assertEquals(AppEnvironment.DEVELOPMENT, result.environment)
        assertEquals("https://backend.example.invalid", result.backendBaseUrl)
    }

    @Test
    fun environmentSeparationIsExplicit() {
        assertTrue(config(AppEnvironment.DEVELOPMENT).isDevelopment)
        assertTrue(config(AppEnvironment.TEST).isTest)
        assertTrue(config(AppEnvironment.PRODUCTION, requireHttps = true, debugDiagnostics = false).isProduction)
    }

    @Test
    fun invalidBackendUrlIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            config(backendBaseUrl = "not-a-url")
        }
    }

    @Test
    fun backendUrlCannotContainCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            config(backendBaseUrl = "https://user:password@backend.example.invalid")
        }
    }

    @Test
    fun productionRequiresHttps() {
        assertThrows(IllegalArgumentException::class.java) {
            config(
                environment = AppEnvironment.PRODUCTION,
                backendBaseUrl = "http://backend.example.invalid",
                requireHttps = true,
                debugDiagnostics = false,
            )
        }
    }

    @Test
    fun backendUrlRejectsUnexpectedPathAndQuery() {
        assertThrows(IllegalArgumentException::class.java) {
            config(backendBaseUrl = "https://backend.example.invalid/api")
        }
        assertThrows(IllegalArgumentException::class.java) {
            config(backendBaseUrl = "https://backend.example.invalid?token=secret")
        }
    }

    @Test
    fun productionDiagnosticsMustBeDisabled() {
        val result = config(
            environment = AppEnvironment.PRODUCTION,
            requireHttps = true,
            debugDiagnostics = false,
        )
        assertFalse(result.security.allowDebugDiagnostics && result.isProduction)
    }

    @Test
    fun productionCannotDisableHttps() {
        assertThrows(IllegalArgumentException::class.java) {
            config(
                environment = AppEnvironment.PRODUCTION,
                requireHttps = false,
                debugDiagnostics = false,
            )
        }
    }

    @Test
    fun productionCannotEnableDebugDiagnostics() {
        assertThrows(IllegalArgumentException::class.java) {
            config(
                environment = AppEnvironment.PRODUCTION,
                requireHttps = true,
                debugDiagnostics = true,
            )
        }
    }

    @Test
    fun futureFeatureFlagsDefaultToDisabled() {
        val flags = config().featureFlags
        assertFalse(flags.authentication)
        assertFalse(flags.enrollment)
        assertFalse(flags.deviceCommunication)
        assertFalse(flags.location)
        assertFalse(flags.screenSharing)
        assertFalse(flags.audio)
        assertFalse(flags.applicationManagement)
        assertFalse(flags.websiteFiltering)
        assertFalse(flags.deviceRestrictions)
    }
}
