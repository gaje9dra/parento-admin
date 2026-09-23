package com.parento.admin.data.local

import android.content.Context
import androidx.room.Room

object LocalDatabaseFactory {
    fun create(context: Context): ParentoAdminDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            ParentoAdminDatabase::class.java,
            DATABASE_NAME,
        ).build()

    private const val DATABASE_NAME = "parento-admin.db"
}
