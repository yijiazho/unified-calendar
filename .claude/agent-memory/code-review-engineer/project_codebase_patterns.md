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
- **Session cookie `secure` flag**: `server.servlet.session.cookie.secure=true` is absent from `application.properties`. This must be set before production deployment (or in a prod-profile). Missing it allows session cookies to transmit over HTTP.
- **Provider and status fields as `String`**: `CalendarAccount.provider` and `Booking.status` use `String` instead of enums. This is an intentional scaffold decision (enums added in implementation tasks) but is a recurring type-safety concern to flag in downstream tasks.
- **`WorkingHours` uses `String` for `startTime`/`endTime`**: These should map to `LocalTime` for type safety, or at minimum be documented as `HH:MM` format. Flag in implementation tasks.

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
