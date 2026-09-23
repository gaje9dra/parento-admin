package com.parento.admin.domain

enum class AdminLocalSetupState {
    UNCONFIGURED,
    READY,
}

fun AdminLocalSetupState.canTransitionTo(target: AdminLocalSetupState): Boolean =
    when (this) {
        UNCONFIGURED -> target == READY
        READY -> target == UNCONFIGURED
    }
