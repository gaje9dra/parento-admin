# Phase 12.6 — Admin Network Policy Finalization

## Scope

This phase finalizes the Admin-side Phase 12 website/network-policy contract. The Admin app manages policy configuration and displays backend-authoritative device state; it does not perform Android network enforcement.

## Contract alignment

The Admin client consumes the Phase 12 backend endpoints for:

- policy list/details/create/update
- effective device policy
- device enforcement status
- device capability
- assignment/removal
- synchronization requests
- status requests

The backend's assignment/synchronization responses use a nested `synchronization: { command, state }` envelope. The Admin parser now accepts both that envelope and the complete device-state projection.

The device-state repository path combines the authoritative effective-policy, enforcement-status, and capability projections so the policy detail view can distinguish:

- desired policy/version
- reported policy/version
- enforcement status
- capability
- command status

The Admin does not infer enforcement from command acknowledgement.

## Version conflicts

Policy updates send `expectedVersion`. HTTP 409 is mapped to the existing domain conflict state. When an update conflicts, the Admin reloads authoritative policy state and tells the operator to review it before retrying rather than silently overwriting the newer server version.

## Rule semantics

Client validation follows backend semantics:

- ALLOW and BLOCK only
- exact domains and explicit leading `*.` wildcards
- normalized lowercase domains
- duplicate normalized domains rejected
- maximum 500 rules
- no URL/path/port syntax

Backend validation remains authoritative.

## Capability and enforcement states

The Admin only uses enforcement values defined by the backend:

`UNKNOWN`, `PENDING`, `APPLIED`, `PARTIALLY_APPLIED`, `FAILED`, `UNSUPPORTED`, `STALE`, `REVOKED`.

Capability values are:

`UNKNOWN`, `UNSUPPORTED`, `SUPPORTED`.

The Admin does not claim universal website blocking and does not implement enforcement locally.

## Authentication and authorization

All policy operations reuse the existing authenticated API client and encrypted Android Keystore-backed session storage. HTTP 401 uses the existing session restoration/expiration flow; HTTP 403 is treated as authorization failure. Backend ownership and authorization remain authoritative.

No direct Admin-to-Managed communication is introduced.

## Offline/stale behavior

Loaded policy lists can remain visible when refresh fails, but are explicitly marked stale. Protected mutations are not queued offline. Device enforcement state is treated as backend-authoritative and is not synthesized from cached command state.

## Privacy

The Admin stores/displays policy and enforcement metadata only. It does not collect or expose:

- browsing history
- visited URLs
- DNS history
- search history
- page contents
- credentials
- cookies
- decrypted TLS traffic
- packet captures

## Realtime

No speculative second realtime transport was introduced. Phase 12 backend does not expose an Admin-authenticated network-policy event stream, so the Admin continues to use lifecycle-aware status refresh and explicit synchronization/status requests.

## Manual verification plan

1. Authenticate as an authorized Admin.
2. Create a policy.
3. Add ALLOW and BLOCK rules.
4. Verify malformed and duplicate domains are rejected.
5. Verify more than 500 rules are rejected client-side.
6. Edit the policy with the current version.
7. Simulate a stale version and verify 409 conflict reloads authoritative state.
8. Assign the policy to an authorized active device.
9. Verify synchronization command and synchronization state are both visible.
10. Verify desired and reported versions remain separate.
11. Verify enforcement status is separate from command status.
12. Verify capability is shown as UNKNOWN/UNSUPPORTED/SUPPORTED according to the backend.
13. Request status and verify the command does not become enforcement success automatically.
14. Remove the assignment and verify the resulting synchronization state is preserved.
15. Disable connectivity and verify cached UI is marked stale where applicable.
16. Restore connectivity and refresh authoritative state.
17. Revoke/inactivate a device and verify mutations are rejected by the backend.
18. Expire the Admin session and verify the existing authentication flow handles 401.
19. Verify no browsing or traffic-surveillance data appears.

## Verification

Repository CI is the authoritative build/test verification path. The workflow runs unit tests, lint, instrumented Android tests, debug build, verification build, and release build.

Real Device Owner/Profile Owner testing requires a provisioned Android Enterprise device and is therefore a manual verification dependency rather than something claimed from source inspection alone.

## Repository boundary

Only `gaje9dra/parento-admin` is modified by Phase 12.6. Backend and Managed repositories remain untouched.
