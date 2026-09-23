package com.parento.admin.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LocalApplicationStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ParentoAdminDatabase : RoomDatabase() {
    abstract fun localApplicationStateDao(): LocalApplicationStateDao
}
