package com.parento.admin

import com.parento.admin.data.LocalApplicationState
import com.parento.admin.domain.AdminLocalSetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalApplicationStateTest {
    @Test
    fun defaultsAreMinimalAndUnauthenticated() {
        val state = LocalApplicationState()
        assertEquals(1, state.stateVersion)
        assertEquals(null, state.lastSynchronizedAtEpochMillis)
        assertFalse(state.initialized)
        assertEquals(null, state.installationId)
        assertEquals(null, state.installationCreatedAtEpochMillis)
        assertEquals(AdminLocalSetupState.UNCONFIGURED, state.setupState)
        assertEquals(null, state.lastInitializedAtEpochMillis)
    }
}
