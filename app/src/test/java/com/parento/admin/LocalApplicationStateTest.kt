package com.parento.admin

import com.parento.admin.data.LocalApplicationState
import com.parento.admin.domain.AdminLocalSetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalApplicationStateTest {
    @Test
    fun defaultsAreMinimalAndUnauthenticated() {
        val state = LocalApplicationState()
        assertEquals(1, state.stateVersion)
        assertEquals(null, state.installationId)
        assertFalse(state.initialized)
        assertEquals(AdminLocalSetupState.UNCONFIGURED, state.setupState)
    }

    @Test
    fun setupLifecycleIsExplicitAndMinimal() {
        assertTrue(AdminLocalSetupState.UNCONFIGURED.canTransitionTo(AdminLocalSetupState.READY))
        assertFalse(AdminLocalSetupState.READY.canTransitionTo(AdminLocalSetupState.READY))
        assertEquals(setOf("UNCONFIGURED", "READY"), AdminLocalSetupState.entries.map { it.name }.toSet())
    }
}
