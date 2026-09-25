# Phase 7.3 — Admin Android Device Information & Monitoring Dashboard

## Repository

Only `gaje9dra/parento-admin` is changed on this branch.

## Implemented Admin-side foundation

- Added a typed `ManagedDeviceMonitoring` model with optional Android, battery, network, storage, memory, last-seen, telemetry and sync observations.
- Added explicit freshness states: Fresh, Stale, Very stale, Never reported, Offline, Revoked and Unknown.
- Added explicit management and network-state enums.
- Added a repository boundary so monitoring networking remains outside UI code.
- Added centralized formatting for bytes, percentages, timestamps, relative update text and storage utilization.
- Added explicit monitoring UI states for loading, content, empty, stale, offline, unauthorized, session-expired, dependency-unavailable and unexpected error.
- Added Admin device monitoring list/detail rendering and navigation.
- Preserved `FLAG_SECURE` and the existing authentication/session boundary.
- Added unit coverage for unavailable values, safe storage percentages and stale timestamp wording.

## Current limitation

The current Admin repository is only at its Phase 3 authentication foundation. Its existing `BackendClient` is a future contract and there is no authenticated administrator device-list or device-detail monitoring API implemented in the backend contract.

The current backend OpenAPI contract exposes administrator authentication, enrollment, and Phase 6 command infrastructure, but it does not expose the read endpoints required by Phase 7.3 for:

- authorized managed-device listing
- authorized managed-device detail
- Android/app telemetry
- battery observations
- network observations
- storage observations
- memory observations
- authoritative monitoring freshness
- administrator-side realtime monitoring events

Because the Phase 7.3 specification requires the backend to remain authoritative, this Admin branch intentionally does **not** invent endpoint paths, fabricate device values, or use the Managed app as a data source.

The wired repository therefore returns an explicit dependency-unavailable state. This keeps the UI honest and leaves a clean seam for the authenticated API implementation once the backend contract exists.

## Security boundary

- Admin authentication remains owned by the existing Phase 3 session system.
- No client-side authorization decision was added.
- No direct Admin-to-Managed communication was added.
- No sensitive future capabilities were implemented.
- No location, camera, microphone, audio, screen capture, app blocking, website blocking, device lock/wipe, hidden surveillance, arbitrary remote execution or security bypass functionality was added.

## Cross-repository dependency

No files, commits, branches or configuration were changed in `gaje9dra/parento-backend` or `gaje9dra/parento-managed`.

The exact dependency is an authenticated, authorized Admin monitoring contract plus its authoritative freshness/realtime semantics in the backend. Per the Phase 7.3 repository rule, that dependency is documented only and must be implemented in its own repository phase.

## Verification

A repository-hosted Gradle runner is not exposed by the GitHub editing interface used for this change, so local Gradle execution could not be performed from this branch-editing session. The added unit test is committed, but its execution remains to be run by the repository's existing CI/Android environment.

## Status

**Phase 7.3 is not fully complete.**

The Admin-side monitoring model, UI/state boundary, formatting rules, navigation and safe dependency handling are implemented. Live monitoring remains blocked by the missing authenticated backend device-information/telemetry contract.
