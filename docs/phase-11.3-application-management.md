# Phase 11.3 — Admin Android Application Inventory & Policy Management

This phase is implemented only in `gaje9dra/parento-admin`.

## Implemented

- Authenticated application inventory retrieval with backend pagination.
- Inventory freshness states: FRESH, STALE, VERY_STALE, NEVER_REPORTED, DISCONNECTED, REVOKED.
- Device-scoped application details and local search by label/package name.
- Explicit inventory refresh request through the existing backend command boundary.
- Admin application-policy models with typed ALLOW/BLOCK actions.
- Policy creation and editing with package-name validation and duplicate-rule rejection in the UI.
- Expected-version policy updates to prevent stale writes from silently succeeding.
- Device policy assignment/removal and policy synchronization actions.
- Desired policy version vs reported device version vs enforcement status are shown separately.
- Session expiration and backend authorization errors continue through the existing Admin authentication/API layer.
- Existing device-detail architecture is reused; no direct Admin-to-Managed connection was introduced.
- Existing secure-window behavior remains enabled, preventing application inventory from appearing in screenshots/recent previews.
- No application inventory is written to logs or telemetry.

## Backend contract

The Admin implementation uses the documented Phase 11.1 API:

- GET /api/v1/admin/devices/{deviceId}/applications
- GET /api/v1/admin/devices/{deviceId}/applications/{packageName}
- POST /api/v1/admin/devices/{deviceId}/applications/inventory/request
- GET/POST /api/v1/admin/application-policies
- GET/PATCH /api/v1/admin/application-policies/{policyId}
- GET/POST/DELETE /api/v1/admin/devices/{deviceId}/application-policy
- GET /api/v1/admin/devices/{deviceId}/application-policy/status
- POST /api/v1/admin/devices/{deviceId}/application-policy/sync

The Admin app does not modify or depend on a second backend contract.

## Safety boundary

The Admin app configures desired application-management policy. Actual application enforcement is performed by the Managed Android app using supported Android management APIs.

This phase does not implement application blocking, package disabling, force-stop, uninstall enforcement, Accessibility blocking, root/hidden APIs, website/network blocking, device locking/wiping, or Phase 12+ functionality.

## Offline / revoked behavior

Cached inventory is not treated as authoritative. Freshness is rendered from the backend response, and revoked/disconnected devices cannot use the inventory-request action. Policy changes only update UI state after a successful backend response.

## Manual verification

1. Authenticate as Admin.
2. Open an authorized ManagedDevice and open Application Management.
3. Verify inventory, observed/received timestamps, and freshness.
4. Search by application label and package name.
5. Request fresh inventory and verify a command identifier is returned.
6. Create an application policy with ALLOW and BLOCK rules.
7. Attempt a malformed package or duplicate rule and verify local validation rejects it.
8. Edit a policy and verify the expected-version field is used.
9. Assign a policy to the selected device only after explicit confirmation.
10. Verify desired version, reported version, and enforcement status remain distinct.
11. Request policy synchronization and verify the backend response is reflected.
12. Revoke or disconnect a device and verify protected actions are disabled.
13. Expire the Admin session and verify the existing authentication flow handles the failure.
14. Verify no inventory or credentials appear in logs, notifications, or screenshots.
