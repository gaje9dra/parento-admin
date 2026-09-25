package com.parento.admin.ui

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object MonitoringFormatters {
    private val timestampFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    fun timestamp(value: String?): String {
        if (value.isNullOrBlank()) return "Unavailable"
        return runCatching { timestampFormatter.format(Instant.parse(value)) }
            .getOrElse { "Unavailable" }
    }

    fun relativeAge(value: String?): String {
        if (value.isNullOrBlank()) return "Unavailable"
        return runCatching {
            val seconds = Duration.between(Instant.parse(value), Instant.now()).seconds.coerceAtLeast(0)
            when {
                seconds < 60 -> "Updated just now"
                seconds < 3600 -> "Updated ${seconds / 60}m ago"
                seconds < 86_400 -> "Updated ${seconds / 3600}h ago"
                else -> "Updated ${seconds / 86_400}d ago"
            }
        }.getOrElse { "Updated time unavailable" }
    }

    fun bytes(value: Long?): String {
        if (value == null || value < 0) return "Unavailable"
        if (value < 1024L) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var amount = value.toDouble()
        var index = -1
        while (amount >= 1024 && index < units.lastIndex) {
            amount /= 1024
            index++
        }
        return String.format(java.util.Locale.US, "%.1f %s", amount, units[index])
    }
}