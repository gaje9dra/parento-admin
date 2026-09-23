package com.parento.admin

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.parento.admin.data.local.ParentoAdminDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStatePhase23MigrationTest {
    @Test
    fun migration2To3PreservesIdentityAndCreatesUniqueIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "parento-admin-phase23-" + System.currentTimeMillis() + ".db"
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE local_application_state (id INTEGER NOT NULL PRIMARY KEY, stateVersion INTEGER NOT NULL, lastSynchronizedAtEpochMillis INTEGER, initialized INTEGER NOT NULL, installationId TEXT, installationCreatedAtEpochMillis INTEGER, setupState TEXT NOT NULL DEFAULT 'UNCONFIGURED', lastInitializedAtEpochMillis INTEGER)")
                    db.execSQL("INSERT INTO local_application_state (id, stateVersion, initialized, installationId) VALUES (1, 1, 1, 'installation-1')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val db = helper.writableDatabase
        try {
            ParentoAdminDatabase.MIGRATION_2_3.migrate(db)
            val indexes = db.query("PRAGMA index_list('local_application_state')")
            val found = indexes.use {
                var value = false
                while (it.moveToNext()) {
                    if (it.getString(it.getColumnIndexOrThrow("name")) == "index_local_application_state_installationId") {
                        value = true
                        assertEquals(1, it.getInt(it.getColumnIndexOrThrow("unique")))
                    }
                }
                value
            }
            assertTrue(found)
            db.query("SELECT installationId FROM local_application_state WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("installation-1", it.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }
}