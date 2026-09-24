# Phase 3.5 — Admin Authentication Finalization & Phase 3 Completion

## Scope

This audit is limited to `gaje9dra/parento-admin`.

No changes are made to `parento-backend`, `parento-managed`, or external projects.

## Authentication architecture

The Admin authentication path remains:

```text
UI
 ↓
AuthenticationViewModel
 ↓
AuthenticationRepository
 ↓
AuthenticationApi
 ↓
AuthenticationApiClient
 ↓
Parento backend authentication API
```

The ViewModel owns the UI-facing authentication state. The repository owns session lifecycle decisions. The API client owns HTTP transport and backend error mapping. The secure session store owns persisted authentication credentials.

No second authentication state machine was introduced.

## Authentication state

The existing state model remains:

- `Unauthenticated`
- `Authenticating`
- `Authenticated`
- `SessionExpired`
- `SessionRevoked`
- `AccountDisabled`
- `AuthenticationError`

Protected content is rendered only for `Authenticated`.

## Session lifecycle

Session restoration:

1. reads the encrypted session;
2. rejects locally expired server sessions;
3. refreshes an expired access token when the server session remains valid;
4. validates the current administrator through `GET /api/v1/auth/admin/me`;
5. stores the server-authoritative administrator identity;
6. transitions the single authentication state accordingly.

Logout clears local credentials before waiting for the backend logout request. Backend logout failure therefore cannot leave the application locally authenticated.

Activity start validation is suppressed during the initial restoration path and serialized through the existing ViewModel mutex/in-flight guards.

## Credential storage

Authentication sessions are encrypted using an Android Keystore-backed AES-GCM key and stored in private SharedPreferences.

Passwords are never persisted.

Access and refresh tokens are not stored in plaintext files, Room, or ordinary unprotected preferences.

The application also disables backup and cleartext traffic in the Android manifest.

The project does not introduce custom cryptography.

## Authentication request security

The API client:

- sends bearer credentials only for authenticated API operations;
- does not log request bodies, authorization headers, or tokens;
- maps HTTP authentication failures to domain errors;
- handles timeouts and connection failures separately;
- parses server responses defensively;
- refreshes only through the existing refresh contract;
- does not retry authentication failures indefinitely.

The release configuration requires HTTPS. Phase 3.5 additionally enforces this invariant at the `AppConfig` boundary so an invalid production configuration cannot be constructed accidentally.

## 401 / 403 behavior

The existing mapping remains:

- `INVALID_CREDENTIALS` → invalid credentials
- `SESSION_REVOKED` → revoked session
- `ACCOUNT_DISABLED` → disabled account
- `AUTHENTICATION_REQUIRED` / generic 401 → session-expired path
- `ACCOUNT_DISABLED` with 403 → disabled account
- other 403 → authorization failure

The Android client does not infer a more specific server-side invalidation reason when the backend provides only a generic authentication failure.

Confirmed invalid/revoked/disabled sessions are cleared locally.

## Account identity separation

Local installation identity is stored independently from authenticated administrator identity.

The authenticated administrator comes from the backend session and `GET /api/v1/auth/admin/me`.

Logout removes the authenticated session while intentionally preserving the independent local installation identity.

## UI security

The authentication UI:

- masks password input by default;
- clears the password field after submission;
- prevents duplicate login submissions through the ViewModel;
- does not display tokens, passwords, hashes, or backend internals.

The authenticated shell continues to use `FLAG_SECURE`.

## Network security

`AndroidManifest.xml` keeps `android:usesCleartextTraffic="false"`.

Production configuration requires HTTPS and cannot enable debug diagnostics.

No certificate validation bypass, trust-all TLS implementation, hostname-verification bypass, or insecure TLS configuration was added.

## Testing

The existing suite covers authentication, session restoration, refresh, revocation, disabled accounts, authorization failures, logout, duplicate login suppression, secure storage, navigation, and local installation identity.

Phase 3.5 adds regression coverage for production configuration invariants:

- production cannot disable HTTPS;
- production cannot enable debug diagnostics.

The configured CI workflow also runs:

- unit tests;
- lint;
- instrumented persistence tests;
- debug build;
- verification build;
- release build.

## Phase boundary

Phase 3.5 does not implement:

- managed-device enrollment;
- QR/device pairing;
- device commands;
- location;
- camera;
- microphone/audio;
- screen sharing;
- application blocking;
- website blocking;
- remote locking;
- device restrictions;
- device monitoring;
- device policies;
- push command execution;
- parental-control functionality;
- Android Device Owner functionality.

Those remain later-phase work.

## Verification status

Verification status must be reported from the actual GitHub Actions results for the final commit. A failed or unavailable CI job is not treated as a passing result.

## Backend dependency

No backend modification is required by this audit. The Admin client continues to use the existing authentication contract:

- `POST /api/v1/auth/admin/login`
- `POST /api/v1/auth/admin/refresh`
- `GET /api/v1/auth/admin/me`
- `POST /api/v1/auth/admin/logout`
