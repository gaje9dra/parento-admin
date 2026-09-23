# Cross-Repository Contract Notes — Admin App

Phase 1.5 documents the future relationship without implementing the protocol.

## Repositories

- `gaje9dra/parento-admin` — administrator/controller Android application.
- `gaje9dra/parento-backend` — backend/API/database/realtime infrastructure.
- `gaje9dra/parento-managed` — managed-device Android application.

## Future relationship

Parento Admin will eventually authenticate as an authorized administrator with Parento Backend. After authentication, Admin operations will consume backend-mediated managed-device state and authorized management contracts.

The Admin app must not directly connect to or control the Managed app outside the backend-mediated architecture.

## Expected future backend contracts

Later phases may require:

- administrator authentication/session contracts
- managed-device listing and details
- enrollment/pairing lifecycle
- policy management
- device status/events
- authorized management commands
- realtime updates

These are contract requirements only. No fake endpoints, network clients, credentials, or realtime channels are implemented in Phase 1.5.

## Managed-device relationship

The managed application will eventually expose device-side behavior through the backend contract. The Admin app will not implement a direct peer-to-peer management channel.

## Security principle

Future sensitive operations must be attributable to an authenticated and authorized administrator, use legitimate Android/Android Enterprise mechanisms where applicable, and must not bypass platform permissions or conceal management behavior.

## Repository boundary

Only `gaje9dra/parento-admin` is modified by Phase 1.5. Requirements for `parento-backend` and `parento-managed` remain documentation-only.


## Phase 2.1 local persistence boundary

Only `gaje9dra/parento-admin` is modified by Phase 2.1.

The local Room database is an Admin-device-local store. It is not the backend database and it does not establish a synchronization protocol.

Future backend work in `gaje9dra/parento-backend` will eventually define authenticated administrator sessions, managed-device data, policy contracts, events, and realtime communication. Those requirements are not implemented here.

Future managed-device work in `gaje9dra/parento-managed` will eventually define authorized enrollment, device identity, status, policy, and management contracts. Those requirements are not implemented here.

The Admin app must communicate with the Managed app through authorized backend-mediated contracts rather than a direct Admin-to-Managed connection.

## Phase 2 cross-repository compatibility notes

The Phase 2 foundations intentionally use different local/server state models and do not require the values to be identical.

### Identity boundaries

- Backend admins.id, managed_devices.id, and enrollments.id are server-owned UUID identifiers.
- Backend managed_devices.stable_identifier is a separate server-side stable device identifier.
- Managed App installationId is a locally generated UUID for the managed installation.
- Admin App installationId is a locally generated UUID for the Admin installation.
- Neither Android installation ID is an authenticated backend identity.

### State boundaries and future mapping

The Managed App lifecycle (UNENROLLED, ENROLLING, ENROLLED, CONNECTED, DISCONNECTED, REVOKED, ERROR) is a device-side lifecycle model. The backend currently persists separate enrollment and operational status concepts (PENDING, ACTIVE, REVOKED) and must define an explicit API mapping in a future integration phase.

The Admin App local setup state (UNCONFIGURED, READY) describes only the local Admin application's setup state. It is not equivalent to backend administrator status, managed-device enrollment status, or managed-device connection status.

Connection state in the Managed App is runtime-only and is not a server-authoritative status contract in Phase 2.

### Timestamp and serialization boundary

The backend uses PostgreSQL TIMESTAMPTZ for server timestamps. Android local persistence uses epoch-millisecond numeric timestamps. A future API contract must serialize server timestamps explicitly, for example as UTC/ISO-8601 values, and convert them at the Android boundary rather than treating local epoch-millisecond fields as wire-format contracts.

### Error boundary

The backend exposes structured HTTP errors with an error code, message, and request ID. Android currently exposes domain-level AdminError / ManagedError values and does not perform API translation yet. A future API client layer should map backend error codes to these domain errors without leaking raw server/database exceptions to UI.

These mappings are documentation-only Phase 3 dependencies; no API client, authentication, synchronization, enrollment, or realtime functionality is implemented here.

## Phase 3.1 — Admin authentication contract

The backend Phase 3.1 authentication foundation is now available to this Admin repository as an external API dependency.

Consumed backend endpoints:

- POST /api/v1/auth/admin/login
- POST /api/v1/auth/admin/refresh
- GET /api/v1/auth/admin/me
- POST /api/v1/auth/admin/logout

The backend returns the standardized { data, requestId } success envelope and { error: { code, message }, requestId } error envelope.

The login response provides an authenticated administrator plus opaque access/refresh credentials and ISO-8601 expiration timestamps. Refresh rotates both credentials. The current-admin endpoint validates the stored access credential. Logout revokes the backend session.

The Admin app keeps its local installation UUID separate from the authenticated administrator ID. Authentication credentials are never stored in Room.

Authentication mapping used by this repository:

| Backend contract | Admin domain |
|---|---|
| INVALID_CREDENTIALS / HTTP 401 | InvalidCredentials |
| HTTP 401 for expired/invalid session | SessionExpired |
| HTTP 400 | Validation |
| HTTP 5xx | ServerUnavailable |
| socket/IO failure | Network |
| timeout | Timeout |
| malformed/unexpected response | UnknownAuthentication |

The backend currently does not expose a distinct disabled-account error; disabled administrators therefore follow the backend's generic invalid-credentials contract rather than an invented client-side distinction.

No backend repository changes are made from this repository.


## Phase 3.2 — Admin authentication hardening

The Admin Android authentication boundary remains dependent on the existing backend Phase 3.1 authentication endpoints:

- POST /api/v1/auth/admin/login
- POST /api/v1/auth/admin/refresh
- GET /api/v1/auth/admin/me
- POST /api/v1/auth/admin/logout

The Admin client treats HTTP 401 from the current-admin endpoint as an invalid/expired access credential and performs at most one refresh attempt using the existing refresh endpoint. It does not invent a refresh protocol.

HTTP 401 with INVALID_CREDENTIALS during login maps to the InvalidCredentials domain error. HTTP 403 maps to authorization/account-status errors according to the backend error code. HTTP 429 from the backend authentication rate limiter maps to the Admin AuthenticationRateLimited domain error and is presented as a safe retry-later message.

The Android app does not assume that every unauthorized response can be distinguished as expired versus revoked because the current backend contract may use the generic AUTHENTICATION_REQUIRED error. When the backend later exposes more specific error codes, the mapping can be extended without changing the secure session boundary.

Authentication credentials remain outside Room. The Admin app stores only the encrypted session blob in private application storage, with its AES key held by Android Keystore. Passwords are transient and are never persisted.

Local logout is authoritative for the device UI: backend logout is attempted when possible, but failure to reach the backend does not prevent local credential removal or transition to the unauthenticated flow.

The backend remains authoritative for administrator authorization and account status. The Admin app does not treat its local authentication state as proof of server-side authorization.

No Admin-to-Managed direct communication, enrollment, device control, monitoring, or later-phase management functionality is introduced by Phase 3.2.
