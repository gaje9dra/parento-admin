# Phase 2.5 — Admin Android App: Local Persistence Security, Testing & Phase 2 Completion

Repository boundary: only gaje9dra/parento-admin.

## Scope

Phase 2.5 audits and hardens the Phase 1.x–2.4 Admin Android foundation. It does not implement authentication, backend communication, enrollment, pairing, device control, monitoring, location, camera, microphone, audio, screen sharing, app/website blocking, device restrictions, remote policies, or remote audit/event functionality.

## Architecture audit

UI → ViewModel → Domain → LocalStateRepository → DAO → Room → SQLite

The UI and ViewModels do not access Room directly. Persistence is centralized in RoomLocalStateRepository, and database creation is centralized in LocalDatabaseFactory/AdminAppContainer.

## Persistence and state

The Admin app persists only minimal local application state: singleton state, state version, initialization metadata, installation UUID and creation timestamp, local setup state (UNCONFIGURED / READY), and synchronization metadata.

Runtime UI state remains outside Room. Installation identity creation is serialized by the repository mutex and is idempotent across repeated/concurrent initialization.

## Room and migrations

Current Room schema version: 3.

- 1 → 2: adds Phase 2.2 local-state fields and preserves existing data.
- 2 → 3: adds the unique installation-ID index.
- No destructive migration fallback is configured.
- Instrumented tests use isolated databases rather than the production database.

## Security audit

- android:allowBackup="false" remains enabled.
- android:usesCleartextTraffic="false" remains enabled.
- Only the launcher Activity is exported.
- No sensitive runtime permissions are declared.
- No credentials, passwords, tokens, keys, or raw database contents are persisted/logged.
- The installation UUID is not an authentication credential.
- Authentication, backend, device, and policy interfaces remain architectural boundaries only.
- Production configuration requires HTTPS and disables debug diagnostics.
- No custom cryptography or security bypasses are introduced.

## Concurrency and recovery

Repository write operations are serialized with a coroutine Mutex. Repeated identity/state initialization is safe and does not intentionally create duplicate singleton records. Malformed persisted setup-state values map to AdminError.LocalStorage; invalid lifecycle transitions map to AdminError.InvalidState. Production data is not silently deleted to recover from failures.

## Testing

Coverage includes local-state lifecycle, installation identity stability and concurrency, persistence round trips, Flow observation, malformed state, invalid transitions, database isolation, migration behavior, UI/ViewModel contracts, configuration, and security boundaries.

CI runs clean, unit tests, lint, instrumented persistence tests, verification build, debug build, and release build.

## Backup and restore

Backup is disabled because the current local state contains installation-specific identity. Phase 2 does not implement credential backup/restore or device enrollment. Future restoration behavior must be designed in later phases.

## Dependency/build correction

CI exposed an annotation-processing failure in kaptReleaseKotlin while Room 2.8.5 was processed through KAPT under Kotlin 2.2.20. Phase 2.5 moves Room 2.8.5 processing to KSP 2.2.20-2.0.4. This is limited to the build/annotation-processing boundary and does not change application architecture.

## Current limitations

Phase 2 remains a local foundation. It does not provide administrator authentication, backend communication, enrollment, pairing, managed-device synchronization, remote commands, monitoring, location, media capture, application/website blocking, device restrictions, remote policies, or remote audit/event systems.

## Completion evidence

Phase 2 is complete only when the latest GitHub Actions run for the current commit passes every required verification step. A pending or failed run must not be described as complete.