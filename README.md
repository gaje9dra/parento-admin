# Parento Admin

**Parento Admin** is the Android administrator/controller application for the Parento platform.

## Phase 1.2 — Admin Android App Architecture & Internal Contracts

Phase 1.2 establishes internal contracts and domain boundaries on top of the Phase 1.1 Android foundation. It does **not** implement authentication, enrollment, device control, policy enforcement, realtime communication, or other future management features.

## Repository boundary

This repository is:

`gaje9dra/parento-admin`

Parento is intentionally separated into three repositories:

- `gaje9dra/parento-admin` — administrator/controller Android application.
- `gaje9dra/parento-backend` — API, persistence, authorization, and realtime infrastructure.
- `gaje9dra/parento-managed` — managed Android-device application.

Only this repository is modified by Phase 1.2.

## Architecture

The Admin application is organized around these responsibilities:

```text
Parento Admin
│
├── Presentation / UI
├── Authentication
├── Device Management
├── Policy Management
├── Communication
├── Security
└── Local Data
```

The domain layer remains independent of Android UI where practical.

### Authentication boundary

`auth/AuthenticationManager.kt` defines the future administrator authentication boundary. Phase 1.2 does not implement login, registration, OAuth, password storage, or fake authentication.

### Backend communication boundary

`communication/BackendClient.kt` defines future communication with `gaje9dra/parento-backend` for device listing/details, policy management, events, and later realtime integration.

No network request, production API credential, authentication flow, or realtime connection is implemented in this phase.

### Device-management boundary

`device/DeviceManager.kt` provides the future service boundary for managed-device listing and details. Pairing, enrollment, removal, remote commands, and other device-control operations remain planned functionality.

### Policy-management boundary

`policy/PolicyManager.kt` defines the future policy-management contract. Application, website, network, device, and usage policies are represented only as future domain concepts. Policy enforcement is not implemented.

### Security boundary

`security/SecurityStore.kt` is the future boundary for authenticated-session state, secure token storage, device authorization, and related security concerns. Phase 1.2 stores no credentials, tokens, keys, or production secrets.

### Local-data boundary

`data/LocalDataStore.kt` defines future persistence for non-sensitive cached application state such as managed-device data. No database or real credential persistence is implemented.

### Logging boundary

`logging/AdminLogger.kt` provides a structured logging contract. Implementations must avoid passwords, authentication tokens, private keys, API secrets, and unnecessary sensitive device information. Production logging should be controllable by the eventual implementation.

## Domain model

The Phase 1.2 foundation contains:

- `Administrator` — future authenticated administrator identity.
- `ManagedDevice` — admin-side representation of a managed device.
- `DeviceStatus` — high-level device status.
- `EnrollmentState` — enrollment lifecycle state.
- `ConnectionState` — connection lifecycle state.
- `Policy` — minimal policy identity for future management.
- `OperationResult` / `AdminError` — consistent success/failure handling.

The managed-device model currently contains only the fields needed by the specification: unique device identity, display name, connection state, enrollment state, status, optional battery percentage, and optional last synchronization timestamp.

## UI / navigation architecture

`presentation/NavigationDestination.kt` establishes future navigation destinations for:

```text
Login
  ↓
Dashboard
  ↓
Devices
  ├── Device Details
  ├── Location
  ├── Screen Session
  ├── Applications
  ├── Websites
  └── Policies
```

These are navigation contracts only. No corresponding management screens or fake controls have been added. The Phase 1.1 launch screen remains the active UI.

## Explicitly not implemented

Phase 1.2 does not implement:

- Admin registration, login, or OAuth
- Device enrollment or QR pairing
- Device authentication or remote commands
- Location
- Screen sharing
- Audio, camera, or gallery access
- Application blocking or installation blocking
- Website blocking or DNS/VPN filtering
- Device locking
- Notifications
- Production policy enforcement
- Backend requests or realtime communication

These belong to later phases.

## Testing

Meaningful unit tests cover:

- Managed-device domain representation.
- State handling.
- Error/result handling.
- Navigation contract context.
- Architectural model behavior.

No tests were added solely to inflate coverage.

## Dependencies

