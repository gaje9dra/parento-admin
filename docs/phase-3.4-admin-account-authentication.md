# Phase 3.4 — Admin Android Account & Authentication Operations

## Scope

This phase modifies only `gaje9dra/parento-admin`.

No managed-device enrollment, pairing, remote commands, location, camera, microphone, screen sharing, blocking, wipe, surveillance, or anti-bypass functionality is implemented.

## Authenticated-admin data flow

The existing Phase 3 authentication session is the source of the authenticated administrator identity:

```text
Authenticated Session
        ↓
AuthenticationRepository
        ↓
GET /api/v1/auth/admin/me
        ↓
AuthenticatedAdmin
        ↓
AuthenticationViewModel.state
        ↓
Authenticated Admin Shell
```

`AuthenticationState.Authenticated` already carries the server-authoritative `AuthenticatedAdmin` profile. Phase 3.4 does not introduce a second independent authentication/profile state machine.

The Android model contains only:

- administrator ID
- email
- account status
- last-authenticated timestamp

Credentials remain inside the authentication session and are never exposed through the UI profile model.

## Admin profile UI

The authenticated Admin Home shell now displays safe account information:

- administrator email
- server-reported account status

No password, password hash, access token, refresh token, or backend security metadata is rendered.

The UI uses the existing application shell and does not introduce a separate profile-management system.

## Current-admin synchronization

The existing Phase 3.3 repository flow remains authoritative:

1. restore the encrypted session;
2. reject an expired server-side session;
3. refresh an expired access credential when the server session remains valid;
4. call `GET /api/v1/auth/admin/me`;
5. persist the server-returned administrator identity;
6. transition the global authentication state based on the result.

The Android client never treats locally cached administrator status as authoritative.

## Account status

`ACTIVE` administrators remain in the authenticated shell.

If the backend returns `DISABLED`, the repository clears the local session and the global authentication state transitions to `AccountDisabled`. Protected navigation is therefore left and the login flow is rendered.

Distinct revoked-session handling remains available when the backend supplies `SESSION_REVOKED`.

For a generic `401 AUTHENTICATION_REQUIRED`, the Android client does not guess whether the underlying server condition was expiry, revocation, or another invalidation reason.

## Password operations

No password-change UI is implemented.

The current backend Phase 3.4 contract does not expose a supported password-change endpoint. The Android client therefore does not invent one or simulate a successful password operation.

If a future backend contract adds password change, the client must follow that documented contract and handle any resulting session invalidation explicitly.

Login password handling remains ephemeral:

- password fields are masked;
- passwords are held only in memory for the login operation;
- passwords are not persisted;
- passwords are not logged;
- passwords are not rendered after submission.

## Session impact

Existing Phase 3.3 session lifecycle behavior remains in force.

Security-sensitive authentication failures can transition the application to:

- `SessionExpired`
- `SessionRevoked`
- `AccountDisabled`
- generic authentication error

Local secure credentials are cleared for confirmed invalid/revoked/disabled authentication states.

Logout remains local-first and does not depend on backend network success.

## Error handling

Account/current-admin operations continue to use centralized `AuthenticationApiClient` error mapping.

The Android client maps backend responses to safe domain errors rather than displaying raw response bodies or server internals.

Supported mappings include:

- `401 INVALID_CREDENTIALS`
- `401 SESSION_REVOKED`
- `401 ACCOUNT_DISABLED`
- `401 AUTHENTICATION_REQUIRED`
- `403 ACCOUNT_DISABLED`
- generic `403`
- `400`
- `429`
- `5xx`
- network failures
- timeouts
- malformed responses

The existing authentication state machine remains the single protected-navigation authority.

## Local data

The administrator profile is not separately persisted as an authoritative account record.

The secure authentication session remains the persisted source needed to restore authentication. The current-admin API response updates the administrator identity stored with that session.

Passwords and credentials are never stored in ordinary preferences, Room, files, or UI state.

## Lifecycle

Activity recreation and process restoration continue to use the Phase 3.3 lifecycle architecture:

- Activity-scoped `AuthenticationViewModel`
- secure persisted session
- startup restoration
- current-admin validation when returning to the started lifecycle state
- duplicate validation suppression

Compose-specific profile state was not introduced because this application currently uses the existing Android View/ViewModel shell.

## UI security

The authenticated shell continues to use `FLAG_SECURE`.

Authentication screens continue to mask password input and avoid rendering credentials.

The authenticated shell is rendered only for `AuthenticationState.Authenticated`, preventing a protected-content flash while session restoration is in progress.

## Testing

Existing Phase 3.3 tests cover the core current-admin/session behavior, including:

- successful authenticated identity restoration
- invalid credentials
- disabled identity rejection
- expired server session
- expired access-token refresh
- revoked session cleanup
- disabled account cleanup
- authorization rejection
- network failure during logout
- duplicate login suppression
- authentication state transitions

Phase 3.4 adds the authenticated-admin profile integration to the existing shell without creating a second authentication state source.

Additional on-device verification should cover:

- Activity recreation
- process recreation
- background/foreground transitions
- disabled account response
- safe profile rendering
- absence of credentials from visible UI

## Backend contract dependency

The Android implementation targets the existing backend contract:

- `POST /api/v1/auth/admin/login`
- `POST /api/v1/auth/admin/refresh`
- `GET /api/v1/auth/admin/me`
- `POST /api/v1/auth/admin/logout`

The current backend does **not** expose a password-change endpoint. Password-change UI is therefore intentionally deferred.

The backend current-admin response supplies the administrator identity/status consumed by the Android app. No backend repository was modified by Phase 3.4.

## Known limitations

- No password-change operation is available until the backend exposes a documented contract.
- No multi-admin account-management or RBAC UI is introduced because that authorization model is not part of the current contract.
- Administrator profile editing is not implemented.
- Current-admin information is shown in the existing authenticated shell rather than a dedicated settings/profile system.

## Verification requirement

Before considering this phase complete, run:

```bash
git status
git diff
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

and the configured instrumentation/static-analysis tasks where the environment permits.

No errors should be suppressed or bypassed merely to make verification pass.
