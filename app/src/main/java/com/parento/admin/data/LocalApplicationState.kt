package com.parento.admin.data

data class LocalApplicationState(
    val stateVersion: Int = 1,
    val lastSynchronizedAtEpochMillis: Long? = null,
    val initialized: Boolean = false,
)