Phase 1.2 adds **no new Gradle dependencies**. The Phase 1.1 Android foundation dependencies remain unchanged.

## Requirements and local verification

- Android Studio with a compatible Android SDK
- JDK 17
- Android SDK Platform 36

Expected commands:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

On Windows:

```powershell
.\\gradlew.bat assembleDebug
.\\gradlew.bat test
.\\gradlew.bat lint
```

## Cross-repository requirements

### `gaje9dra/parento-backend`

Future backend work must expose authenticated, authorized contracts for administrator authentication, managed-device listing/details, policy management, events, and realtime communication. This phase does not modify that repository.

### `gaje9dra/parento-managed`

Future managed-device work must expose the device-side contracts required for authorized enrollment, status, policy application, and other explicitly permitted management functionality. This phase does not modify that repository.

## Security principles

- No production credentials or secrets are committed.
- No passwords or authentication tokens are stored by Phase 1.2.
- No hidden administrator accounts or bypass mechanisms.
- No covert surveillance implementation.
- No unnecessary sensitive Android permissions.
- Future management operations must use explicit authentication and authorization.
- Android-sensitive capabilities are introduced only with their corresponding legitimate feature and security model.

## Phase 1.2 status

Implemented: internal architecture contracts, domain foundation, error/result model, logging boundary, local-data boundary, backend boundary, device-management boundary, policy-management boundary, security boundary, and future navigation contract.

Not implemented: all future management functionality listed above.

## Phase 1.3 — Configuration, Environment & Operational Foundation

Phase 1.3 adds the configuration and operational foundation while preserving the Phase 1.2 architecture contracts. No authentication, enrollment, device control, policy enforcement, realtime communication, or other future management functionality is implemented.

### Configuration architecture

Application configuration is centralized through:

Build configuration
       |
AdminApplicationConfig
       |
AppConfig
  |-- environment
  |-- backendBaseUrl
  |-- logging
  |-- security
  `-- featureFlags

Application code should consume AppConfig rather than reading build-specific values directly.

### Build environments

| Build type | Environment | Backend endpoint | Logging | Diagnostics |
|---|---|---|---|---|
| debug | development | https://dev-backend.example.invalid | DEBUG | enabled |
| test | test | https://test-backend.example.invalid | INFO | disabled |
| release | production | https://backend.example.invalid | WARNING | disabled |

The .example.invalid endpoints are placeholders only. No production infrastructure domain or credentials are invented in this phase. The release configuration requires HTTPS.

### Feature flags

Future feature flags exist only as a lightweight configuration foundation and are disabled by default:
- authentication
- enrollment
- device communication
- location
- screen sharing
- audio
- application management
- website filtering
- device restrictions

These flags do not implement or activate those features.

### Logging

AdminLogger remains the logging boundary. AndroidAdminLogger applies the configured minimum level and enabled state centrally.

Never log:
- passwords
- authentication or refresh tokens
- private keys
- authorization headers
- enrollment or pairing secrets
- sensitive device information
- unnecessary location data
- screen contents
- microphone/audio contents

Release logging is limited to WARNING and ERROR.

### Startup

ParentoAdminApplication initializes typed configuration before the existing Admin UI is created. Startup does not authenticate, enroll devices, initialize device-control services, or contact the backend.

### Security baseline

The Admin manifest now:
- explicitly initializes ParentoAdminApplication
- keeps only the existing launcher Activity exported
- disables application backup
- disables global cleartext traffic
- adds no sensitive runtime permissions

No camera, microphone, location, screen-capture, accessibility, VPN, Device Owner, or unnecessary storage permissions were introduced.

### Configuration validation

The configuration layer rejects:
- blank backend URLs
- malformed backend URLs
- URLs without HTTP(S)
- backend URLs containing embedded credentials
- production HTTP endpoints when HTTPS is required
- unsupported environment values
- unsupported log levels

Configuration errors use generic messages and do not expose submitted credentials or other sensitive configuration values.

### Testing

Phase 1.3 adds unit tests for:
- valid configuration loading
- development/test/production separation
- invalid backend URL rejection
- embedded backend credentials rejection
- production HTTPS enforcement
- production diagnostic restrictions
- future feature flags remaining disabled

The existing JUnit 4 dependency is reused; no new testing framework was added.

### Versioning

The existing application version remains unchanged:
- version name: 0.1.0
- version code: 1

Production signing credentials are not included in source control. Real release signing must be supplied through the deployment environment.

### Development commands

    ./gradlew assembleDebug
    ./gradlew assembleTest
    ./gradlew test
    ./gradlew lint
    ./gradlew assembleRelease

On Windows, use gradlew.bat.

No secrets are required for the current phase.

### Cross-repository requirements

No other Parento repository was modified.

gaje9dra/parento-backend will later need to expose authenticated administrator API/realtime contracts and the finalized backend endpoint.

gaje9dra/parento-managed will later need to expose the managed-device contracts consumed by authorized Admin operations.

Those are documentation-level future requirements only for this phase.

### Phase 1.3 limitations

Still intentionally not implemented:
- admin registration/login
- Google OAuth
- password/JWT authentication
- refresh tokens
- managed-device enrollment or QR pairing
- device commands
- realtime communication
- push notifications
- location
- camera/microphone/audio
- screen sharing / MediaProjection
- application or website blocking
- DNS/VPN filtering
- device locking or remote wipe
- Android Enterprise / Device Owner provisioning
- policy enforcement
- audit system
- payments
- hidden monitoring
- covert surveillance
- Android security bypasses

## Phase 1.4 — Application Lifecycle, Navigation & UI Shell

Phase 1.4 establishes the Admin application startup, lifecycle-aware UI shell, root navigation, typed UI state, reusable loading/empty/error states, accessibility foundation, and a minimal dashboard. It does not implement authentication, device management, backend communication, or other future management functionality.

### UI architecture

The project was already using Android Views rather than Jetpack Compose, so Phase 1.4 preserves that framework.

```text
ParentoAdminApplication
        |
        v
    MainActivity
        |
        v
   AdminNavigator
        |
        +---- AdminHomeScreen
        |         |
        |         +---- AdminHomeViewModel
        |         |
        |         +---- AdminUiState
        |
        +---- Future placeholder destinations
