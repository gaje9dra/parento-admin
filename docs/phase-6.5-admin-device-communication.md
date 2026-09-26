# Phase 6.5 — Admin Android Device Communication, Status & Command Foundation

## Scope

This phase adds the Admin Android integration layer only. The Admin app communicates with Parento Backend through the authenticated administrator session. It never connects directly to the Managed Android application.

## Implemented

- Authenticated backend client using the existing secure session store and authentication repository.
- One protected-request retry after a 401 through the existing administrator session refresh/current-admin flow.
- Enrollment-derived managed-device list. Completed enrollment records that contain a managed-device ID are deduplicated and used as the current device references.
- Device status retrieval through `GET /api/v1/devices/{deviceId}/status`.
- Separate enrollment, operational/device, connection, and monitoring-freshness state.
- Operational monitoring display:
  - Android version/API level
  - Parento app version/version code
  - management mode
  - battery percentage
  - charging state
  - battery status
  - network state
  - storage totals/availability/usage
  - memory totals/availability/low-memory state
  - last monitoring update
  - last seen
- Explicit refresh and retry behavior. Device data is refreshed when the Devices destination becomes active; no background polling was introduced.
- Reconnecting state is preserved from the backend's STALE connection state.
- Generic command-management foundation using the backend's only currently allowlisted command type, `FUTURE_COMMAND`.
- Command creation uses backend idempotency keys.
- Command retrieval, lifecycle display, result/error display, and cancellation where the backend permits it.
- Session expiration is routed back through the existing Phase 3 authentication state instead of creating another auth flow.
- Existing secure session storage remains the only credential storage mechanism.

## Backend contract dependencies

The Phase 6.4 backend contract currently does not expose a canonical `GET /devices` managed-device listing endpoint. The Admin app therefore uses the existing authenticated enrollment-list endpoint as a bounded source of completed managed-device IDs. This is not treated as a new backend contract.

The Phase 6.4 backend also does not expose an Admin-authenticated realtime device-status stream. The backend realtime stream is device-session based. Consequently this phase does not introduce a second or direct realtime transport. Status changes are obtained through explicit/lifecycle-aware backend refreshes.

The backend command contract currently allowlists only `FUTURE_COMMAND` with an empty payload. No later-phase device-control command types are invented or enabled.

The backend currently exposes no command-history list endpoint. The Admin app therefore supports authoritative command creation and retrieval when a command ID is known, but does not manufacture a local unlimited command history.

## Security boundaries

- Admin requests use the existing authenticated administrator bearer session.
- Backend authorization remains authoritative.
- Tokens and session credentials are not logged or copied into UI.
- No arbitrary command text, shell execution, script execution, or code execution exists.
- No Admin-to-Managed direct communication exists.
- Location, camera, microphone/audio, screen sharing, app blocking, website filtering, remote lock/wipe, policy management, contacts, SMS, call logs, and browser history remain deferred.

## UI state

Device screens distinguish:

- Loading
- Success/content
- Empty
- Refreshing
- Offline/disconnected
- Reconnecting
- Stale monitoring
- Unknown/unavailable monitoring
- Error

Command status is rendered from the backend command lifecycle and is not considered successful merely because creation returned successfully.

## Verification

Verification must be performed against the Phase 6.5 branch with Gradle build, unit tests, lint/static analysis, and available Android instrumentation/UI tests. No verification result is claimed until the corresponding GitHub Actions or local command result is observed.
