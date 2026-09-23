# Cross-Repository Contract Notes — Admin App

Phase 1.5 documents the future relationship without implementing the protocol.

## Repositories

- `gaje9dra/parento-admin` — administrator/controller Android application.
- `gaje9dra/parento-backend` — backend/API/database/realtime infrastructure.
- `gaje9dra/parento-managed` — managed-device Android application.

## Future relationship

Parento Admin will eventually authenticate as an authorized administrator with Parento Backend. After authentication, Admin operations will consume backend-mediated managed-device state and authorized management contracts.

The Admin app must not directly connect to or control the Managed app outside the backend-mediated architecture.

## Expected future backend contracts

Later phases may require:

- administrator authentication/session contracts
- managed-device listing and details
- enrollment/pairing lifecycle
- policy management
- device status/events
- authorized management commands
- realtime updates

These are contract requirements only. No fake endpoints, network clients, credentials, or realtime channels are implemented in Phase 1.5.

## Managed-device relationship

The managed application will eventually expose device-side behavior through the backend contract. The Admin app will not implement a direct peer-to-peer management channel.

## Security principle

Future sensitive operations must be attributable to an authenticated and authorized administrator, use legitimate Android/Android Enterprise mechanisms where applicable, and must not bypass platform permissions or conceal management behavior.

## Repository boundary

Only `gaje9dra/parento-admin` is modified by Phase 1.5. Requirements for `parento-backend` and `parento-managed` remain documentation-only.


## Phase 2.1 local persistence boundary

Only `gaje9dra/parento-admin` is modified by Phase 2.1.

The local Room database is an Admin-device-local store. It is not the backend database and it does not establish a synchronization protocol.

Future backend work in `gaje9dra/parento-backend` will eventually define authenticated administrator sessions, managed-device data, policy contracts, events, and realtime communication. Those requirements are not implemented here.

Future managed-device work in `gaje9dra/parento-managed` will eventually define authorized enrollment, device identity, status, policy, and management contracts. Those requirements are not implemented here.

The Admin app must communicate with the Managed app through authorized backend-mediated contracts rather than a direct Admin-to-Managed connection.
