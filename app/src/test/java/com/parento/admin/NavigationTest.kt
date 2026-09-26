package com.parento.admin

import com.parento.admin.navigation.AdminDestination
import com.parento.admin.navigation.AdminNavigator
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {
    @Test
    fun rootStartsAtHome() {
        assertEquals(AdminDestination.HOME, AdminNavigator().currentDestination)
    }

    @Test
    fun navigatorChangesDestination() {
        val navigator = AdminNavigator()
        navigator.navigate(AdminDestination.DEVICES)
        assertEquals(AdminDestination.DEVICES, navigator.currentDestination)
    }

    @Test
    fun allRootDestinationsRemainStable() {
        assertEquals(
            listOf(
                AdminDestination.HOME,
                AdminDestination.DEVICES,
                AdminDestination.POLICIES,
                AdminDestination.SETTINGS,
                AdminDestination.LOCATION,
                AdminDestination.SCREEN_SHARING,
            ),
            AdminDestination.entries,
        )
    }
}
