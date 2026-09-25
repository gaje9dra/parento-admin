package com.parento.admin.domain

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object DeviceMonitoringFormatting {
    fun bytes(value: Long?): String {
        if (value == null || value < 0L) return "Unavailable"
        if (value < 1024L) return value.toString() + " B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var index = -1
        while (size >= 1024.0 && index < units.lastIndex) {
            size /= 1024.0
            index++
        }
        return String.format(Locale.US, "%.1f %s", size, units[index])
    }

    fun percentage(value: Int?): String =
        if (value == null || value !in 0..100) "Unavailable" else value.toString() + "%"

    fun timestamp(epochMillis: Long?): String =
        epochMillis?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(it))
        } ?: "Never reported"

    fun relativeUpdatedAt(observedAtEpochMillis: Long?, nowEpochMillis: Long = System.currentTimeMillis()): String {
        if (observedAtEpochMillis == null) return "Never reported"
        val delta = max(0L, nowEpochMillis - observedAtEpochMillis)
        val seconds = delta / 1_000L
        return when {
            seconds < 60L -> "Updated just now"
            seconds < 3_600L -> "Updated " + (seconds / 60L) + " min ago"
            seconds < 86_400L -> "Updated " + (seconds / 3_600L) + " hr ago"
            else -> "Updated " + (seconds / 86_400L) + " day(s) ago"
        }
    }

    fun storageUsedPercent(total: Long?, used: Long?): Int? =
        if (total == null || used == null || total <= 0L || used < 0L) null
        else ((used.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
}
