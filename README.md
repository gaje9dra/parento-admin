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
| testing | test | disabled |
| release | production | disabled |

Android reserves the BuildType name `test`, so the test environment uses the `testing` build type while retaining the `TEST` application environment.

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
gradle assembleTesting
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
