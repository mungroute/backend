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
- Fix: `WalkSessionCleanup` attempts both ports, preserves both failures, and keeps
  the end operation retryable.
- Regression tests: `WalkSessionCleanupTest`.

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

## Verified controls

- Refresh-cookie requests from an unapproved Origin are rejected by the CORS
  security filter (`SecurityAccessTest`).
- Rotated refresh tokens and password-reset/logout revocation cannot be reused.
- Production health details are hidden and Swagger/API docs are disabled by default.
- Course/walk deletion checks group sharing through consumer-owned ports.
- Course matching no longer depends on walk-domain status types.
- Static `innerHTML` uses in the frontend contain only code-owned markup; no user
  input reaches those sinks.

## Follow-up backlog

These items were not classified as critical/high based on the current evidence and
remain explicit follow-ups:

- Add an outbox/retry worker for cleanup failures so recovery does not depend on an
  API retry (Medium).
- Move remaining walk-to-user and meet/proximity-to-walk persistence references to
  application ports, then broaden `ArchitectureBoundaryTest` (Medium).
- Split the remaining `CourseCatalogService` query/comparison metric assembly after
  the current comparison changes are checkpointed; representative and deletion
  use cases are already separate (Medium).
- Extract the remaining segment-swap candidate generation loop; connectivity
  validation and constraint/scoring evaluation now have dedicated collaborators
  (Medium).
- Add deployed-environment WebSocket reconnect/worker-count and parallel database
  race smoke tests; unit/architecture tests cannot fully reproduce them (Medium).

## CI gates

- Frontend: locked install, zero-warning lint, tests, production build, high-risk
  npm audit, dependency review, secret scan, and CodeQL.
- Backend: unit/integration tests (DB integration explicitly enabled), ArchUnit,
  executable build, dependency review, secret scan, and CodeQL.
