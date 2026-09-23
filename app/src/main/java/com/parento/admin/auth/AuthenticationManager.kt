package com.parento.admin.auth

import com.parento.admin.domain.Administrator
import com.parento.admin.domain.OperationResult

/**
 * Boundary for future administrator authentication.
 *
 * Implementations may be added in a later phase. No credentials or authentication
 * behavior are stored or executed by this Phase 1.2 contract.
 */
interface AuthenticationManager {
    fun currentAdministrator(): OperationResult<Administrator?>
}
