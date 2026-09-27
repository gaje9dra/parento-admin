# Phase 10.3 — Admin Android Audio Access & Live Audio Foundation

## Scope

This phase is implemented only in `gaje9dra/parento-admin`.

The Admin app reuses the existing authentication, API client, managed-device selection, navigation, and lifecycle architecture. It adds an explicit audio-access control surface without creating a direct Admin-to-Managed connection.

## Backend contract

Phase 10.1 exposes:

- `POST /api/v1/devices/{deviceId}/audio-sessions`
- `GET /api/v1/audio-sessions/{sessionId}`
- `POST /api/v1/audio-sessions/{sessionId}/stop`

The Admin API client sends the existing authenticated bearer token through the existing `AdminBackendApiClient`. Backend authorization remains authoritative.

The Admin model represents:

`REQUESTED -> AUTHORIZED -> STARTING -> ACTIVE -> STOPPING -> STOPPED`

and terminal `EXPIRED`, `FAILED`, and `REJECTED` states.

## Authorization and lifecycle

Audio access can only be opened from an existing managed-device detail selection. The UI requires an enrolled, non-revoked, connected device before enabling the action, while the backend remains authoritative.

Starting requires an explicit Admin action. Opening a device does not create a session.

Stopping is explicit and local playback is stopped before the backend stop request. Session state is polled while the app is foregrounded and reconciled after returning to the foreground.

Admin logout/backgrounding stops local playback and clears the sensitive local session state. Terminal sessions cannot be resumed; a new authorized session is required.

## Transport

The Phase 10.1 backend contract defines audio session control and transport-state metadata but does not define a production media-byte protocol or codec. Therefore the Admin implementation provides:

- a session-bound `AudioTransport` abstraction;
- a separate `AudioPlaybackController` abstraction;
- a fail-closed `UnavailableAudioTransport`;
- bounded/no persisted audio state.

No speculative WebRTC, WebSocket, RTP, codec, media server, or raw audio protocol was introduced.

When a production transport contract is later approved, it must bind to the authenticated audio session and managed-device ID and remain behind these abstractions.

## Realtime

The existing Admin repository does not expose a separate realtime event client for screen sharing; Phase 9.3 uses authenticated session polling and foreground reconciliation. Audio follows that established Admin pattern rather than introducing a second realtime system.

## Security

- No Admin-device microphone permission is requested.
- No raw audio is stored or logged.
- No audio history/export/download is implemented.
- No audio credentials are copied to clipboard or persisted.
- No arbitrary device IDs are introduced into a separate registry.
- No direct Admin-to-Managed transport is created.
- Existing authentication refresh/error handling is reused.
- Revoked/disconnected devices are rejected before a new session request.
- Session expiration and terminal state prevent continued playback.
- Audio playback/transport is stopped on Admin backgrounding and logout.
- No future Phase 11+ features are included.

## Manual verification

1. Sign in as an authorized Admin.
2. Open an authorized connected ManagedDevice.
3. Confirm Audio Access is available.
4. Start audio access explicitly.
5. Confirm intermediate session states are displayed.
6. Confirm the managed device session becomes active when the Managed side is available.
7. Confirm the current Phase 10.1 transport boundary reports unavailable rather than fabricating playback.
8. Stop audio access and confirm local playback/transport cleanup.
9. Allow the session to expire and confirm playback remains stopped and a new session is required.
10. Revoke/disconnect the device and confirm new audio access is rejected.
11. Sign out and confirm local audio state is cleared.
12. Background/foreground the Admin app and confirm session reconciliation.
13. Attempt an unauthorized/revoked device and confirm the start action is unavailable.
14. Verify no raw audio, credentials, or transport secrets appear in logs or persistent storage.

## Verification

Run the repository's existing checks:

- `gradle clean`
- `gradle test`
- `gradle lint`
- `gradle connectedDebugAndroidTest`
- `gradle assembleDebug`
- `gradle assembleVerification`
- `gradle assembleRelease`

Phase 10.3 must not be declared complete until supported checks pass.
