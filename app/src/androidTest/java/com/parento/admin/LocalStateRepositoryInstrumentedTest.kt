package com.parento.admin

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parento.admin.data.LocalApplicationState
import com.parento.admin.data.RoomLocalStateRepository
import com.parento.admin.data.local.LocalApplicationStateEntity
import com.parento.admin.data.local.ParentoAdminDatabase
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.AdminLocalSetupState
import com.parento.admin.domain.OperationResult
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun localInstallationIdIsCreatedAndStable() = runBlocking {
        val first = repository.getLocalInstallationId()
        val second = repository.getLocalInstallationId()

        assertTrue(first is OperationResult.Success)
        assertTrue(second is OperationResult.Success)
        val firstId = (first as OperationResult.Success).value
        val secondId = (second as OperationResult.Success).value
        assertEquals(firstId, secondId)
        UUID.fromString(firstId)
    }

    @Test
    fun initializePersistsIdentityAndSafeInitialState() = runBlocking {
        val initializedAt = 1234L
        val result = repository.initializeLocalState(initializedAt)

        assertTrue(result is OperationResult.Success)
        val state = (result as OperationResult.Success).value
        assertNotNull(state.installationId)
        assertEquals(AdminLocalSetupState.UNCONFIGURED, state.setupState)
        assertTrue(state.initialized)
        assertEquals(initializedAt, state.lastInitializedAtEpochMillis)
        assertEquals(OperationResult.Success(state), repository.read())
    }

    @Test
    fun writeAndReadRoundTripPreservesPhase22State() = runBlocking {
        val expected = LocalApplicationState(
            initialized = true,
            installationId = "installation-test",
            installationCreatedAtEpochMillis = 1000L,
            setupState = AdminLocalSetupState.UNCONFIGURED,
            lastInitializedAtEpochMillis = 1234L,
        )

        assertEquals(OperationResult.Success(Unit), repository.write(expected))
        assertEquals(OperationResult.Success(expected), repository.read())
    }

    @Test
    fun observeEmitsPersistedState() = runBlocking {
        val expected = LocalApplicationState(
            initialized = true,
            installationId = "installation-observed",
        )
        repository.write(expected)

        assertEquals(OperationResult.Success(expected), repository.observe().first())
    }

    @Test
    fun invalidPersistedSetupStateMapsToLocalStorageError() = runBlocking {
        database.localApplicationStateDao().upsert(
            LocalApplicationStateEntity(setupState = "INVALID"),
        )

        val result = repository.read()

        assertTrue(result is OperationResult.Failure)
        assertEquals(AdminError.LocalStorage, (result as OperationResult.Failure).error)
    }

    @Test
    fun clearRemovesState() = runBlocking {
        repository.write(LocalApplicationState(initialized = true))

        assertEquals(OperationResult.Success(Unit), repository.clear())
        assertEquals(OperationResult.Success(null), repository.read())
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
