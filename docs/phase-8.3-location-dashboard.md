# Phase 8.3 — Admin Android Location Dashboard & Map Integration

## Scope

This phase adds the Admin-side location domain, freshness/status UI, device-context navigation hook, map boundary, validation, and test coverage. It does not collect location from managed Android devices and does not modify backend or managed repositories.

## Current repository dependency finding

The current Admin repository does not contain a Phase 8.1 location-read contract. The current parento-backend tree also does not expose a location-read endpoint or location schema in the inspected API surface. Therefore this implementation deliberately fails closed at the repository boundary rather than inventing an endpoint, response fields, ownership rules, or fake coordinates.

ContractPendingDeviceLocationRepository is the temporary adapter. It must be replaced by the authenticated implementation once the Phase 8.1 backend contract is available.

No backend repository was modified.

## Intended flow

Admin UI -> LocationViewModel -> DeviceLocationUseCase -> DeviceLocationRepository -> existing authenticated backend communication boundary -> Phase 8.1 backend location-read contract

The UI never constructs HTTP requests or authorization headers.

## Domain

DeviceLocation supports:

- latitude / longitude
- accuracy
- optional altitude
- optional bearing
- optional speed
- observed timestamp
- backend received timestamp
- availability
- backend-provided freshness

Coordinates are validated before map rendering. Invalid values are rejected; they are not clamped. Missing coordinates never become 0,0.

## States

The UI distinguishes:

- loading
- available/fresh
- stale
- very stale
- never reported
- unavailable
- unauthorized
- revoked
- error

Freshness is represented as a backend-provided classification. The Admin UI does not invent conflicting freshness thresholds.

## Device selection

The existing ManagedDevice content path now exposes a location action for each available device and passes the existing deviceId into the location destination. No parallel device model or ownership mechanism is introduced.

The current repository does not yet contain a live Phase 5–7 device-list backend implementation, so the action becomes active when managed-device content is supplied by that existing architecture.

## Map

Google Maps SDK for Android is used behind the existing Android Views UI architecture.

- SDK: com.google.android.gms:play-services-maps:20.0.0
- valid location -> one latest marker
- valid accuracy -> accuracy circle using the reported radius
- no valid coordinates -> no marker
- map is not recreated for unrelated UI state changes
- MapView lifecycle is forwarded by MainActivity

The Maps API key is supplied through the Gradle property MAPS_API_KEY and the Android manifest placeholder. No key is committed to source control.

Example local build configuration:

./gradlew assembleDebug -PMAPS_API_KEY=<restricted-key>

The production key should be restricted to the Android application/package and appropriate Maps SDK usage.

## Privacy and security

- FLAG_SECURE remains enabled for Admin screens.
- Coordinates are not written to Admin logs.
- No analytics payload contains location.
- No clipboard copy is implemented.
- No location history is persisted.
- Cached location is not presented as live because this phase does not add a local location cache.
- Authorization failures remain visible as unauthorized state.
- Revoked devices are not bypassed.

## Backend contract dependency

Before the retrieval adapter is activated, Phase 8.1 must document:

1. authenticated Admin endpoint and HTTP method
2. device identifier path/query semantics
3. response envelope
4. location fields and timestamp serialization
5. availability values
6. freshness values and whether freshness is server-authoritative
7. 401/403/404/revoked error codes
8. ownership/authorization behavior

The Admin implementation should then consume that exact contract through the existing authenticated communication layer. It must not introduce a second authentication system or direct Admin-to-Managed connection.

## Tests

Unit tests cover:

- valid coordinates
- invalid latitude
- invalid longitude
- zero coordinates remaining valid data rather than a missing-location substitute
- fresh -> available
- stale -> stale
- very stale -> very stale
- missing location -> never reported
- authorization failure -> unauthorized

## Manual verification

Once the backend contract and a test API environment are available:

1. authenticate Admin
2. select an enrolled managed device
3. open Location
4. verify loading state
5. verify available/fresh state
6. verify stale and very-stale states
7. verify never-reported/unavailable states
8. verify 401/403/revoked handling
9. verify marker placement
10. verify numerical accuracy and accuracy circle
11. verify observed vs received timestamps
12. verify refresh behavior
13. verify back navigation and process recreation
14. verify different screen sizes
15. verify no coordinates appear in logs

## Out of scope

- managed-device location collection
- managed-device permissions
- backend API/schema changes
- location history
- route tracking
- geofencing
- location analytics
- covert tracking
- device control
- screen/audio/camera features
- Phase 8.4 or Phase 9+ functionality
