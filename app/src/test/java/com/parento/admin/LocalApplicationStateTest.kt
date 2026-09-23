package com.parento.admin

import com.parento.admin.data.LocalApplicationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalApplicationStateTest {
    @Test
    fun defaultsAreMinimalAndNonSensitive() {
        val state = LocalApplicationState()

        assertEquals(1, state.stateVersion)
        assertEquals(null, state.lastSynchronizedAtEpochMillis)
        assertFalse(state.initialized)
    }
}