```

`AdminHomeViewModel` owns a strongly typed `StateFlow<AdminUiState>`. UI rendering is driven by that state and the ViewModel has no Activity or View references.

UI states are `Loading`, `Empty`, `Content`, and `Error`. No fake network requests or fake device records are created.

`MainActivity` collects state with `repeatOnLifecycle(STARTED)`, and the ViewModel survives normal Activity recreation. Window insets are applied with `WindowInsetsCompat`.

### Admin navigation

Current root destinations are:

- Home / Dashboard — implemented shell.
- Devices — placeholder only.
- Policies — placeholder only.
- Settings — placeholder only.

The Phase 1.2 `presentation/NavigationDestination` contract remains unchanged as a future management-navigation contract.

### Initial Admin home

The home screen provides Parento Admin branding, a neutral empty managed-device state, and navigation entry points for future sections. It does not show fake devices, connection status, statistics, or backend results.

### Reusable states and accessibility

`AdminStateViews` provides accessible loading, neutral empty, and safe error states with optional retry support. Error messages are mapped from domain errors without exposing stack traces, tokens, passwords, backend secrets, or infrastructure details.

Interactive controls use a minimum 48dp height. Text remains resource-backed and supports Android font scaling. The layout avoids absolute positioning and applies system-bar insets.

### Dependencies

Phase 1.4 adds only AndroidX lifecycle runtime/ViewModel support:

- `androidx.lifecycle:lifecycle-runtime-ktx:2.9.3`
- `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.3`

No unrelated dependency upgrades were performed.

### Testing and verification

JVM tests cover root navigation defaults/transitions, typed dashboard state transitions, empty-list handling, and safe error messaging.

The repository environment available during implementation did not provide a local Android SDK/Gradle runtime, so Gradle build, unit-test execution, Android lint, static analysis, UI tests, and APK generation were not executed here. The expected commands remain:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
./gradlew assembleRelease
```

### Cross-repository requirements

No other Parento repository was modified.

