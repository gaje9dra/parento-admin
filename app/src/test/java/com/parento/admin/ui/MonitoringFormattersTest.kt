package com.parento.admin.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringFormattersTest {
    @Test
    fun nullValuesAreExplicitlyUnavailable() {
        assertEquals("Unavailable", MonitoringFormatters.timestamp(null))
        assertEquals("Unavailable", MonitoringFormatters.relativeAge(null))
        assertEquals("Unavailable", MonitoringFormatters.bytes(null))
    }

    @Test
    fun byteFormattingIsHumanReadable() {
        assertEquals("1023 B", MonitoringFormatters.bytes(1023))
        assertEquals("1.0 KB", MonitoringFormatters.bytes(1024))
        assertEquals("1.0 MB", MonitoringFormatters.bytes(1024 * 1024))
    }
}
