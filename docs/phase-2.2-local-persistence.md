# Parento Admin — Phase 2.2 Local Persistence

## Repository boundary

This phase modifies only `gaje9dra/parento-admin`. Backend and managed-device repositories are unchanged.

## Architecture

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

The codebase does not currently have a use-case layer, so local-state operations stay behind the domain-facing repository contract.

## Persisted entity

Phase 2.2 continues the Phase 2.1 singleton `local_application_state` entity. It stores the existing Phase 2.1 state plus an app-scoped installation UUID, installation creation timestamp, local setup state, and last initialization timestamp.

No credentials, tokens, managed-device records, commands, telemetry, media, location history, or audit history are stored.

## Identity

The installation identifier is generated with `UUID.randomUUID()` and stored in the app-private Room database. This avoids restricted hardware identifiers and keeps the identifier scoped to this application installation.

## Initial state

A new installation is explicitly `UNCONFIGURED`. Phase 2.2 does not authenticate an administrator, create an account, create a session, or store secrets.

## Database

Room database: `parento-admin.db`.

- version 1: Phase 2.1
- version 2: Phase 2.2
- explicit migration: `ParentoAdminDatabase.MIGRATION_1_2`
- destructive migration is not configured

The migration preserves existing Phase 2.1 state and adds only the four new metadata columns.

## Backup

The existing manifest setting `android:allowBackup="false"` is preserved. Local persistence is therefore treated as installation-specific. The future authentication phase must revisit backup behavior before introducing credentials or session secrets.

## Secure storage

Room is not used for future credentials. Passwords, access tokens, refresh tokens, OAuth secrets, API secrets, and similar authentication material are outside this phase and must use an appropriate Android secure-storage mechanism when authentication is implemented.

## Threading and logging

Application initialization runs through an application-owned coroutine scope on `Dispatchers.IO`. The repository maps storage failures and invalid persisted setup-state values to `AdminError.LocalStorage`. Logging records only non-sensitive operation success/failure.

## Testing

Repository tests use an in-memory Room database and verify identity creation and stability, initialization and persistence, domain/entity mapping, reactive observation, clear/missing state behavior, and application-level storage error representation.

Migration tests verify the Phase 2.1 schema is upgraded to version 2 without losing existing state.

## Deferred work

Authentication, backend communication, enrollment, QR pairing, Device Owner provisioning, managed-device management, monitoring, location, camera, microphone, audio, screen sharing/capture, app/site blocking, policies, notifications, and remote commands are intentionally deferred.
