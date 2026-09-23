package com.parento.admin.data

import com.parento.admin.data.local.LocalApplicationStateDao
import com.parento.admin.data.local.LocalApplicationStateEntity
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.AdminLocalSetupState
import com.parento.admin.domain.OperationResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface LocalStateRepository {
    suspend fun read(): OperationResult<LocalApplicationState?>
    suspend fun write(state: LocalApplicationState): OperationResult<Unit>
    suspend fun clear(): OperationResult<Unit>
    fun observe(): Flow<OperationResult<LocalApplicationState?>>
    suspend fun getLocalInstallationId(): OperationResult<String>
    suspend fun initializeLocalState(initializedAtEpochMillis: Long): OperationResult<LocalApplicationState>
}

class RoomLocalStateRepository(
    private val dao: LocalApplicationStateDao,
) : LocalStateRepository {
    override suspend fun read(): OperationResult<LocalApplicationState?> =
        runStorageOperation { dao.read()?.toDomain() }

    override suspend fun write(state: LocalApplicationState): OperationResult<Unit> =
        runStorageOperation { dao.upsert(state.toEntity()) }

    override suspend fun clear(): OperationResult<Unit> =
        runStorageOperation { dao.clear() }

    override fun observe(): Flow<OperationResult<LocalApplicationState?>> =
        dao.observe().map { entity ->
            try {
                OperationResult.Success(entity?.toDomain())
            } catch (_: Exception) {
                OperationResult.Failure(AdminError.LocalStorage)
            }
        }

    override suspend fun getLocalInstallationId(): OperationResult<String> =
        runStorageOperation {
            val current = dao.read()
            val existingId = current?.installationId?.takeIf { it.isNotBlank() }
            if (existingId != null) {
                existingId
            } else {
                val now = System.currentTimeMillis()
                val installationId = UUID.randomUUID().toString()
                val state = current?.copy(
                    installationId = installationId,
                    installationCreatedAtEpochMillis =
                        current.installationCreatedAtEpochMillis ?: now,
                ) ?: LocalApplicationStateEntity(
                    installationId = installationId,
                    installationCreatedAtEpochMillis = now,
                )
                dao.upsert(state)
                installationId
            }
        }

    override suspend fun initializeLocalState(
        initializedAtEpochMillis: Long,
    ): OperationResult<LocalApplicationState> =
        runStorageOperation {
            val current = dao.read()
            val installationId = current?.installationId?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString()
            val installationCreatedAt = current?.installationCreatedAtEpochMillis
                ?: initializedAtEpochMillis

            val state = (current ?: LocalApplicationStateEntity()).copy(
                installationId = installationId,
                installationCreatedAtEpochMillis = installationCreatedAt,
                initialized = true,
                lastInitializedAtEpochMillis = initializedAtEpochMillis,
                setupState = current?.setupState
                    ?: LocalApplicationStateEntity.DEFAULT_SETUP_STATE,
            )
            dao.upsert(state)
            state.toDomain()
        }

    private suspend fun <T> runStorageOperation(
        operation: suspend () -> T,
    ): OperationResult<T> =
        try {
            OperationResult.Success(operation())
        } catch (_: Exception) {
            OperationResult.Failure(AdminError.LocalStorage)
        }
}

private fun LocalApplicationStateEntity.toDomain(): LocalApplicationState {
    val parsedSetupState = runCatching {
        AdminLocalSetupState.valueOf(setupState)
    }.getOrElse {
        throw IllegalStateException("Invalid local setup state")
    }

    return LocalApplicationState(
        stateVersion = stateVersion,
        lastSynchronizedAtEpochMillis = lastSynchronizedAtEpochMillis,
        initialized = initialized,
        installationId = installationId,
        installationCreatedAtEpochMillis = installationCreatedAtEpochMillis,
        setupState = parsedSetupState,
        lastInitializedAtEpochMillis = lastInitializedAtEpochMillis,
    )
}

private fun LocalApplicationState.toEntity() =
    LocalApplicationStateEntity(
        stateVersion = stateVersion,
        lastSynchronizedAtEpochMillis = lastSynchronizedAtEpochMillis,
        initialized = initialized,
        installationId = installationId,
        installationCreatedAtEpochMillis = installationCreatedAtEpochMillis,
        setupState = setupState.name,
        lastInitializedAtEpochMillis = lastInitializedAtEpochMillis,
    )
