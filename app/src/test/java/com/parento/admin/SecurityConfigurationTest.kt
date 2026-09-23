package com.parento.admin

import com.parento.admin.config.AppConfig
import com.parento.admin.config.AppEnvironment
import com.parento.admin.config.FeatureFlags
import com.parento.admin.config.LoggingConfig
import com.parento.admin.config.SecurityConfig
import com.parento.admin.logging.LogLevel
import com.parento.admin.security.securityConfiguration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityConfigurationTest {
    private fun config(
        environment: AppEnvironment,
        allowDiagnostics: Boolean,
        requireHttps: Boolean = environment == AppEnvironment.PRODUCTION,
    ) = AppConfig(
        environment = environment,
        backendBaseUrl = "https://backend.example.invalid",
        logging = LoggingConfig(LogLevel.WARNING, enabled = true),
        featureFlags = FeatureFlags(),
        security = SecurityConfig(
            requireHttps = requireHttps,
            allowDebugDiagnostics = allowDiagnostics,
        ),
    )

    @Test
    fun developmentDiagnosticsCanBeEnabled() {
        val security = config(AppEnvironment.DEVELOPMENT, allowDiagnostics = true)
            .securityConfiguration()

        assertFalse(security.requireHttps)
        assertTrue(security.debugDiagnosticsAllowed)
    }

    @Test
    fun testAndProductionDiagnosticsAreNotAllowed() {
        val testSecurity = config(AppEnvironment.TEST, allowDiagnostics = true)
            .securityConfiguration()
        val productionSecurity = config(AppEnvironment.PRODUCTION, allowDiagnostics = true)
            .securityConfiguration()

        assertFalse(testSecurity.debugDiagnosticsAllowed)
        assertTrue(productionSecurity.requireHttps)
        assertFalse(productionSecurity.debugDiagnosticsAllowed)
    }
}
