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
 */
interface AdminLogger {
    fun log(level: LogLevel, message: String)
}
