package com.parento.admin

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.parento.admin.data.local.ParentoAdminDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStateMigrationTest {
    @Test
    fun phase21StateMigratesToPhase22WithoutLosingExistingValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "parento-admin-migration-test-${System.currentTimeMillis()}.db"
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS local_application_state (" +
                            "id INTEGER NOT NULL, " +
                            "stateVersion INTEGER NOT NULL, " +
                            "lastSynchronizedAtEpochMillis INTEGER, " +
                            "initialized INTEGER NOT NULL, " +
                            "PRIMARY KEY(id))",
                    )
                    db.execSQL(
                        "INSERT INTO local_application_state " +
                            "(id, stateVersion, lastSynchronizedAtEpochMillis, initialized) " +
                            "VALUES (1, 1, 1234, 1)",
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val oldDb = helper.writableDatabase

        try {
            ParentoAdminDatabase.MIGRATION_1_2.migrate(oldDb)
            ParentoAdminDatabase.MIGRATION_2_3.migrate(oldDb)

            oldDb.query(
                "SELECT installationId, installationCreatedAtEpochMillis, " +
                    "setupState, lastInitializedAtEpochMillis, " +
                    "lastSynchronizedAtEpochMillis, initialized " +
                    "FROM local_application_state WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertNull(cursor.getString(0))
                assertNull(cursor.getLongOrNull(1))
                assertEquals("UNCONFIGURED", cursor.getString(2))
                assertNull(cursor.getLongOrNull(3))
                assertEquals(1234L, cursor.getLong(4))
                assertEquals(1, cursor.getInt(5))
            }

            oldDb.query("PRAGMA index_list('local_application_state')").use { cursor ->
                var foundUniqueInstallationIndex = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) ==
                        "index_local_application_state_installationId") {
                        foundUniqueInstallationIndex = true
                        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("unique")))
                    }
                }
                assertTrue(foundUniqueInstallationIndex)
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index)
}
