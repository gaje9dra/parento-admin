# Phase 7.3 — Admin Android Device Information & Monitoring Dashboard

## Repository scope

This phase changes only `gaje9dra/parento-admin`.

## Data source

The Admin app now consumes the Phase 7.1 canonical `GET /api/v1/devices` endpoint for the device list and the existing `GET /api/v1/devices/:deviceId/status` endpoint for detail. Backend authorization remains authoritative.

The previous enrollment-reference plus one-request-per-device list path is retained only as legacy API code and is no longer used by the monitoring dashboard.

## Device list

- Uses backend pagination with a bounded page size.
- Supports explicit refresh and load-more.
- Shows compact device name, communication state, monitoring freshness, battery, network, last telemetry age, and Parento version.
- Does not issue N+1 status requests for every list item.

## Device detail

Sections:
- overview and identity
- enrollment and management
- connection/last seen
- Android and Parento version
- battery
- network
- storage
- memory
- telemetry synchronization
- existing Phase 6 command management.

Exact timestamps are formatted locally and relative ages are shown separately so stale values are not presented as live.

## Freshness

The UI consumes backend freshness classifications rather than calculating a competing local freshness model:
- FRESH
- STALE
- VERY_STALE
- NEVER_REPORTED
- DISCONNECTED
- REVOKED
- UNKNOWN

Unavailable values remain `Unavailable`; the UI does not convert null telemetry to zero or false.

## Refresh and realtime

Manual refresh and lifecycle-safe ViewModel loading remain in place. No second realtime connection was added. The Phase 6 Admin realtime architecture did not expose a monitoring event stream that can safely drive this dashboard, so monitoring remains API-authoritative until such an event contract exists.

## Caching

No new persistent telemetry cache was introduced. Existing local authentication/session storage remains unchanged. The screen only retains ViewModel state during the active Admin session, avoiding indefinite persistence of detailed telemetry.

## Authorization and session

All monitoring requests use the existing authenticated Admin session and backend authorization. HTTP 401 responses continue through the existing authentication/session restoration path. No client-side authorization decision is treated as a security boundary.

## Accessibility

Status text is exposed semantically, buttons have content descriptions, and status is not communicated by color alone. The existing secure-window behavior remains enabled.

## Deferred

Phase 7.3 does not implement location, camera, microphone, audio recording, screen capture/recording, application or website blocking, device lock/wipe, arbitrary remote execution, hidden surveillance, or security bypasses.