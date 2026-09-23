package com.parento.admin.domain

enum class AdminLocalSetupState {
    UNCONFIGURED,
    READY,
}

fun AdminLocalSetupState.canTransitionTo(target: AdminLocalSetupState): Boolean =
    when (this) {
        AdminLocalSetupState.UNCONFIGURED -> target == AdminLocalSetupState.READY
        AdminLocalSetupState.READY -> target == AdminLocalSetupState.UNCONFIGURED
    }
