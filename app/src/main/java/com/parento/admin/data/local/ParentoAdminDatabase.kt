package com.parento.admin.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LocalApplicationStateEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ParentoAdminDatabase : RoomDatabase() {
    abstract fun localApplicationStateDao(): LocalApplicationStateDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE local_application_state ADD COLUMN installationId TEXT")
                database.execSQL(
                    "ALTER TABLE local_application_state " +
                        "ADD COLUMN installationCreatedAtEpochMillis INTEGER",
                )
                database.execSQL(
                    "ALTER TABLE local_application_state " +
                        "ADD COLUMN setupState TEXT NOT NULL DEFAULT 'UNCONFIGURED'",
                )
                database.execSQL(
                    "ALTER TABLE local_application_state " +
                        "ADD COLUMN lastInitializedAtEpochMillis INTEGER",
                )
            }
        }
    }
}
