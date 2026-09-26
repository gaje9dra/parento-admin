package com.parento.admin

import com.parento.admin.screensharing.ScreenSharingSessionStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSharingRepositoryTest {
    @Test
    fun sessionLifecycleDefinesExpectedTerminalStates() {
        assertTrue(ScreenSharingSessionStatus.STOPPED.isTerminal)
        assertTrue(ScreenSharingSessionStatus.EXPIRED.isTerminal)
        assertTrue(ScreenSharingSessionStatus.FAILED.isTerminal)
        assertTrue(ScreenSharingSessionStatus.REJECTED.isTerminal)
    }

    @Test
    fun nonTerminalStatesRemainReconciliable() {
        assertTrue(!ScreenSharingSessionStatus.REQUESTED.isTerminal)
        assertTrue(!ScreenSharingSessionStatus.AUTHORIZED.isTerminal)
        assertTrue(!ScreenSharingSessionStatus.STARTING.isTerminal)
        assertTrue(!ScreenSharingSessionStatus.ACTIVE.isTerminal)
        assertTrue(!ScreenSharingSessionStatus.STOPPING.isTerminal)
    }
}
