package com.parento.admin.presentation

/**
 * Navigation contract for future Admin screens.
 *
 * These destinations do not imply that the corresponding screens or functionality
 * are implemented in Phase 1.2.
 */
sealed interface NavigationDestination {
    data object Login : NavigationDestination
    data object Dashboard : NavigationDestination
    data object Devices : NavigationDestination
    data class DeviceDetails(val deviceId: String) : NavigationDestination
    data class Location(val deviceId: String) : NavigationDestination
    data class ScreenSession(val deviceId: String) : NavigationDestination
    data class Applications(val deviceId: String) : NavigationDestination
    data class Websites(val deviceId: String) : NavigationDestination
    data class Policies(val deviceId: String) : NavigationDestination
}
