# Phase 10.6 — Admin Android Audio Security, Reliability & Phase 10 Completion

## Scope

This phase modifies only `gaje9dra/parento-admin`. Backend and Managed Android contracts are treated as external dependencies and are not modified here.

The Admin application continues to use `Admin App -> authenticated backend API -> managed device`. No direct Admin-to-Managed connection is introduced.

## Security hardening

- Audio start remains an explicit Admin action from an authorized managed-device detail screen.
- The Admin API client continues to use the existing authenticated session and bearer-token refresh path.
- Device/session identity returned by the backend is checked against the currently selected device and requested session.
- Audio session IDs are validated before session lookup/stop calls.
- Audio URL path segments are encoded rather than concatenated without escaping.
- HTTP `401`, `403`, `410`, and `429` are mapped distinctly enough for safe UI behavior.
- Audio transport metadata is reduced to an allowlisted lifecycle state. Arbitrary backend transport metadata is not retained in Admin UI state.
- No authentication token, transport credential, media payload, or raw audio is stored in the audio domain.
- `AudioTransport` receives only a session/device binding context and bounded transport state; it does not receive authentication tokens.

## Session lifecycle

The backend remains authoritative for `REQUESTED -> AUTHORIZED -> STARTING -> ACTIVE -> STOPPING -> STOPPED`, with terminal `EXPIRED`, `FAILED`, and `REJECTED`.

Admin behavior:

- duplicate Start actions cannot create another session while an existing session is held;
- terminal sessions cannot be resumed implicitly;
- terminal sessions may only be replaced by an explicit Start a new audio session action;
- local clock expiry is rendered as an expired state and never extends the session;
- backend authorization failure stops local playback and clears the ability to continue;
- a session returned for another device is rejected;
- Stop is idempotent from the UI and always releases local playback/transport first;
- backgrounding stops local playback and polling;
- foregrounding reconciles the retained session with the backend;
- logout clears the session and stops local playback.

Automatic retries are deliberately bounded. Playback is attempted once per active session/foreground reconciliation, with an explicit Retry playback action available after a transport failure.

## Transport boundary

The current Phase 10 backend/Managed contract does not define an approved production media-byte protocol or codec.

Therefore Admin retains:

- a session-bound `AudioTransport` abstraction;
- a separate `AudioPlaybackController`;
- bounded lifecycle state;
- a fail-closed `UnavailableAudioTransport`.

No WebRTC, WebSocket, RTP, codec, media server, recording pipeline, or speculative raw-audio protocol is introduced.

When a production transport is formally defined, it must remain behind this boundary and be authorized by the backend/session contract.

## UI and accessibility

The audio surface explicitly communicates requesting, authorized/waiting, starting, active/connecting, active/playing, stopping, stopped, expired, failed, rejected, transport/playback unavailable, and authorization/network errors.

The Stop, Retry, Refresh, Back, and Start actions have content descriptions and Android minimum touch targets. State text is exposed through content descriptions so important audio state is not conveyed only visually.

The Admin activity already enables Android secure-window behavior, so the audio surface does not introduce screenshots or recent-task exposure of session content.

## Lifecycle and resource policy

- No audio data is persisted.
- No audio history/export/download is implemented.
- No duplicate playback controller is created by recomposition.
- Polling is cancelled while backgrounded and after terminal state.
- Local playback is stopped before transport cleanup.
- A failed transport does not trigger an uncontrolled reconnect loop.
- Process death does not restore an in-memory ACTIVE state as authoritative; a new process must reconcile with the backend.

## Manual verification

1. Sign in as an authorized Admin.
2. Select an enrolled, non-revoked, connected ManagedDevice.
3. Confirm Audio Access is enabled only for an eligible device.
4. Start audio explicitly.
5. Verify the UI reflects backend session lifecycle rather than treating request creation as capture success.
6. Verify no Admin microphone permission is requested.
7. Verify the current fail-closed transport boundary does not fabricate playback.
8. Stop the session and verify local playback/transport cleanup.
9. Allow or simulate expiry and verify playback stops and restart is explicit.
10. Revoke/unauthorize the device and verify audio stops and the UI reports authorization loss.
11. Log out and verify local audio state is cleared.
12. Background and foreground the Admin app and verify session reconciliation.
13. Recreate/rotate the Activity and verify no duplicate session is created.
14. Attempt duplicate Start and duplicate Stop.
15. Verify unauthorized/replayed/mismatched session data is not accepted by the Admin UI.
16. Verify no audio bytes, credentials, or raw transport metadata are persisted or logged.

## Verification

The repository workflow runs clean, unit tests, lint, instrumented Android tests, debug build, verification build, and release build.

The workflow timeout is 30 minutes so the complete Android verification sequence can finish without weakening or skipping checks.

## Remaining external dependency

Phase 10 Admin control/security/lifecycle work can be audited and tested without inventing a media protocol. End-to-end live audio playback remains dependent on a finalized, authenticated audio-byte transport contract shared by backend, Managed, and Admin.

No Phase 11 functionality is implemented.
