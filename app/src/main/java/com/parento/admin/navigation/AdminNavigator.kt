package com.parento.admin.navigation

class AdminNavigator(
    initialDestination: AdminDestination = AdminDestination.HOME,
) {
    var currentDestination: AdminDestination = initialDestination
        private set

    var selectedDeviceId: String? = null
        private set

    fun navigate(destination: AdminDestination) {
        currentDestination = destination
        if (destination != AdminDestination.DEVICE_DETAIL) {
            selectedDeviceId = null
        }
    }

    fun navigateToDevice(deviceId: String) {
        selectedDeviceId = deviceId
        currentDestination = AdminDestination.DEVICE_DETAIL
    }

    fun backToDevices() {
        selectedDeviceId = null
        currentDestination = AdminDestination.DEVICES
    }
}
