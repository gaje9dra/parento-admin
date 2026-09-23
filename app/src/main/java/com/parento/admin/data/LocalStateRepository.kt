package com.parento.admin.data

import com.parento.admin.domain.AdminError
import com.parento.admin.domain.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.parento.admin.data.local.LocalApplicationStateDao
import com.parento.admin.data.local.LocalApplicationStateEntity

interface LocalStateRepository {
    suspend fun read(): OperationResult<LocalApplicationState?>

    suspend fun write(state: LocalApplicationState): OperationResult<Unit>

    suspend fun clear(): OperationResult<Unit>

    fun observe(): Flow<OperationResult<LocalApplicationState?>>
}

class RoomLocalStateRepository(
    private val dao: LocalApplicationStateDao,
) : LocalStateRepository {
    override suspend fun read(): OperationResult<LocalApplicationState?> =
        runStorageOperation { dao.read()?.toDomain() }

    override suspend fun write(
        state: LocalApplicationState,
    ): OperationResult<Unit> =
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

    private suspend fun <T> runStorageOperation(
        operation: suspend () -> T,
    ): OperationResult<T> =
        try {
            OperationResult.Success(operation())
        } catch (_: Exception) {
            OperationResult.Failure(AdminError.LocalStorage)
        }
}

private fun LocalApplicationStateEntity.toDomain() =
    LocalApplicationState(
        stateVersion = stateVersion,
        lastSynchronizedAtEpochMillis = lastSynchronizedAtEpochMillis,
        initialized = initialized,
    )

private fun LocalApplicationState.toEntity() =
    LocalApplicationStateEntity(
        stateVersion = stateVersion,
        lastSynchronizedAtEpochMillis = lastSynchronizedAtEpochMillis,
        initialized = initialized,
    )
