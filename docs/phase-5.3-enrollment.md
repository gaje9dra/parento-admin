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

While the enrollment screen is visible, status refresh is limited to one poller at a 5-second interval. Polling is cancelled when the ViewModel is cleared or a terminal state is reached. Manual refresh is also available.

## Process death and navigation

Only the active enrollment ID is retained in SavedStateHandle. The authorization secret is intentionally not restored. After process recreation, the app must fetch server status before presenting success or pairing material.

## Security

- Existing Keystore-backed admin session is reused.
- Enrollment API requests use the stored access credential and do not create a second token.
- HTTP 401 clears the local session and is surfaced as session expiration.
- Pairing material is never automatically copied or shared.
- Explicit copy clears the enrollment clipboard after 60 seconds when it still contains the enrollment clip.
- MainActivity already uses FLAG_SECURE.
- No QR, provisioning bypass, root, covert enrollment, or device-control behavior is implemented.

## Cross-repository dependency

No other repository is modified. The implementation depends on the existing Phase 5.1 backend contract and Managed Android Phase 5.2 consumer.
