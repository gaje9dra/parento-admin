# Phase 9.3 — Admin Android Screen Viewer & Session Control

## Scope

This phase is implemented only in `gaje9dra/parento-admin`.

The Admin app now consumes the actual Phase 9.1 screen-session API:

- `POST /api/v1/devices/{deviceId}/screen-sessions`
- `GET /api/v1/screen-sessions/{sessionId}`
- `POST /api/v1/screen-sessions/{sessionId}/stop`

The existing Admin authentication/session and backend API boundary are reused.

## Session lifecycle

The client represents the backend lifecycle exactly:

`REQUESTED`, `AUTHORIZED`, `STARTING`, `ACTIVE`, `STOPPING`, `STOPPED`, `EXPIRED`, `FAILED`, `REJECTED`.

Terminal states are never presented as live. The client only treats an `ACTIVE` session as display-authorized.

## Device authorization

The start action is exposed only when the selected ManagedDevice is:

- enrolled;
- not revoked;
- connected through an active communication session;
- in an available/connected operational state.

The backend remains authoritative; the client-side checks are a UI safety gate and do not replace backend authorization.

## Screen transport boundary

Phase 9.1 explicitly coordinates lifecycle and command delivery but does not define a media-frame transport. Therefore this phase adds the `ScreenStreamClient` abstraction without inventing a WebSocket, media server, codec, or frame protocol.

No screen frames are fabricated.

The dedicated viewer surface deliberately shows a safe "no live screen content" state when no approved frame transport is available. It never presents the last known frame as live.

## Realtime

The current backend Phase 9.1 contract does not expose an Admin-authenticated screen-session event stream. The Admin app therefore does not create another realtime channel.

Session state is reconciled with the existing authenticated API through bounded polling while a session is non-terminal. Polling stops on terminal state, navigation away, or ViewModel destruction.

If a future backend contract exposes authorized screen-session events, the repository/ViewModel boundary can consume them without changing the viewer surface.

## Stop and automatic termination

The Stop action calls the backend stop endpoint and is idempotency-safe at the UI layer by disabling repeated operations while a request is in flight and ignoring terminal sessions.

The viewer detaches when navigating away and polling is cancelled.

On Admin logout/session loss, the authenticated UI is removed and the screen-sharing ViewModel is cleared. Backend Phase 9.1 remains authoritative for terminating active sessions on Admin logout.

## Privacy and security

The Admin activity preserves `FLAG_SECURE`.

No screen frames, screenshots, recordings, credentials, or transport secrets are persisted or logged.

The viewer does not accept arbitrary inbound media. The future `ScreenStreamClient` contract binds a transport to the authenticated Admin session, ManagedDevice ID, and screen-session ID.

## Lifecycle

- Rotation/activity recreation: ViewModel state is retained by the Activity-scoped ViewModel while the process remains alive.
- Process death: no screen session is restored from local persistence; the viewer cannot falsely return to ACTIVE.
- Background/foreground: API reconciliation on Activity start validates the Admin session; no media transport is left running because Phase 9.1 provides no media transport.
- Navigation away: screen viewer detaches and session polling stops.
- Logout/session loss: viewer is removed and local screen-sharing state is cleared.
- Session expiration/revocation: terminal backend state removes live-view semantics.

## Accessibility and responsive behavior

Start/stop/back controls have meaningful labels. State text is not color-only. The viewer surface preserves a neutral aspect-ratio-safe container and does not stretch remote frames because no frames are currently transported.

## Manual real-device test plan

1. Login as an authorized Admin.
2. Open an enrolled, connected ManagedDevice.
3. Start screen sharing.
4. Verify the session is created.
5. Verify the session progresses through backend state changes.
6. After Managed Android authorization, verify the Admin sees ACTIVE.
7. Verify the current viewer remains blank because no Phase 9.1 media transport exists.
8. Rotate the Admin device.
9. Interrupt and restore network connectivity.
10. Stop screen sharing.
11. Revoke the ManagedDevice and verify the session becomes terminal.
12. Logout Admin and verify the viewer is removed.
13. Kill/restart the Admin process and verify no stale ACTIVE viewer is restored.

No physical-device test is claimed as passed by this implementation.
