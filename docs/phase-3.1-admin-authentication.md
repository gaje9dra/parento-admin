# Phase 3.1 — Admin Android Authentication Foundation

## Authentication flow

Application startup restores the encrypted session, validates it with the backend current-admin endpoint, and refreshes the opaque credentials when the access credential is invalid but the server-side session remains valid.

Login submits email/password through the authentication repository and backend API client. Successful authentication stores only the encrypted session and transitions the application to the authenticated shell.

Logout attempts backend session revocation and always clears the local secure session.

## Secure storage

Authentication sessions are stored outside Room in a private SharedPreferences file encrypted with an AES-GCM key held by Android Keystore. Passwords are never stored.

## Backend dependency

The Admin app consumes the Phase 3.1 backend endpoints documented in docs/cross-repository-contracts.md. The backend is an external dependency and is not modified by this repository.

## Error handling

Backend authentication failures map to domain-level errors without exposing raw server details. Network failures, timeouts, validation errors, invalid credentials, expired sessions, and server failures have separate safe mappings.

## Development configuration

The existing build configuration supplies environment-specific backend base URLs. Release builds require HTTPS and cleartext traffic remains disabled. The repository does not contain production credentials.

## Boundary

This phase does not implement enrollment, pairing, device synchronization, realtime communication, FCM, WebSockets, monitoring, location, camera, microphone, audio, screen sharing, app/site blocking, device restrictions, remote policies, or remote commands.
