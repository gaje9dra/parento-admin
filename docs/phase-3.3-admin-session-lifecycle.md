# Phase 3.3 — Admin Android Session Lifecycle

## Scope

This phase hardens administrator authentication in `gaje9dra/parento-admin` only.

No managed-device enrollment, pairing, monitoring, device control, location, camera, microphone, screen sharing, blocking, policies, or notifications are implemented here.

## Authentication state

`AuthenticationViewModel` is the single UI-facing authentication state source.

The state machine is:

```text
UNAUTHENTICATED
      |
      | login / restore
      v
AUTHENTICATING
   |       |
   |       +--------------------+
   |                          failure
   |                            |
   v                            v
AUTHENTICATED          AUTHENTICATION_ERROR
   |
   +-- access/session invalid --> SESSION_EXPIRED
   |
   +-- explicit revocation -----> SESSION_REVOKED
   |
   +-- disabled identity -------> ACCOUNT_DISABLED
   |
   +-- logout ------------------> UNAUTHENTICATED
```

The ViewModel serializes authentication operations and suppresses duplicate login, validation, and logout submissions.

Authentication state is not stored as a separate boolean. Protected UI is rendered only for the `Authenticated` state.

## Session storage

The session contains:

- authenticated administrator identity
- access credential
- refresh credential
- access-credential expiration
- server-side session expiration

The session is encrypted before being stored in private `SharedPreferences`. The encryption key is generated and retained by Android Keystore using AES-GCM.

Passwords are never persisted.

The app window also uses `FLAG_SECURE` so sensitive administrator UI is not exposed through ordinary screenshots or non-secure displays.

## Session restoration

On startup the ViewModel enters `AUTHENTICATING` while the persisted session is evaluated.

The repository:

1. loads the encrypted session;
2. rejects an expired server-session lifetime;
3. refreshes locally expired access credentials before making an authenticated request;
4. validates the current administrator through `GET /api/v1/auth/admin/me`;
5. persists the server-returned administrator identity;
6. clears invalid credentials when the session is no longer usable.

A persisted local `isLoggedIn` flag is not used as the authority for authentication.

## Access expiration and refresh

The Android client does not intentionally send a locally expired access credential.

When the access credential expires but the parent server-side session remains valid, the client calls:

`POST /api/v1/auth/admin/refresh`

and then verifies the refreshed session through the current-admin endpoint.

Refresh credentials are replaced with the rotated credential returned by the backend.

If refresh fails, the local session is cleared and the backend error is propagated to the authentication state.

## Unauthorized responses

Authentication API failures are mapped centrally in `AuthenticationApiClient`.

The current backend contract uses HTTP 401 for an invalid authenticated session. The Android client maps:

- `INVALID_CREDENTIALS` → invalid login credentials
- `SESSION_REVOKED` → revoked session, when supplied
- `ACCOUNT_DISABLED` → disabled account, when supplied
- `AUTHENTICATION_REQUIRED` → expired/invalid session
- other 401 responses → expired/invalid session

A 403 is mapped to authorization failure unless the backend explicitly supplies `ACCOUNT_DISABLED`.

Protected callers therefore do not need to interpret raw HTTP status codes themselves.

## Revocation and disabled accounts

The Android implementation supports distinct `SESSION_REVOKED` and `ACCOUNT_DISABLED` states when the backend provides machine-readable reasons.

The current backend contract does not expose a distinct error code for every possible server-side invalidation reason. In particular, the current protected-session failure contract may use `401 AUTHENTICATION_REQUIRED` for an expired, revoked, or disabled session.

The Android client does not guess which reason occurred. When the backend provides only the generic contract, the client treats the session as invalid and requires authentication again.

If distinct revoked-versus-disabled UI is required, the backend must expose a documented machine-readable reason. This phase does not modify that backend repository.

## Logout

Logout is best effort against the backend but authoritative locally.

The client:

1. sends the current session to the backend logout endpoint when available;
2. clears the encrypted local session regardless of network success;
3. clears the in-memory authentication state;
4. returns to the login flow.

Repeated logout is harmless.

## Navigation

Only `AuthenticationState.Authenticated` can render the authenticated admin shell.

Unauthenticated, authentication-error, expired, revoked, disabled, and authentication-in-progress states render the authentication flow.

Activity recreation does not grant access independently of the ViewModel/session restoration process.

## Lifecycle

The authentication ViewModel is Activity-scoped and uses `viewModelScope` for authentication operations.

This preserves in-memory state across ordinary Activity configuration changes while secure persisted credentials provide the source needed to reconstruct authentication after process death.

The Activity revalidates the current session when returning to the started state after the initial startup restoration.

Duplicate lifecycle callbacks cannot create overlapping validation requests.

## Error handling

User-facing authentication errors are deliberately generic.

The client never displays:

- access credentials
- refresh credentials
- passwords
- stack traces
- database errors
- raw backend exceptions
- internal API details

Network failures and timeouts remain recoverable authentication errors rather than being treated as successful authentication.

## Security notes

The repository already rejects cleartext traffic at the Android manifest level and production configuration requires HTTPS.

The session store uses Android Keystore-backed AES-GCM rather than custom cryptography.

No authentication bypass, hard-coded credential, TLS bypass, or debug authentication shortcut is introduced.

## Testing coverage

Phase 3.3 tests cover:

- successful login persistence
- invalid credentials
- validation before API access
- expired server session cleanup
- expired access-token refresh
- disabled identity rejection
- revoked-session cleanup
- disabled-account cleanup
- authorization rejection cleanup
- logout when the backend is unavailable
- refresh failure propagation
- duplicate login submission protection
- authentication state transitions

Android lifecycle and process-death behavior should also be exercised on-device/emulator as part of release verification.

## Backend contract

The Android client currently targets:

- `POST /api/v1/auth/admin/login`
- `POST /api/v1/auth/admin/refresh`
- `GET /api/v1/auth/admin/me`
- `POST /api/v1/auth/admin/logout`

The access credential is sent as:

`Authorization: Bearer <accessToken>`

The backend remains authoritative for administrator identity and account status.

No backend repository is modified by this phase.
