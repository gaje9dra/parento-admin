# Parento Admin

**Parento Admin** is the Android administrator/controller application for the Parento platform.

## Architecture

Parento consists of three strictly separated components:

```text
Parento Admin
      ↓
Parento Backend
      ↓
Parento Managed
```

- `gaje9dra/parento-admin` — this Android administrator/controller application.
- `gaje9dra/parento-backend` — trusted API, persistence, realtime, and authorization layer.
- `gaje9dra/parento-managed` — Android application installed on the explicitly enrolled/authorized managed device.

The Admin app will eventually communicate with Managed devices through the backend. It does not directly establish arbitrary device-control connections.

## Phase 1.1 scope

This phase establishes only a clean Android application foundation:

- Reproducible Gradle/Android configuration
- Standard application identity
- Minimal launchable UI
- Unit-test foundation
- Documentation and security boundaries

The following are intentionally **not implemented**:

- Administrator authentication or account management
- Device enrollment or pairing
- Managed-device list/details/status
- Location or location history
- Screen sharing
- Audio or camera functionality
- Application inventory
- Application blocking or installation restrictions
- Website/network filtering
- Device restrictions or device lock
- Policy management
- Realtime communication
- Notifications
- Audit/security events
- Backend API integration
- Device commands

No fake controls imply that these features are available.

## Project structure

```text
app/
  src/
    main/
      java/com/parento/admin/
        MainActivity.kt
      res/
        values/
          strings.xml
          themes.xml
      AndroidManifest.xml
    test/
      java/com/parento/admin/
        ExampleUnitTest.kt
```

The future architecture will separate UI, authentication, device management, policy management, communication, security, and local data. Phase 1.1 does not create speculative implementation classes for those systems.

## Requirements

- Android Studio with a compatible Android SDK
- JDK 17
- Android SDK Platform 36

## Local development

Open the repository in Android Studio and allow Gradle to sync.

Build from a terminal:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

Run unit tests:

```bash
./gradlew test
```

Run lint:

```bash
./gradlew lint
```

## Security principles

- No passwords, API keys, private keys, or production credentials are stored in source.
- No hidden administrator accounts or backdoors.
- No arbitrary remote-control mechanism.
- No covert surveillance functionality.
- Future management operations must authenticate and authorize the administrator through the backend.
- Sensitive Android permissions are introduced only when their corresponding feature is legitimately implemented.

## Backend boundary

Future Admin-app communication will use:

`gaje9dra/parento-backend`

Phase 1.1 deliberately does not authenticate, call, or open a realtime connection to the backend.

## Managed-device boundary

Future managed-device operations target:

`gaje9dra/parento-managed`

The intended architecture is Admin → Backend → Managed. The Admin app does not directly connect to the Managed app.

## Cross-repository policy

Only `gaje9dra/parento-admin` is modified by this phase. Any future dependency on the backend or Managed app must be documented and implemented in its respective repository during its own phase.
