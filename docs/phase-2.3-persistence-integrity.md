# Phase 2.3 — Admin Persistence Integrity & State Repository Contracts

Repository boundary: only gaje9dra/parento-admin is modified.

## Architecture

UI / ViewModel
→ Domain
→ LocalStateRepository
→ LocalApplicationStateDao
→ Room

The existing Android Views architecture is preserved. Room entities and DAOs remain inside the data layer.

## Persistent state

The singleton local application state stores:
- state version
- initialization metadata
- installation UUID and creation timestamp
- local setup state
- synchronization metadata

Local setup states are only:
- UNCONFIGURED
- READY

Authentication states such as LOGGED_IN, SESSION_ACTIVE, and TOKEN_VALID do not exist.

Runtime-only UI state such as loading, navigation, dialogs, snackbars, and screen transitions is not persisted.

## Lifecycle

UNCONFIGURED → READY

The repository rejects repeated or impossible local setup transitions. Authentication transitions are intentionally absent.

## Installation identity

A UUID is generated locally, once, and stored in the private Room database. It survives normal restarts and is independent of hardware identifiers.

No IMEI, serial number, MAC address, advertising ID, or fake backend account identifier is used.

The singleton primary key and unique installation-ID index prevent duplicate persisted installation identities.

Repository write/read-modify-write operations are serialized with a coroutine Mutex.

## Database

Room database: parento-admin.db

Versions:
- v1 — Phase 2.1
- v2 — Phase 2.2
- v3 — Phase 2.3

Migration 1→2 preserves Phase 2.1 values and adds Phase 2.2 fields.

Migration 2→3 adds the unique installation-ID index.

No destructive migration is configured.

## Error handling

Storage, database, mapping, and migration failures are represented at the application boundary as AdminError.LocalStorage.

Invalid local lifecycle transitions are represented as AdminError.InvalidState.

Raw Room/SQLite exceptions and stack traces are not exposed to UI.

## Backup and security

The existing security baseline remains:
- android:allowBackup="false"
- android:usesCleartextTraffic="false"
- no sensitive runtime permissions
- no credentials or tokens persisted
- no sensitive identifiers logged

Persisted local state cannot establish authentication, backend sessions, administrator trust, or device enrollment by itself.

## Testing

Coverage includes:
- default local state
- setup lifecycle
- absence of authentication states
- stable UUID identity
- concurrent identity initialization
- repeated initialization
- persistence round trips
- Flow observation
- invalid persisted state
- invalid state transition
- migration 1→2
- migration 2→3
- isolated in-memory Room persistence

## Deferred

Authentication, backend communication, enrollment, device management, monitoring, location, camera, microphone, audio, screen sharing/capture, app blocking, website blocking, DNS/VPN filtering, policies, and remote commands remain deferred.
