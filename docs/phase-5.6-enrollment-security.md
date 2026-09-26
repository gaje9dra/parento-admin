# Phase 5.6 — Admin Android Enrollment Security Hardening

## Scope

This document records the final Admin-side security review for Phase 5. The implementation is limited to `parento-admin`. Backend and Managed repositories are not modified by this phase.

Phase 5.6 covers the authenticated enrollment-management boundary, pairing-material handling, state integrity, lifecycle recovery, retry behavior, API contract handling, and verification.

## Architecture

The Admin app keeps four identities separate:

1. Local Admin-App installation identity.
2. Authenticated administrator identity from the Phase 3 session.
3. Enrollment session identity from the backend.
4. ManagedDevice identity returned by the enrollment protocol.

The Admin UI does not accept an administrator ID as an ownership authority. The backend remains authoritative for enrollment ownership.

Enrollment operations use the existing `SessionStore` to obtain the authenticated access token. The enrollment repository does not create a second authentication system.

## Backend Contract Used

The Admin app consumes these authenticated Phase 5 endpoints:

| Operation | Method | Endpoint |
|---|---|---|
| Create enrollment | POST | `/api/v1/devices/enrollments` |
| List owned enrollments | GET | `/api/v1/devices/enrollments` |
| Get owned enrollment | GET | `/api/v1/devices/enrollments/:enrollmentId` |
| Cancel owned enrollment | POST | `/api/v1/devices/enrollments/:enrollmentId/cancel` |

The managed-device consume endpoint is intentionally not called by the Admin app:

`POST /api/v1/devices/enrollments/:enrollmentId/consume`

The backend contract returns enrollment status, timestamps, verification attempts, and an optional ManagedDevice ID. The create response additionally returns a one-time authorization secret.

The backend authorizes create/list/get/cancel operations against the authenticated administrator. The Admin client does not attempt to reproduce that authorization decision locally.

## Enrollment State Machine

The backend states are:

`CREATED -> PENDING -> VERIFIED -> COMPLETED`

with terminal outcomes including:

- `COMPLETED`
- `EXPIRED`
- `CANCELLED`
- `REVOKED`
- `FAILED`

The Admin client treats `CREATED`, `PENDING`, and `VERIFIED` as active states. Terminal states stop polling and clear the persisted active enrollment reference.

A completed enrollment is not treated as proof that the ManagedDevice is online. Device communication belongs to Phase 6.

## Pairing Material

The authorization secret is returned only from enrollment creation and is kept in the ViewModel's active UI state.

It is not:

- written to Room;
- written to ordinary preferences;
- written to SavedStateHandle;
- logged;
- placed in notifications;
- sent to analytics;
- placed in crash diagnostics;
- copied automatically.

Process-death recovery persists only the enrollment ID through `SavedStateHandle`. After restoration, the Admin app queries the backend for authoritative enrollment state. It does not attempt to reconstruct a lost authorization secret.

If a create request times out or fails with an ambiguous network/server result, the client reconciles through the authenticated enrollment-list endpoint before allowing another create. It does not blindly submit a second create request.

If reconciliation finds an active enrollment whose create response was lost, the UI treats that enrollment as an outcome-unknown session and does not fabricate or recover a secret that the backend never returned. The administrator can inspect/cancel it before creating another enrollment.

## Clipboard

Copying the authorization secret requires an explicit user action.

The clipboard is cleared after a bounded 60-second interval when the copied enrollment label is still present. API-level compatibility is handled without persisting the clipboard contents.

Authentication access or refresh tokens are never copied.

## Sharing and QR

The Admin app has no automatic sharing flow for enrollment credentials.

There is no Admin QR implementation in this Phase 5 code path. No custom QR or cryptographic protocol was introduced.

The managed-device consume protocol remains backend-defined and outside the Admin pairing UI.

## Screenshot and Recent-App Protection

The Activity applies `WindowManager.LayoutParams.FLAG_SECURE`. This protects the enrollment screen from appearing in screenshots and on non-secure displays.

The application also disables Android backup in the manifest. Pairing credentials are not placed in persistent local storage.

## Expiration

Backend expiration is authoritative.

The client accepts a terminal `EXPIRED` response and stops polling. It also maps HTTP 410 / `ENROLLMENT_EXPIRED` into the enrollment-expired domain error and does not repeatedly poll an expired session.

Local device time is used only for presentation. The client does not locally promote a pending enrollment to completed or otherwise override backend state.

## Cancellation

Cancellation uses the authenticated backend endpoint.

A successful cancellation becomes terminal and stops polling. Server rejection is surfaced as an error; the client does not claim cancellation succeeded locally.

## Network and Retry Behavior

The Admin API boundary uses:

- 10-second connection timeout;
- 15-second read timeout;
- disabled HTTP caching;
- bounded 64 KiB response-body reads;
- centralized bearer-token injection at the repository/API boundary;
- no response-body logging.

HTTP mappings include 400, 401, 403, 404, 409, 410, 429, and 5xx responses.

Transient network, timeout, and server-unavailable errors are retryable.

Authorization failures, expired/cancelled/revoked/invalid enrollment states, and rate-limit responses are not placed into an endless automatic retry loop.

Create requests are special because a timeout can leave the outcome unknown. The client first reconciles with the authenticated list endpoint instead of blindly creating another session.

## Lifecycle and Process Death

Polling is active only while an enrollment screen is relevant and the enrollment is in an active state.

Polling stops when:

- the Activity stops;
- navigation leaves enrollment;
- enrollment becomes terminal;
- an unrecoverable enrollment error occurs;
- the ViewModel is cleared.

Polling is guarded against duplicate jobs.

The ViewModel survives configuration changes through normal ViewModel ownership. The only enrollment value stored for process restoration is the active enrollment ID.

## Local Persistence

The Admin app does not create a second enrollment persistence mechanism.

Persistent session credentials remain in the existing encrypted `SecureSessionStore`. Enrollment pairing secrets remain transient.

The enrollment ID retained in `SavedStateHandle` is not an authorization credential.

## Logging

The existing Admin logger boundary explicitly prohibits credentials and sensitive payloads. Enrollment API code does not log access tokens, authorization secrets, request bodies, or response bodies.

Safe operational information remains limited to non-sensitive operation/state information in components that already use the logging boundary.

## Security Boundaries

The following Phase 6 capabilities remain out of scope:

- WebSockets;
- Socket.IO;
- FCM command delivery;
- device commands;
- live location;
- camera/microphone;
- screen sharing;
- application blocking;
- website filtering;
- remote lock/wipe/reboot;
- device policy control;
- advanced device dashboard.

## Cross-Repository Dependencies

No Admin-side change requires modifying the backend or Managed repositories for this phase.

The Admin client was verified against the existing Phase 5 backend contract rather than introducing new endpoints.

## Verification

Verification must use the repository's existing Gradle tasks. A result is reported as PASSED only when the corresponding task actually completes successfully.

The Phase 5.6 implementation should be verified for:

- formatting;
- lint;
- JVM/unit tests;
- instrumented persistence tests;
- debug build;
- release/verification build where configured.

