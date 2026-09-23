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
