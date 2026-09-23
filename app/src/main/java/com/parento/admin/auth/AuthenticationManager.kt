package com.parento.admin.auth

import com.parento.admin.domain.OperationResult

/**
 * Administrator authentication boundary used by future higher-level consumers.
 * Concrete credential/session behavior is provided by AuthenticationRepository.
 */
interface AuthenticationManager {
    suspend fun currentAdministrator(): OperationResult<AuthenticatedAdmin?>
}
