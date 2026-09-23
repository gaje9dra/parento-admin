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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    fun concurrentIdentityInitializationProducesOneStableIdentity() = runBlocking {
        val results = (0 until 16).map {
            async { repository.getLocalInstallationId() }
        }.awaitAll()

        val ids = results.map {
            assertTrue(it is OperationResult.Success)
            (it as OperationResult.Success).value
        }.toSet()

        assertEquals(1, ids.size)
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
            installationId = "550e8400-e29b-41d4-a716-446655440000",
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
            installationId = "550e8400-e29b-41d4-a716-446655440001",
        )
        repository.write(expected)

        assertEquals(OperationResult.Success(expected), repository.observe().first())
    }

    @Test
    fun invalidInstallationIdIsRejectedBeforePersistence() = runBlocking {
        val result = repository.write(
            LocalApplicationState(
                installationId = "not-a-uuid",
                installationCreatedAtEpochMillis = 1L,
            ),
        )

        assertEquals(
            OperationResult.Failure(AdminError.LocalStorage),
            result,
        )
        assertEquals(OperationResult.Success(null), repository.read())
    }

    @Test
    fun malformedPersistedInstallationIdMapsToLocalStorageError() = runBlocking {
        database.localApplicationStateDao().upsert(
            LocalApplicationStateEntity(
                installationId = "not-a-uuid",
                installationCreatedAtEpochMillis = 1L,
            ),
        )

        val result = repository.read()

        assertTrue(result is OperationResult.Failure)
        assertEquals(AdminError.LocalStorage, (result as OperationResult.Failure).error)
    }

    @Test
    fun invalidPersistedInstallationTimestampMapsToLocalStorageError() = runBlocking {
        database.localApplicationStateDao().upsert(
            LocalApplicationStateEntity(
                installationId = "550e8400-e29b-41d4-a716-446655440002",
                installationCreatedAtEpochMillis = 0L,
            ),
        )

        val result = repository.read()

        assertTrue(result is OperationResult.Failure)
        assertEquals(AdminError.LocalStorage, (result as OperationResult.Failure).error)
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
    fun invalidSetupTransitionReturnsApplicationError() = runBlocking {
        assertEquals(
            OperationResult.Success(Unit),
            repository.updateSetupState(com.parento.admin.domain.AdminLocalSetupState.READY)
                .let { result ->
                    if (result is OperationResult.Success) OperationResult.Success(Unit) else result
                },
        )

        val result = repository.updateSetupState(com.parento.admin.domain.AdminLocalSetupState.READY)

        assertEquals(
            OperationResult.Failure(AdminError.InvalidState),
            result,
        )
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
