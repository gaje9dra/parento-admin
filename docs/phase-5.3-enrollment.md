# Phase 5.3 — Admin Android Secure Enrollment & Pairing

## Implemented scope

The Admin app consumes the Phase 5.1 backend contract:
- POST /api/v1/devices/enrollments
- GET /api/v1/devices/enrollments
- GET /api/v1/devices/enrollments/{enrollmentId}
- POST /api/v1/devices/enrollments/{enrollmentId}/cancel

Creation uses the existing authenticated admin session. The backend derives administrator ownership from that session; the Admin UI never supplies an admin ID.

## Flow

Authenticated administrator -> Create Enrollment -> one-time authorization secret -> managed device consumes secret -> Admin status refresh/polling -> Completed with managedDeviceId.

The one-time authorization secret is held only in ViewModel/UI memory. It is not written to Room, logs, analytics, URLs, or crash diagnostics and is never automatically shared.

No QR protocol is implemented because Phase 5.1 does not define one.

## Status and lifecycle

The client represents CREATED, PENDING, VERIFIED, COMPLETED, EXPIRED, CANCELLED, REVOKED, and FAILED. The backend remains authoritative. Terminal states stop polling.

While the enrollment destination is visible, status polling starts only when an active enrollment exists. It begins at 5 seconds and backs off to a maximum of 30 seconds after transient network/server/rate-limit failures. Polling stops when leaving the destination, when the ViewModel is cleared, or after a terminal state.

Manual refresh is also available. Enrollment UI is rendered from the ViewModel state rather than performing network operations inside the screen.

## Process death and navigation

Only the active enrollment ID is retained in SavedStateHandle. The authorization secret is intentionally not restored. After process recreation, the app enters a restoring state and queries the backend before presenting success or pairing material.

## Security

- Existing Keystore-backed admin session is reused.
- Enrollment API requests use the stored access credential and do not create a second token.
- HTTP 401 clears the local session through the existing authentication boundary.
- Pairing material is never automatically copied or shared.
- Explicit copy clears the enrollment clipboard after 60 seconds when it still contains the enrollment clip.
- MainActivity uses FLAG_SECURE.
- No QR, provisioning bypass, root, covert enrollment, or device-control behavior is implemented.

## Backend contract

The Admin client follows the Phase 5.1 response envelope and enrollment-session fields, including:
- id
- status
- createdAt
- updatedAt
- expiresAt
- verifiedAt
- completedAt
- cancelledAt
- managedDeviceId
- verificationAttempts

Enrollment-specific backend errors are mapped to Admin domain errors without exposing raw server errors to the UI.

## Testing

Unit coverage includes:
- creation and one-time secret presentation
- server-authoritative completion
- process-death restoration using only enrollment ID
- authentication/session-expiry boundary
- cancellation
- non-success enrollment-state handling

Android UI/instrumentation coverage remains in the repository's existing Android test suite where applicable. No future realtime/device-control features are introduced.

## Cross-repository dependency

No other repository is modified. The implementation depends on the existing Phase 5.1 backend contract and the Managed Android Phase 5.2 consumer.