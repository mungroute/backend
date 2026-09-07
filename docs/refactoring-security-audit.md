# Refactoring security and reliability audit

This log accompanies the frontend/backend refactor. REST paths, WebSocket topics,
payloads, and existing database data remain compatibility constraints.

## Fixed high-severity findings

### Production JWT secret could use a public development value

- Reproduction: start the `prod` profile without `JWT_SECRET`; the base
  configuration previously supplied `local-development-secret-change-me-1234567890`.
- Impact: anyone who knows the repository value can forge production access tokens.
- Severity: High.
- Fix: the production profile now requires `JWT_SECRET`; startup validation rejects
  missing, shorter-than-32-byte, and known development values.
- Regression tests: `JwtSecretPolicyTest`.

### Concurrent refresh requests could rotate one token more than once

- Reproduction: send two concurrent `/api/auth/refresh` requests with the same
  usable cookie before either transaction commits.
- Impact: one stolen or replayed refresh token can create multiple valid sessions.
- Severity: High.
- Fix: refresh rotation now acquires a pessimistic write lock on the token row.
- Regression tests: `AuthFlowIntegrationTest` verifies one-time use; the repository
  lock serializes concurrent rotations.

### Browser bearer token was persisted in session storage

- Reproduction: log in and read the prior auth/session storage keys from injected
  same-origin JavaScript.
- Impact: an XSS payload could exfiltrate the bearer token and keep using it after
  the page closes.
- Severity: High.
- Fix: access/auth state is memory-only; reload restoration uses the HttpOnly
  refresh cookie.
- Regression tests: frontend `auth.test.ts`.

### Logout did not invalidate an already issued access JWT

- Reproduction: capture an access token, call `/api/auth/logout`, then call an
  authenticated endpoint with the captured bearer token before its 30-minute expiry.
- Impact: a copied access token remained usable after logout.
- Severity: High.
- Fix: every access JWT now carries the hash of its refresh session. REST and
  WebSocket authentication accept it only while that server-side session remains
  usable, so logout, rotation, password reset, and account deactivation immediately
  invalidate older access tokens.
- Regression tests: `SecurityAccessTest` and `AccessTokenSessionValidatorTest`.

## Reliability fixes

### Active-walk back navigation raced with browser history

- Reproduction: dismiss the exit dialog, then quickly press Back again.
- Impact: the guard could be skipped or the dialog could flicker during a live walk.
- Severity: Medium.
- Fix: an explicit same-URL history guard entry makes Back deterministic.
- Regression tests: frontend `App.test.tsx`.

### Realtime cleanup stopped after the first failed dependency

- Reproduction: make Redis presence removal fail while ending a walk.
- Impact: Meet cleanup was never attempted, leaving more stale realtime state.
- Severity: Medium.
- Fix: finalization now inserts a cleanup task in the same transaction. The worker
  checkpoints Presence and Meet independently, uses a lease for multi-instance
  safety, and retries failures with bounded exponential backoff. A cleanup outage
  no longer changes the already committed end response.
- Regression tests: `WalkSessionCleanupTest`, `WalkFinalizationServiceTest`, and
  the outbox assertion in `WalkRecordFlowIntegrationTest`.

### Representative-course updates were not serialized across sources

- Reproduction: concurrently mark a saved walk and a custom course as the same
  user's representative course.
- Impact: the clear-then-set operations could interleave and leave inconsistent
  representative flags across the two tables.
- Severity: Medium.
- Fix: both use cases acquire the same user-row write lock through consumer-owned
  ports before touching course rows, using a consistent lock order.
- Regression gate: the port boundary is covered by `ArchitectureBoundaryTest`; DB
  integration tests exercise both representative update paths.

### Stale WebSocket close events could schedule an extra reconnect

- Reproduction: allow an old Presence, Meet, or group-course socket to deliver a
  delayed close event after its replacement connection is active.
- Impact: an extra socket and duplicate subscriptions could accumulate, causing
  duplicated realtime messages and unnecessary browser/server work.
- Severity: Medium.
- Fix: close events mutate state only when they belong to the current socket;
  reconnect timers are singular and are cancelled on explicit client shutdown.
- Regression tests: frontend `presenceSocket.test.ts`, `meetSocket.test.ts`, and
  `groupCourseSocket.test.ts`.

### Route integration tests depended on an imported developer dataset

- Reproduction: migrate a clean PostgreSQL database, set
  `RUN_DB_INTEGRATION_TESTS=true`, and run the route, catalog, group, and thermal
  integration tests.
- Impact: CI could fail before exercising those paths because no route rows were
  created by Flyway, while a populated developer database hid the dependency.
- Severity: Medium.
- Fix: affected tests now install and remove a deterministic test-only PostGIS and
  pgRouting network with hot base edges and cooler bounded-detour alternatives.
- Regression tests: `CourseRoutingRepositoryIntegrationTest`,
  `CourseCatalogFlowIntegrationTest`, `CourseDrawFlowIntegrationTest`,
  `GroupFlowIntegrationTest`, and `RouteThermalRepositoryIntegrationTest` pass
  against a schema-only database without using the data-pipeline.

## Verified controls

- Refresh-cookie requests from an unapproved Origin are rejected by the CORS
  security filter (`SecurityAccessTest`).
- Rotated refresh tokens and password-reset/logout revocation cannot be reused.
- Production health details are hidden and Swagger/API docs are disabled by default.
- Course/walk deletion checks group sharing through consumer-owned ports.
- Course matching no longer depends on walk-domain status types.
- Walk services reach user persistence through `WalkUserPort`; Meet and Proximity
  authorize sessions through the immutable `WalkSessionSnapshot` port.
- The cleanup outbox lease is claimed atomically; a real PostgreSQL race test
  verifies that two workers cannot claim the same session concurrently.
- Course catalog query/metric mapping and comparison are separate use cases behind
  the existing facade; segment-swap candidate generation has its own collaborator.
- Static `innerHTML` uses in the frontend contain only code-owned markup; no user
  input reaches those sinks.

## Follow-up backlog

These items were not classified as critical/high based on the current evidence and
remain explicit follow-ups:

- Add a deployed-environment WebSocket reconnect/worker-count smoke test; unit
  tests cannot fully reproduce multi-instance infrastructure behavior (Medium).

## CI gates

- Frontend: locked install, zero-warning lint, tests, production build, high-risk
  npm audit, dependency review, secret scan, and CodeQL.
- Backend: unit/integration tests (DB integration explicitly enabled), ArchUnit,
  executable build, dependency review, secret scan, and CodeQL.