`gaje9dra/parento-backend` will later need authenticated/authorized contracts for administrator sessions, managed-device listing/details, policy operations, events, and realtime communication.

`gaje9dra/parento-managed` will later need authorized managed-device contracts for enrollment, status, policy application, and other explicitly permitted management operations.

Those are documentation-level future requirements only for Phase 1.4.

## Phase 1.5 — Security Baseline, Testing Infrastructure & Phase 1 Completion

Phase 1.5 hardens the existing Admin Android foundation without implementing authentication or device-management functionality.

### Project purpose

Parento Admin is the authorized administrator/controller Android application for the Parento platform.

### Security review

- Only the launcher Activity is exported.
- No camera, microphone, location, accessibility, VPN, notification-listener, Device Owner, device-admin, or unnecessary storage permissions are declared.
- Global cleartext traffic remains disabled.
- Application backup remains disabled.
- Production configuration requires HTTPS.
- Backend URLs reject embedded credentials and unexpected path/query/fragment data.
- Production diagnostics are disabled.
- Future management feature flags remain disabled.
- No production credentials or security material are committed.

### Lifecycle

`ParentoAdminApplication` initializes configuration once at application startup.

`MainActivity` owns Activity/UI lifecycle and navigation. `AdminHomeViewModel` owns UI state and has no Activity, View, or Context reference.

`SavedStateHandle` restores lightweight UI state after Activity/process recreation. Device records are not persisted in Phase 1, so restored content safely returns to the neutral Empty state.

### UI and navigation

The existing Android Views + Material Components architecture is preserved.

Current UI states: Loading, Empty, Content, Error.

Current root destinations: Home, Devices (placeholder), Policies (placeholder), Settings (placeholder).

No fake device data, fake backend responses, or future management controls are displayed.

The UI uses system-bar insets, resource-backed dimensions, accessible loading semantics, and 48dp minimum interactive targets.

### Configuration

Build configuration is centralized through `AppConfig`.

Build environments:

| Build type | Environment | Diagnostics |
|---|---|---|
| debug | development | enabled |
| verification | test | disabled |
| release | production | disabled |

Android reserves the BuildType name `test`, so the test environment uses the `verification` build type while retaining the `TEST` application environment.

No real production backend URL is invented. Example endpoints remain placeholders.

### Testing

JUnit 4 is retained.

Phase 1.5 tests cover valid/invalid configuration, HTTPS enforcement, backend URL credential/path/query/fragment rejection, environment separation, disabled future feature flags, production diagnostic restrictions, ViewModel state transitions, SavedStateHandle restoration, navigation destination stability, and domain architecture contracts.

Tests use deterministic local values and do not require credentials, external services, or personal device state.

### CI

A minimal GitHub Actions workflow is provided at `.github/workflows/verify.yml`.

It provisions JDK 17 and Gradle 8.13 and runs:

1. unit tests
2. Android lint
3. debug build
4. release build

The workflow does not publish or deploy APKs.

The repository does not currently contain a Gradle wrapper, so CI provisions Gradle 8.13 explicitly.

### Development

Use Android Studio with Android SDK Platform 36 and JDK 17.

Expected commands when Gradle 8.13 is available:

```bash
gradle test
gradle lint
gradle assembleDebug
gradle assembleRelease
```

The test environment build type is:

```bash
gradle assembleVerification
```

No secrets are required for the current phase.

### Documentation

Added:
- `docs/phase-1-architecture.md`
- `docs/cross-repository-contracts.md`

These documents distinguish implemented foundation work from planned and intentionally deferred functionality.

### Cross-repository requirements

No other Parento repository was modified.

`gaje9dra/parento-backend` will later need authenticated/authorized administrator contracts, managed-device contracts, policy operations, events, and realtime communication.

`gaje9dra/parento-managed` will later need authorized managed-device contracts for enrollment, status, policy application, and permitted management operations.

Those are documentation-only requirements for this phase.

### Phase 1.5 boundary

Still intentionally not implemented:
- admin authentication
- Google OAuth
- password/JWT/refresh-token flows
- backend API integration
- enrollment or QR pairing
- device commands
- WebSockets or FCM
- location
- camera/microphone/audio
- screen capture/sharing
- application blocking
- website/DNS/VPN filtering
- Device Owner/Android Enterprise provisioning
- device locking
- remote wipe
- policy enforcement
- covert monitoring
- surveillance
- security bypasses

