# Phase 11.6 — Admin Android Application Management Finalization

This phase is implemented only in `gaje9dra/parento-admin`.

## Finalization scope

- Application inventory remains device-scoped and backend-authorized.
- Inventory freshness is displayed separately from application data.
- Inventory and policy pagination are consumed through the existing cursor APIs.
- Application-management commands are displayed separately from enforcement state.
- Desired policy version, reported policy version, and backend enforcement status remain distinct.
- Optional per-application desired/reported actions and enforcement results are displayed only when the backend returns them.
- Unknown enforcement and command states fail closed instead of crashing or being represented as success.
- Policy edits continue to send an expected policy version; a backend conflict requires refresh/retry.
- Disabled policies cannot be assigned from the Admin UI.
- Assignment/removal actions are explicitly confirmed and described as desired-state changes.
- Duplicate action requests are suppressed while an operation is in flight.
- Session expiration/revocation, authorization failures, rate limiting, connectivity failures, timeouts, backend failures, and revoked devices have actionable UI handling.
- Last successfully loaded application state can remain visible in-memory during a temporary connectivity outage, but it is explicitly marked offline and actions are gated.
- Application state is cleared on Admin logout/session loss.
- Existing secure-window behavior remains enabled.
- No application inventory is written to logs or telemetry.

## State semantics

The Admin UI must not equate any of these:

1. Request sent.
2. Command delivered.
3. Policy received/reported by Managed Android.
4. Policy applied.
5. Application enforcement completed.

For example, a delivered synchronization command may coexist with `PENDING` enforcement. The UI does not label the application as successfully blocked unless the backend reports an applicable successful enforcement state.

## Realtime boundary

The inspected Admin repository does not contain an established realtime subscription mechanism for application-management events. This phase therefore does not introduce a second or speculative realtime system. The screen uses explicit refresh and existing backend command/status APIs; it does not continuously poll.

If a future backend/Admin realtime contract is added, it must remain authenticated, device-scoped, and policy/command-scoped.

## Offline/cache boundary

The existing local persistence layer stores Admin application setup state, not application inventories or policy history. Phase 11.6 therefore retains the last loaded application-management state only for the active ViewModel/session and labels it as offline when a refresh fails. It does not persist unnecessary application inventory history.

## Security boundary

- Backend authorization remains authoritative.
- UI-provided device IDs are never treated as authorization.
- Device revocation stops protected application-management operations.
- Session expiration returns through the existing authentication flow.
- No direct Admin → Managed communication is introduced.
- No shell commands, scripts, arbitrary code execution, package-manager commands, root operations, Accessibility abuse, hidden APIs, or security bypasses are introduced.

## Android enforcement limitation

The Admin app configures and observes backend-authoritative application-management state. Actual enforcement depends on the Managed Android device's supported management mode and the enforcement result reported by that device through the backend.

This Admin phase does not implement application enforcement itself and does not claim universal application blocking.

## Manual verification plan

The following should only be marked passed after actual execution:

1. Admin login.
2. Authorized ManagedDevice selection.
3. Application inventory load.
4. Inventory freshness display.
5. Inventory search/filter.
6. Inventory pagination.
7. Policy creation.
8. Policy edit with expected-version update.
9. Duplicate/invalid rule validation.
10. Disabled policy assignment prevention.
11. Policy assignment confirmation.
12. Policy removal confirmation.
13. Desired vs reported policy version display.
14. Application-level desired/reported/enforcement display when returned by the backend.
15. Inventory command status display.
16. Policy synchronization command/enforcement separation.
17. Pending/applied/failed/unsupported/revoked/stale state presentation where supported by the backend contract.
18. Duplicate rapid action prevention.
19. 401/session expiration handling.
20. 403 authorization handling.
21. 404/410 device loss or revocation handling.
22. 409 policy conflict handling.
23. 429 rate-limit handling.
24. Timeout/network failure with retained offline state.
25. Logout clears application-management state.
26. Reconnection and explicit refresh.
27. Accessibility labels and status semantics.
28. No application inventory in logs, crash text, screenshots, or unintended UI routes.

## Contract limitations

The current Admin-side contract exposes aggregate policy synchronization/enforcement status. Per-application enforcement results are displayed only when the backend includes the corresponding fields in an inventory/application response. The Admin app does not fabricate per-application results from aggregate state.

The current inspected Admin architecture also has no established application-management realtime event stream, so no realtime behavior is claimed or simulated by this phase.