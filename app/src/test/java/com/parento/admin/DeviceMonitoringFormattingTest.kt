package com.parento.admin

import com.parento.admin.domain.DeviceMonitoringFormatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceMonitoringFormattingTest {
    @Test
    fun unavailableValuesAreNotConvertedToZero() {
        assertEquals("Unavailable", DeviceMonitoringFormatting.bytes(null))
        assertEquals("Unavailable", DeviceMonitoringFormatting.percentage(null))
        assertNull(DeviceMonitoringFormatting.storageUsedPercent(null, 10L))
        assertNull(DeviceMonitoringFormatting.storageUsedPercent(0L, 0L))
    }

    @Test
    fun storagePercentageIsSafelyDerived() {
        assertEquals(50, DeviceMonitoringFormatting.storageUsedPercent(1_000L, 500L))
        assertEquals(100, DeviceMonitoringFormatting.storageUsedPercent(1_000L, 2_000L))
    }

    @Test
    fun relativeTimestampDoesNotClaimLiveData() {
        assertEquals(
            "Updated 18 min ago",
            DeviceMonitoringFormatting.relativeUpdatedAt(0L, 1_080_000L),
        )
        assertEquals("Never reported", DeviceMonitoringFormatting.relativeUpdatedAt(null, 1L))
    }
}