Only `gaje9dra/parento-admin` is modified by Phase 1.5.


## Phase 2.1 — Local Persistence & Data Layer Foundation

Phase 2.1 establishes the Admin application's local persistence architecture without implementing authentication, backend communication, enrollment, device management, monitoring, policies, or other future Parento functionality.

### Local persistence architecture

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

The UI and ViewModels do not access Room directly. Room-specific entities and DAOs remain inside the data layer.

### Persistence technology

The Admin app uses Android Room with SQLite. No competing persistence framework was introduced.

The database is `parento-admin.db` and is created through `LocalDatabaseFactory` using the application context.

`AdminAppContainer`, owned by `ParentoAdminApplication`, provides controlled lazy access to the database and `LocalStateRepository`.

### Initial persisted state

Only `LocalApplicationStateEntity` is created in Phase 2.1.

It contains:

- `stateVersion`
- `lastSynchronizedAtEpochMillis`
- `initialized`

No admin accounts, passwords, tokens, JWTs, managed devices, enrollment records, policies, commands, locations, media, telemetry, audit records, or notification history are persisted.

### Repository layer

`LocalStateRepository` is the persistence contract. `RoomLocalStateRepository` implements it using `LocalApplicationStateDao`.

The repository supports:

- read
- write
- clear
- reactive observation with Kotlin Flow

Persistence failures are mapped to the existing `AdminError.LocalStorage` error instead of exposing raw Room exceptions to UI code.

### Database versioning

Room database version is `1`. Schema export is configured under `schemas/`.

Destructive migration is not enabled. Future schema changes must use explicit deterministic Room migrations.

### Test database

Android instrumentation tests use an isolated in-memory Room database. The tests do not use the application's real persistent database.

The test suite covers initialization, read, write, update, clear, missing records, reactive observation, and application-level storage error handling.

### Development reset

There is no production database-reset mechanism. Test reset is performed by closing the isolated in-memory database. Production application data is never automatically deleted.

### Security and backup

Phase 2.1 does not store passwords, access tokens, refresh tokens, JWTs, or other credentials. No custom cryptography is introduced.

The existing `android:allowBackup="false"` security baseline remains active. No new Android permissions were introduced.

Future sensitive authentication/session material must use appropriate Android secure-storage facilities in its later phase.

Persistence logging does not include storage contents, credentials, or sensitive device data.

### Deferred functionality

Still deferred:

- admin authentication
- backend API communication
- WebSockets / FCM
- device enrollment and pairing
- device control
- monitoring and telemetry
- location
- camera/microphone/audio
- screen capture/sharing
- application blocking
- website/DNS/VPN filtering
- policy synchronization/enforcement
- remote commands
- notifications

### Cross-repository boundary

Only `gaje9dra/parento-admin` is modified by Phase 2.1.

Future requirements for `gaje9dra/parento-backend` and `gaje9dra/parento-managed` are documented only. Neither repository is modified by this phase.


## Phase 2.2 — Admin Local Domain Persistence

Phase 2.2 extends the Phase 2.1 Room foundation with the minimum persistent Admin application state. Only `gaje9dra/parento-admin` is modified.

### Architecture

```
UI
 ↓
ViewModel
 ↓
Domain
 ↓
Repository
 ↓
Local Data Source
 ↓
Room
```

The current project has no use-case layer, so the repository remains the domain-facing persistence boundary without adding unnecessary boilerplate.

### Persisted state

The existing singleton `local_application_state` row now retains the Phase 2.1 state plus:

- application-scoped `installationId`
- `installationCreatedAtEpochMillis`
- `setupState`
- `lastInitializedAtEpochMillis`

The installation identifier is a randomly generated UUID stored only in the app's private Room database. No IMEI, serial number, MAC address, advertising ID, or other hardware identifier is used.

Fresh local state is explicitly `UNCONFIGURED`. Authentication, accounts, sessions, credentials, and backend identity are not implemented.

### Database and migration

