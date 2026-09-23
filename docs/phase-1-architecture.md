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