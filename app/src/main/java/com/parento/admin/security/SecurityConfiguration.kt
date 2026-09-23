package com.parento.admin.security

import com.parento.admin.config.AppConfig

data class SecurityConfiguration(
    val requireHttps: Boolean,
    val debugDiagnosticsAllowed: Boolean,
)

fun AppConfig.securityConfiguration(): SecurityConfiguration =
    SecurityConfiguration(
        requireHttps = security.requireHttps,
        debugDiagnosticsAllowed = security.allowDebugDiagnostics && isDevelopment,
    )