The Room database remains `parento-admin.db`.

- Phase 2.1: database version 1
- Phase 2.2: database version 2
- explicit `MIGRATION_1_2`
- no destructive migration fallback

The migration adds only the Phase 2.2 local-state metadata and preserves the Phase 2.1 values.

### Initialization and errors

Application startup initializes the local state through the existing application container using an application-owned IO coroutine scope. Persistence failures are mapped to `AdminError.LocalStorage`; logs do not include installation IDs or stored state.

### Backup and secure-storage boundary

The existing `android:allowBackup="false"` baseline remains unchanged. Current local state is therefore treated as installation-specific, not as a supported backup/restore mechanism. No credentials or session secrets are stored in Room. Future authentication must introduce Android-appropriate secure storage separately.

### Tests

Instrumented repository tests use an isolated in-memory Room database. Migration coverage creates the Phase 2.1 table shape, applies the explicit 1→2 migration, and verifies preservation of existing values plus safe new defaults.

### Deferred

Authentication, backend communication, enrollment, device management, monitoring, location, camera, microphone, audio, screen sharing/capture, app/site blocking, policies, notifications, and remote commands remain deferred.

## Phase 2.5 — Local Persistence Security, Testing & Phase 2 Completion

Phase 2.5 audits and hardens the existing local persistence foundation without implementing Phase 3 functionality.

Current Room schema: version 3. Migration 1→2 adds Phase 2.2 local-state metadata; migration 2→3 adds the unique installation-ID index. No destructive migration fallback is configured.

The local installation UUID is generated once and persisted in the private Room database. Repository writes are serialized for concurrent initialization. Runtime UI state is not stored in Room.

Security baseline remains minimal: application backup is disabled, cleartext traffic is disabled, only the launcher Activity is exported, no sensitive runtime permissions are declared, and no credentials/tokens/keys/raw database contents are logged or persisted.

CI verifies clean, unit tests, lint, instrumented persistence tests, verification build, debug build, and release build.

During Phase 2.5 verification, CI exposed a Room KAPT annotation-processing failure under Kotlin 2.2.20. Room processing was migrated to KSP 2.2.20-2.0.4; application architecture and runtime behavior were otherwise preserved.

See docs/phase-2.5-local-persistence-security.md and docs/phase-2-completion-checklist.md for the audit and evidence checklist.

Phase 2 remains intentionally limited to the local Admin foundation. Authentication, backend communication, enrollment, pairing, device control, monitoring, location, camera/microphone/audio, screen sharing, app/website blocking, device restrictions, remote policies, and remote audit/event systems remain deferred.

## Phase 3.1 — Admin Android Authentication Foundation

Phase 3.1 implements the Admin application's authentication boundary against the external Phase 3.1 backend contract.

Implemented:

- administrator login screen
- authentication state: Unauthenticated, Authenticating, Authenticated, AuthenticationError
- login validation and dedicated authentication repository
- backend API client for login, refresh, current-admin, and logout
- standardized backend error mapping
- encrypted session storage using Android Keystore-backed AES-GCM
- session restoration and expiration handling
- refresh-and-verify flow for invalid access credentials
- logout with backend revocation when available and local session clearing
- authentication-aware application navigation
- authenticated shell using the existing Admin UI architecture
- unit coverage for valid login, invalid credentials, session expiry, refresh restoration, and logout
- authentication cross-repository contract documentation

The local Admin installation UUID remains in the existing Room boundary and is never used as the authenticated administrator identity.

Authentication credentials are not stored in Room, plain SharedPreferences, source code, URLs, or logs. Password input is transient and is cleared from the UI after submission.

The backend remains an external dependency. No changes are made to gaje9dra/parento-backend or gaje9dra/parento-managed.

### Phase 3.1 security boundary

Release builds retain HTTPS-only transport and cleartext traffic disabled. The authentication client does not disable TLS validation, accept arbitrary certificates, or provide HTTP fallback.

No managed-device enrollment, pairing, device synchronization, realtime communication, FCM, WebSockets, device monitoring, location, camera, microphone, audio, screen sharing, app/site blocking, device restrictions, remote policies, or remote commands are implemented.

