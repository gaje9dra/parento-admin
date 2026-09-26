package com.parento.admin.ui

import com.parento.admin.device.ManagementMode
import com.parento.admin.device.MonitoringFreshness
import com.parento.admin.domain.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringStatusLabelsTest {
    @Test
    fun freshnessStatesRemainExplicit() {
        assertEquals("Fresh", MonitoringStatusLabels.freshness(MonitoringFreshness.CURRENT))
        assertEquals("Stale", MonitoringStatusLabels.freshness(MonitoringFreshness.STALE))
        assertEquals("Very stale", MonitoringStatusLabels.freshness(MonitoringFreshness.VERY_STALE))
        assertEquals("Never reported", MonitoringStatusLabels.freshness(MonitoringFreshness.NEVER_REPORTED))
        assertEquals("Offline", MonitoringStatusLabels.freshness(MonitoringFreshness.DISCONNECTED))
        assertEquals("Revoked", MonitoringStatusLabels.freshness(MonitoringFreshness.REVOKED))
    }

    @Test
    fun operationalLabelsAreNotCollapsed() {
        assertEquals("Connected", MonitoringStatusLabels.connection(ConnectionState.CONNECTED))
        assertEquals("Disconnected", MonitoringStatusLabels.connection(ConnectionState.DISCONNECTED))
        assertEquals("Device Owner", MonitoringStatusLabels.management(ManagementMode.DEVICE_OWNER))
        assertEquals("Profile Owner", MonitoringStatusLabels.management(ManagementMode.PROFILE_OWNER))
        assertEquals("Unmanaged", MonitoringStatusLabels.management(ManagementMode.UNMANAGED))
    }
}
