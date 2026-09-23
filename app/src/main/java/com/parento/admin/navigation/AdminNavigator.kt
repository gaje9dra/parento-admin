package com.parento.admin.navigation

class AdminNavigator(
    initialDestination: AdminDestination = AdminDestination.HOME,
) {
    var currentDestination: AdminDestination = initialDestination
        private set

    fun navigate(destination: AdminDestination) {
        currentDestination = destination
    }
}
