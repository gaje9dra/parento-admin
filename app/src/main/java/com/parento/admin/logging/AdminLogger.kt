package com.parento.admin.logging

enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

/**
 * Structured logging boundary. Implementations must never emit credentials,
 * tokens, private keys, API secrets, or unnecessary sensitive device information.
 *
 * Implementations can disable logging by build type or runtime configuration.
 */
interface AdminLogger {
    val enabled: Boolean

    fun log(level: LogLevel, message: String)
}
