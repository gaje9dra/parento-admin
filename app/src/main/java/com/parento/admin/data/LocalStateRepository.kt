package com.parento.admin.data

import com.parento.admin.data.local.LocalApplicationStateDao
import com.parento.admin.data.local.LocalApplicationStateEntity
import com.parento.admin.domain.AdminError
import com.parento.admin.domain.AdminLocalSetupState
import com.parento.admin.domain.OperationResult
import com.parento.admin.domain.canTransitionTo
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface LocalStateRepository {
    suspend fun read(): OperationResult<LocalApplicationState?>
    suspend fun write(state: LocalApplicationState): OperationResult<Unit>
    suspend fun clear(): OperationResult<Unit>
    fun observe(): Flow<OperationResult<LocalApplicationState?>>
    suspend fun getLocalInstallationId(): OperationResult<String>
    suspend fun initializeLocalState(initializedAtEpochMillis: Long): OperationResult<LocalApplicationState>
    suspend fun updateSetupState(state: AdminLocalSetupState): OperationResult<LocalApplicationState>
}

class RoomLocalStateRepository(
    private val dao: LocalApplicationStateDao,
) : LocalStateRepository {
    private val writeMutex = Mutex()

    override suspend fun read(): OperationResult<LocalApplicationState?> =
        runStorageOperation { dao.read()?.toDomain() }

    override suspend fun write(state: LocalApplicationState): OperationResult<Unit> =
        writeMutex.withLock { runStorageOperation { dao.upsert(state.toEntity()) } }

    override suspend fun clear(): OperationResult<Unit> =
        writeMutex.withLock { runStorageOperation { dao.clear() } }

    override fun observe(): Flow<OperationResult<LocalApplicationState?>> =
        dao.observe().map { entity ->
            try {
                OperationResult.Success(entity?.toDomain())
            } catch (_: Exception) {
                OperationResult.Failure(AdminError.LocalStorage)
            }
        }

    override suspend fun getLocalInstallationId(): OperationResult<String> =
        writeMutex.withLock {
            runStorageOperation {
                val current = dao.read()
                current?.installationId?.takeIf { it.isNotBlank() }?.also {
                    if (!isValidInstallationId(it)) {
                        throw IllegalStateException("Invalid persisted installation identity")
                    }
                    if (current.installationCreatedAtEpochMillis != null &&
                        current.installationCreatedAtEpochMillis <= 0L
                    ) {
                        throw IllegalStateException("Invalid persisted installation timestamp")
                    }
                } ?: run {
                    val now = System.currentTimeMillis()
                    val id = UUID.randomUUID().toString()
                    dao.upsert(
                        (current ?: LocalApplicationStateEntity()).copy(
                            installationId = id,
                            installationCreatedAtEpochMillis =
                                current?.installationCreatedAtEpochMillis ?: now,
                        ),
                    )
                    id
                }
            }
        }

    override suspend fun initializeLocalState(
        initializedAtEpochMillis: Long,
    ): OperationResult<LocalApplicationState> =
        writeMutex.withLock {
            runStorageOperation {
                val current = dao.read()
                val id = current?.installationId?.takeIf { it.isNotBlank() }?.also {
                    if (!isValidInstallationId(it)) {
                        throw IllegalStateException("Invalid persisted installation identity")
                    }
                } ?: UUID.randomUUID().toString()
                val createdAt = current?.installationCreatedAtEpochMillis?.also {
                    if (it <= 0L) {
                        throw IllegalStateException("Invalid persisted installation timestamp")
                    }
                } ?: initializedAtEpochMillis
                val updated = (current ?: LocalApplicationStateEntity()).copy(
                    installationId = id,
                    installationCreatedAtEpochMillis = createdAt,
                    initialized = true,
                    lastInitializedAtEpochMillis = initializedAtEpochMillis,
                    setupState = current?.setupState
                        ?: LocalApplicationStateEntity.DEFAULT_SETUP_STATE,
                )
                dao.upsert(updated)
                updated.toDomain()
            }
        }

    override suspend fun updateSetupState(
        state: AdminLocalSetupState,
    ): OperationResult<LocalApplicationState> =
        writeMutex.withLock {
            runStorageOperation {
                val current = dao.read() ?: LocalApplicationStateEntity()
                val domain = current.toDomain()
                if (!domain.setupState.canTransitionTo(state)) {
                    throw InvalidLocalStateTransitionException()
                }
                val updated = current.copy(setupState = state.name)
                dao.upsert(updated)
                updated.toDomain()
            }
        }

    private suspend fun <T> runStorageOperation(operation: suspend () -> T): OperationResult<T> =
        try {
            OperationResult.Success(operation())
        } catch (_: InvalidLocalStateTransitionException) {
            OperationResult.Failure(AdminError.InvalidState)
        } catch (_: Exception) {
            OperationResult.Failure(AdminError.LocalStorage)
        }
}

private class InvalidLocalStateTransitionException : IllegalStateException()

private fun LocalApplicationStateEntity.toDomain(): LocalApplicationState {
    if (!installationId.isNullOrBlank() && !isValidInstallationId(installationId)) {
        throw IllegalStateException("Invalid persisted installation identity")
    }
    if (installationCreatedAtEpochMillis != null && installationCreatedAtEpochMillis <= 0L) {
        throw IllegalStateException("Invalid persisted installation timestamp")
    }

    return LocalApplicationState(
    LocalApplicationState(
        stateVersion = stateVersion,
        lastSynchronizedAtEpochMillis = lastSynchronizedAtEpochMillis,
        initialized = initialized,
        installationId = installationId,
        installationCreatedAtEpochMillis = installationCreatedAtEpochMillis,
        setupState = runCatching { AdminLocalSetupState.valueOf(setupState) }
            .getOrElse { throw IllegalStateException("Invalid local setup state") },
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


private fun isValidInstallationId(value: String): Boolean =
    runCatching { UUID.fromString(value) }.isSuccess
