package com.parento.admin.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_application_state")
data class LocalApplicationStateEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val stateVersion: Int = 1,
    val lastSynchronizedAtEpochMillis: Long? = null,
    val initialized: Boolean = false,
    val installationId: String? = null,
    val installationCreatedAtEpochMillis: Long? = null,
    val setupState: String = DEFAULT_SETUP_STATE,
    val lastInitializedAtEpochMillis: Long? = null,
) {
    companion object {
        const val SINGLETON_ID = 1
        const val DEFAULT_SETUP_STATE = "UNCONFIGURED"
    }
}
