# Parento Admin — Phase 1 Architecture

## Scope

This repository contains only the Parento administrator/controller Android application. Phase 1 establishes the secure application shell and architecture boundaries; authentication and device-management functionality remain deferred.

## Application lifecycle

ParentoAdminApplication initializes typed configuration before MainActivity creates the UI. MainActivity owns Activity and navigation lifecycle concerns. AdminHomeViewModel owns UI state and has no Activity, View, or Context reference.

`AdminHomeViewModel` uses `SavedStateHandle` for lifecycle-safe state restoration. Error/loading/empty state can be restored; device content is intentionally not persisted because Phase 1 has no device-data persistence.

## Configuration

`AppEnvironment`, `AppConfig`, `BuildConfiguration`, and `AdminApplicationConfig` form the centralized configuration boundary.

Build environments are development, test, and production. Build type `testing` maps to the test environment because Android reserves the `test` BuildType name.

Production requires HTTPS. Backend URLs are validated as origins and reject embedded credentials, paths, query parameters, and fragments. No real production endpoint or secret is committed.

Future management feature flags are present only as disabled configuration boundaries.

## UI

The existing Android Views + Material Components architecture is preserved.

The root UI contains a minimal Admin dashboard with typed `Loading`, `Empty`, `Content`, and `Error` states. No fake device data or backend responses are generated.

Navigation destinations currently exposed by the UI are Home, Devices, Policies, and Settings. Devices, Policies, and Settings are placeholders only.

Interactive controls use a minimum 48dp touch target. Loading content has accessibility descriptions. Layouts use resource dimensions and system-bar insets rather than fixed screen coordinates.

## Navigation

`AdminDestination` and `AdminNavigator` provide the root navigation boundary. Back navigation returns from placeholder destinations to Home and exits the Activity from Home.

Future management navigation remains represented by the Phase 1 architecture contract but is not implemented as functional screens.

## ViewModels and state management

`AdminHomeViewModel` exposes `StateFlow<AdminUiState>` and explicit transition methods. The UI collects state with `repeatOnLifecycle(STARTED)`.

`SavedStateHandle` restores valid lightweight UI state after recreation. Missing or invalid saved state falls back to the neutral Empty state.

## Logging

`AdminLogger` and `AndroidAdminLogger` are the application logging boundary. Logging level and enabled state are configuration-controlled. Release configuration uses WARNING as its minimum level.

Application code must never pass passwords, authentication tokens, refresh tokens, API keys, private keys, authorization headers, enrollment secrets, or unnecessary sensitive device information to the logger.

Current startup logging contains only a static initialization message.

## Security boundaries

- Only the launcher Activity is exported.
- No sensitive Android permissions are declared.
- Global cleartext traffic is disabled.
- Application backup is disabled.
- Production configuration requires HTTPS.
- Backend configuration rejects embedded credentials and unexpected URL path/query/fragment data.
- Production diagnostics are disabled.
- Future management feature flags default to disabled.
- No authentication or enrollment secrets exist in source.
- No device-management or surveillance APIs are initialized.

## Testing

JUnit 4 remains the testing framework.

Tests cover configuration validity, invalid configuration, environment separation, HTTPS enforcement, backend-origin validation, future feature flags, security configuration, ViewModel state transitions, SavedStateHandle restoration, navigation destination stability, and domain architecture contracts.

Tests use deterministic local values only. No production credentials, external services, or personal device state are required.

## Implemented

- Android foundation and existing architecture
- centralized configuration and environment separation
- controlled logging boundary
- manifest/network security baseline
- lifecycle-safe Admin UI shell
- root navigation
- typed UI state
- SavedStateHandle state restoration
- JVM tests
- minimal GitHub Actions verification workflow

## Planned

Later phases may add authenticated administrator sessions, backend communication, enrollment, managed-device status, policy management, and authorized management operations.

## Intentionally deferred

- admin authentication
- Google OAuth
- password/JWT/refresh-token flows
- backend API integration
- enrollment and QR pairing
- device commands
- WebSockets and FCM
- location
- camera/microphone/audio
- screen capture/sharing
- application blocking
- Play Store restrictions
- website/DNS/VPN filtering
- Device Owner/Android Enterprise provisioning
- device locking
- remote wipe
- policy enforcement
- covert monitoring
- surveillance
- permission or security bypasses

## Cross-repository relationship

Parento Admin -> Parento Backend <- Parento Managed

The Admin app will eventually authenticate and communicate with the backend and consume backend-mediated managed-device state. Those protocols are documented only and are not implemented in Phase 1.5.

Only `gaje9dra/parento-admin` is modified by this phase.

## Phase 2.1 — Local Persistence & Data Layer Foundation

Phase 2.1 adds the Admin application's local persistence foundation only.

### Persistence architecture

```text
UI
 ↓
ViewModel
 ↓
Domain / Use Case
 ↓
Repository Interface
 ↓
Repository Implementation
 ↓
Local Data Source / DAO
 ↓
Room Database
```

The UI and ViewModels do not access Room directly. Room implementation details remain inside the data layer.

### Database

Room + SQLite is the local persistence technology for this phase.

`ParentoAdminDatabase` is version 1 and contains only `LocalApplicationStateEntity`.

The baseline state contains `stateVersion`, `lastSynchronizedAtEpochMillis`, and `initialized`.

No authentication, device, enrollment, policy, command, telemetry, location, media, or audit entities are persisted.

`LocalDatabaseFactory` creates the application database using `applicationContext`. `AdminAppContainer`, owned by `ParentoAdminApplication`, provides controlled lazy access to the database and repository.

### Repository

`LocalStateRepository` is the domain-facing persistence contract.

`RoomLocalStateRepository` is its Room-backed implementation.

The repository exposes read, write, clear, and Flow observation. Room entities and DAO types do not cross into UI code.

Persistence failures are mapped to the existing `AdminError.LocalStorage` application-level error.

### Threading

DAO reads/writes are suspend functions and reactive reads use Kotlin Flow. Production code does not perform synchronous database operations from the UI layer and no arbitrary thread pool was introduced.

### Migration

Room schema versioning starts at version 1 with schema export configured under `schemas/`.

No destructive migration is enabled. Future schema changes must increment the version and provide explicit deterministic migrations.

### Test storage

Android instrumentation tests use an isolated in-memory Room database. They never use the application's real persistent database.

Tests cover database initialization, read/write, update, clear, missing-record behavior, Flow observation, and application-level storage error handling.

### Security and backup

Phase 2.1 creates no credential or authentication storage. Passwords, JWTs, access tokens, refresh tokens, and secrets are not persisted.

Android backup remains disabled from the existing security baseline. No new Android permissions were introduced.

Future sensitive session/credential material must use appropriate Android secure-storage mechanisms in its later phase. No custom cryptography is introduced.

### Development reset

No production database-reset API is exposed. Test reset is achieved by closing and discarding the isolated in-memory database. Production data is never automatically deleted.

### Deferred functionality

Phase 2.1 does not implement authentication, backend communication, enrollment, device management, monitoring, screen sharing, audio, camera, application blocking, website blocking, policy management, notifications, or other future Parento features.

Only `gaje9dra/parento-admin` is modified by this phase.
