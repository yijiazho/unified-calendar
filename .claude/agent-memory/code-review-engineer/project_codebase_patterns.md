---
name: Codebase Patterns and Anti-patterns
description: Recurring patterns, known risks, and conventions observed across code reviews in this repo
type: project
---

## Established Conventions

- All domain models use Java `record` types (immutable value objects), not JPA entities.
- Repository interfaces use plain Spring JDBC (`JdbcTemplate`), not JPA. SQL belongs in the implementing class, not scattered elsewhere.
- All timestamps use `java.time.Instant` (UTC) in domain models. `CalendarEvent.startTimeUtc` / `endTimeUtc` naming convention makes UTC intent explicit.
- Package structure is feature-vertical: `auth/`, `calendar/`, `calendar/sync/`, `availability/`, `booking/`, `workinghours/`, `email/`, `config/`.
- Stub classes use `// Implemented in TASK-XXX` comment pattern; these are intentional scaffolding, not incomplete work.
- `DataSourceConfig` is a hand-rolled HikariCP datasource bean that overrides Spring Boot's autoconfiguration. It must always set `maxPoolSize=1` (SQLite write-lock constraint).

## Known Risks and Watch Points

- **HikariCP `connectionInitSql` with SQLite**: HikariCP executes `connectionInitSql` via `Statement.execute()`, which in the SQLite JDBC driver does NOT support multiple semicolon-separated statements. Only the first PRAGMA will execute. Always use separate `connectionInitSql` calls or a `SQLiteDataSource` subclass approach.
- **Flyway + SQLite**: Spring Boot 3.3+ pulls Flyway 10+. As of Flyway 11.7.2, SQLite support IS bundled in `flyway-core` (classes in `org.flywaydb.core.internal.database.sqlite`). No extra `flyway-database-sqlite` artifact is needed. Verify on each major Spring Boot version bump.
- **Encryption default fallback**: `encryption.secret-key=${ENCRYPTION_SECRET_KEY:dev-secret-key-change-in-production}` in `application.properties` provides a weak plaintext default. While acceptable for dev scaffold, the production deploy must override this via environment variable.
- **Session cookie `secure` flag**: `server.servlet.session.cookie.secure=true` IS set in `application.properties` (added in TASK-005 admin-auth-BE branch). `application-local.properties` correctly overrides it to `false` for local dev. `same-site=strict` and `http-only=true` are also configured. This risk is now resolved at the property level.
- **Provider and status fields as `String`**: `CalendarAccount.provider` and `Booking.status` use `String` instead of enums. This is an intentional scaffold decision (enums added in implementation tasks) but is a recurring type-safety concern to flag in downstream tasks.
- **`WorkingHours` uses `String` for `startTime`/`endTime`**: These should map to `LocalTime` for type safety, or at minimum be documented as `HH:MM` format. Flag in implementation tasks.

## Auth Layer Patterns (established in TASK-005, reviewed 2026-06-11)

- `AuthController` holds a direct `AdminRepository` reference to serve `GET /auth/me`. This is a mild SRP bend — the controller owns two distinct collaborators (service + repo). Acceptable for MVP but flag if the controller grows.
- `AuthService.signup()` validates slug format but does NOT check slug uniqueness before insert. Uniqueness is enforced only by the database `UNIQUE` constraint, which surfaces as an unhandled `DataIntegrityViolationException` (500) instead of a user-friendly 409/422.
- `EmailAlreadyUsedException` message includes the raw email value (`"Email already in use: " + email`). This is a minor info-disclosure item in high-sensitivity deployments.
- `JdbcAdminRepository.save()` re-queries by email after INSERT to return the fully populated entity. This is a safe but slightly redundant pattern (two round-trips); using `findById(keyHolder.getKey().longValue())` would be more direct and avoid the theoretical ambiguity of a just-inserted duplicate email.
- `keyHolder.getKey()` is called without a null check in `JdbcAdminRepository.save()` — `getKey()` returns `null` when SQLite JDBC does not populate the `GeneratedKeyHolder`. This will NPE in the rare case of a driver/config mismatch.
- `SessionUtils.requireAdminId()` uses an unsafe raw cast `(Long)` — if anything ever stores a non-Long value under `"adminId"`, it will throw `ClassCastException` and produce a 500 instead of 401.
- `@DirtiesContext(AFTER_EACH_TEST_METHOD)` is used for integration test isolation with in-memory SQLite. Correct approach; avoids shared state between tests at the cost of Spring context reload per test (slow at scale).
- Test `signupUser()` helper discards the `MvcResult` and does not assert status — silently swallows signup failures, which can cause misleading test failures downstream.

## CLAUDE.md Rules Frequently Worth Checking

- Availability logic must never call provider APIs — query `calendar_events` in SQLite only.
- Token encryption must go through `EncryptionConfig` — never ad-hoc `javax.crypto` calls inline.
- No Phase 2 features (booking form, email, ICS, cancel/reschedule) in Phase 1 scope.
- All timestamps stored as UTC; no `LocalDateTime` in persistence layer.

**Why:** These are architecture invariants that, if violated early, cause painful refactors and data integrity bugs in production.
**How to apply:** Flag immediately as Critical if any of these appear in a diff.

## Frontend Conventions (established in TASK-003 scaffold, reviewed 2026-06-10)

- Two Axios instances in `src/api/`: `client.ts` (baseURL `/api`, `withCredentials: true`) for admin endpoints, and `publicClient.ts` (baseURL `/api`, `withCredentials: false`) for visitor endpoints. Public API modules (`availability.ts`, `bookings.ts`) correctly import `publicClient` to avoid the 401-redirect interceptor.
- `client.ts` is missing the 401 interceptor that was specified in the task (`window.location.href = '/login'` on 401). Auth redirect is instead handled by `ProtectedRoute` via `AuthContext`. This is a valid architectural choice but deviates from the spec.
- `AuthContext` (`src/context/AuthContext.tsx`) provides global auth state via a single `getMe()` call on mount. `useAuth.ts` is a thin re-export of the hook from `AuthContext`. Page stubs and `ProtectedRoute` both consume `useAuth()` from this provider correctly.
- `Spinner` component exists (`src/components/Spinner.tsx`) and is used in `ProtectedRoute` to avoid blank-screen flash during auth resolution. `role="status"` + `aria-label="Loading"` present for accessibility.
- `Modal` component has an accessibility bug: outer wrapper uses `role="presentation"` but the click-to-close overlay should be `role="dialog"` or have no role; the inner div correctly has `role="dialog"` + `aria-modal="true"` but no accessible label (`aria-labelledby` or `aria-label` missing).
- Page stubs follow the pattern: one-line JSDoc + `return <div>Label</div>`. Intentional scaffold; not incomplete work.
- `dist/` directory is present as an untracked file and should be gitignored. The `.gitignore` already lists `dist` but the directory exists outside the repo, meaning it will be committed if someone does `git add .`.
- `App.css` (184 lines of Vite boilerplate template CSS) and `index.css` (Vite template styles) are present but unused by application code. Should be cleaned up before implementing real pages.
- `index.html` `<title>` is still the default Vite placeholder "frontend" — should be updated to the application name.
- `vite.config.ts` adds `changeOrigin: true` to the proxy config (not in the task spec but harmless/correct for most setups).
- `nginx.conf` IS present in the repo (correcting a stale prior memory entry).