## Phase 3.2 — Admin Authentication Hardening & Session Security

Phase 3.2 hardens the existing Phase 3.1 Android authentication boundary without adding device-management functionality.

### Authentication state

The Admin app uses one explicit authentication state machine:
- Unauthenticated
- Authenticating
- Authenticated
- AuthenticationError
- SessionExpired
- SessionRevoked
- AccountDisabled

Authentication, session restoration, foreground validation, and logout operations are serialized so duplicate login submissions and logout/login races cannot leave the UI in an inconsistent authenticated state.

### Secure session storage

Authentication credentials are stored only inside the existing SecureSessionStore.

The store uses Android Keystore-backed AES-GCM encryption and private application SharedPreferences only as encrypted ciphertext storage. Passwords are never persisted. Access and refresh tokens are not stored in Room, URLs, logs, or screenshots.

Malformed encrypted session data is discarded deterministically. Logout removes the encrypted session synchronously from local storage.

The existing local installation UUID remains in Room and is not used as administrator identity.

### Authenticated request boundary

AuthenticationApiClient is the single authentication API boundary. Login, refresh, current-admin, and logout are centralized there.

Bearer credentials are attached only by this client. Feature-specific UI code does not construct authorization headers.

The client validates required response fields and token lengths, maps authentication HTTP failures into domain errors, treats malformed successful responses as authentication failures, and does not retry invalid credentials automatically.

### Session expiration and unauthorized responses

A rejected current-admin request causes the repository to invalidate the local session when the failure represents revocation, account disablement, or authorization rejection.

For an expired or invalid access credential, the repository performs one refresh attempt using the existing refresh endpoint. The refreshed session is immediately verified through current-admin.

There is no infinite retry loop and no reuse of an invalid access credential.

If refresh fails, the local session is cleared and the application returns to the unauthenticated flow.

The app also validates an authenticated session when returning to the foreground. This is a server-backed check rather than a local timer and prevents stale authenticated UI from surviving indefinitely.

### Logout

Logout attempts backend revocation using the existing endpoint, but local logout is not dependent on network success.

The local encrypted session is cleared regardless of backend logout success. The UI always transitions to Unauthenticated.

### Navigation guard

MainActivity renders the authenticated Admin shell only for AuthenticationState.Authenticated.

All unauthenticated, expired, revoked, and disabled states render the login flow. Protected destinations cannot remain active after the authentication state leaves Authenticated.

The Activity also uses FLAG_SECURE to prevent ordinary screenshots and screen capture of the Admin UI.

### Login hardening

The login UI masks passwords, clears the password field after submission, disables sign-in while authentication is in progress, prevents duplicate login operations at the ViewModel boundary, preserves the non-sensitive email draft when the login view is recreated, and never persists the password.

### Backend contract dependency

The Admin app consumes the existing Phase 3.1 backend contract:
- POST /api/v1/auth/admin/login
- POST /api/v1/auth/admin/refresh
- GET /api/v1/auth/admin/me
- POST /api/v1/auth/admin/logout

The backend currently represents invalid, expired, and revoked authenticated sessions as HTTP 401 with the generic AUTHENTICATION_REQUIRED error. Therefore the Android app cannot truthfully distinguish every 401 as expired versus revoked.

The current backend login contract also returns INVALID_CREDENTIALS for disabled administrators. The Android app supports an explicit ACCOUNT_DISABLED contract if the backend later exposes one, but it does not invent that distinction locally.

No backend repository was modified for Phase 3.2 Admin work.

### Testing scope

Phase 3.2 strengthens coverage for expired sessions, refresh and current-admin verification, local logout when backend logout fails, authentication lifecycle states, serialized authentication operations, malformed encrypted-session cleanup, unauthorized/forbidden error mapping, and secure session restoration.

Android build, JVM tests, instrumentation tests, and lint must be run through the repository's Gradle/CI environment before release. No generated secrets or signing material are committed.

### Deferred

Phase 3.2 does not implement enrollment, QR pairing, managed-device lists, remote commands, location, camera, microphone, audio streaming, screen sharing, application blocking, website filtering, device locking, remote wipe, policy management, notification control, monitoring, or covert surveillance.
