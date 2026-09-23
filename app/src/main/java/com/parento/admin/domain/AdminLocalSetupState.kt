package com.parento.admin.domain

/**
 * Minimal local lifecycle state for the Admin application.
 *
 * Authentication is intentionally not implemented in Phase 2.2. A fresh
 * installation therefore remains UNCONFIGURED until a later authentication
 * phase explicitly changes this state.
 */
enum class AdminLocalSetupState {
    UNCONFIGURED,
}
