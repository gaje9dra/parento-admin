package com.parento.admin.data

import android.content.Context
import com.parento.admin.data.local.LocalDatabaseFactory
import com.parento.admin.data.local.ParentoAdminDatabase

class AdminAppContainer(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext

    val database: ParentoAdminDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalDatabaseFactory.create(applicationContext)
    }

    val localStateRepository: LocalStateRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomLocalStateRepository(database.localApplicationStateDao())
    }

    override fun close() {
        if (database.isOpen) {
            database.close()
        }
    }
}
