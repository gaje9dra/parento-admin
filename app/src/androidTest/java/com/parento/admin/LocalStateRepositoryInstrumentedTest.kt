package com.parento.admin

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parento.admin.data.LocalApplicationState
import com.parento.admin.data.RoomLocalStateRepository
import com.parento.admin.data.local.ParentoAdminDatabase
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStateRepositoryInstrumentedTest {
    private lateinit var database: ParentoAdminDatabase
    private lateinit var repository: RoomLocalStateRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ParentoAdminDatabase::class.java,
        ).build()
        repository = RoomLocalStateRepository(database.localApplicationStateDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun databaseInitializesWithNoApplicationState() = runBlocking {
        assertEquals(OperationResult.Success(null), repository.read())
    }

    @Test
    fun writeAndReadRoundTrip() = runBlocking {
        val expected = LocalApplicationState(
            stateVersion = 1,
            lastSynchronizedAtEpochMillis = 1234L,
            initialized = true,
        )

        assertEquals(OperationResult.Success(Unit), repository.write(expected))
        assertEquals(OperationResult.Success(expected), repository.read())
    }

    @Test
    fun updateReplacesExistingState() = runBlocking {
        repository.write(LocalApplicationState(initialized = false))

        val updated = LocalApplicationState(
            stateVersion = 1,
            lastSynchronizedAtEpochMillis = 5678L,
            initialized = true,
        )
        repository.write(updated)

        assertEquals(OperationResult.Success(updated), repository.read())
    }

    @Test
    fun clearRemovesState() = runBlocking {
        repository.write(LocalApplicationState(initialized = true))

        assertEquals(OperationResult.Success(Unit), repository.clear())
        assertEquals(OperationResult.Success(null), repository.read())
    }

    @Test
    fun observeEmitsPersistedState() = runBlocking {
        val expected = LocalApplicationState(initialized = true)
        repository.write(expected)

        assertEquals(OperationResult.Success(expected), repository.observe().first())
    }

    @Test
    fun missingRecordIsRepresentedAsNull() = runBlocking {
        assertEquals(OperationResult.Success(null), repository.read())
    }

    @Test
    fun repositoryExposesApplicationLevelStorageError() {
        val result: OperationResult<Unit> =
            OperationResult.Failure(AdminError.LocalStorage)

        assertTrue(result is OperationResult.Failure)
        assertEquals(AdminError.LocalStorage, (result as OperationResult.Failure).error)
    }
}
