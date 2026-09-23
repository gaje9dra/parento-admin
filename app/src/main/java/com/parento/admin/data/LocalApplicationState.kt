package com.parento.admin.data

import com.parento.admin.domain.AdminLocalSetupState

data class LocalApplicationState(
    val stateVersion: Int = 1,
    val lastSynchronizedAtEpochMillis: Long? = null,
    val initialized: Boolean = false,
    val installationId: String? = null,
    val installationCreatedAtEpochMillis: Long? = null,
    val setupState: AdminLocalSetupState = AdminLocalSetupState.UNCONFIGURED,
    val lastInitializedAtEpochMillis: Long? = null,
)
