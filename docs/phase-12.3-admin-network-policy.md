# Phase 12.3 — Admin Android Website & Network Policy Management

## Implemented

- Admin-side network policy/rule domain models using the backend's authoritative Phase 12.1 semantics.
- Exact domains and explicit leading `*.example.com` wildcard normalization.
- Policy list, create, edit, enable/disable and detail flows.
- Authorized managed-device list reused for assignment selection.
- Assignment/removal and separate synchronization/status requests.
- Desired policy version, reported version, enforcement status, command status, capability mode and capability version are displayed independently.
- Existing authenticated `AdminBackendApiClient` is reused; no second HTTP client was introduced.
- Existing navigation/ViewModel architecture is reused.
- HTTP 401/403/404/409/429/5xx handling remains inside the existing API error boundary.
- Loaded policy lists remain visible as explicitly stale during refresh failures; destructive mutation is never queued offline.
- No network enforcement is implemented in Admin.

## Contract alignment

Phase 12.1 provides:
- `POST /api/v1/admin/network-policies`
- `GET /api/v1/admin/network-policies`
- `GET /api/v1/admin/network-policies/:policyId`
- `PATCH /api/v1/admin/network-policies/:policyId`
- `GET /api/v1/admin/devices/:deviceId/network-policy`
- `POST /api/v1/admin/devices/:deviceId/network-policy`
- `DELETE /api/v1/admin/devices/:deviceId/network-policy`
- `GET /api/v1/admin/devices/:deviceId/network-policy/status`
- `GET /api/v1/admin/devices/:deviceId/network-policy/capability`
- `POST /api/v1/admin/devices/:deviceId/network-policy/sync`
- `POST /api/v1/admin/devices/:deviceId/network-policy/status/request`

The Admin implementation consumes those exact endpoints. Command delivery and enforcement are intentionally separate.

## Realtime/offline limitation

The inspected Admin repository does not currently contain a production realtime client or event-stream abstraction. No second realtime system was introduced speculatively. The UI uses the existing lifecycle refresh architecture and explicit status requests. A future finalized realtime event contract can be attached to the existing ViewModel state without changing policy domain semantics.

The current cache is in-memory ViewModel state; it is marked stale after refresh failure and is never represented as authoritative. Persistent policy caching would require extending the existing Room schema in a follow-up contract-approved change.

## Security/privacy

All operations pass through the existing authenticated API client. Backend authorization remains authoritative. The UI does not collect or display browsing history, visited URLs, DNS history, page contents, credentials, cookies, TLS traffic or packet captures. Full policy payloads are not logged.

## Manual verification

1. Sign in as an authorized Admin.
2. Open Policies.
3. Create a policy with ALLOW and BLOCK rules.
4. Verify invalid domains and duplicates are rejected.
5. Edit and disable/enable the policy.
6. Open a policy and load an authorized managed device.
7. Assign/remove the policy.
8. Request synchronization and status refresh.
9. Verify command status is separate from enforcement status.
10. Verify desired/reported versions are distinct.
11. Verify UNSUPPORTED/UNKNOWN capability is shown accurately.
12. Disable connectivity and confirm previously loaded policy data is labeled stale after refresh failure.
13. Verify 401/403/409/429/5xx paths through existing error handling.
14. Confirm no browsing/traffic surveillance data appears.

No backend or managed repository is modified by Phase 12.3.
