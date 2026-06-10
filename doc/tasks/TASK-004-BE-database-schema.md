# TASK-004 · BE · Database Schema

## Context

Defines all five SQLite tables via a Flyway migration script. Flyway runs automatically on startup, creating or migrating the schema. This task must be completed after TASK-002 (backend scaffold) and before any task that reads or writes the database.

All timestamps are stored as UTC ISO-8601 strings (SQLite has no native datetime type; storing as TEXT in ISO format keeps them sortable and readable).

---

## Instructions

### Migration file

Create `backend/src/main/resources/db/migration/V1__init_schema.sql`.

```sql
-- ──────────────────────────────────────────
-- admins
-- ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admins (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    email        TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    slug         TEXT NOT NULL UNIQUE,       -- URL-safe identifier for public page
    timezone     TEXT NOT NULL DEFAULT 'UTC',
    created_at   TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    updated_at   TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

-- ──────────────────────────────────────────
-- calendar_accounts
-- ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS calendar_accounts (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    admin_id             INTEGER NOT NULL REFERENCES admins(id) ON DELETE CASCADE,
    provider             TEXT NOT NULL CHECK (provider IN ('GOOGLE', 'OUTLOOK')),
    provider_account_id  TEXT NOT NULL,         -- Google sub / Microsoft oid
    email                TEXT NOT NULL,
    encrypted_access_token  TEXT NOT NULL,
    encrypted_refresh_token TEXT NOT NULL,
    is_primary           INTEGER NOT NULL DEFAULT 0 CHECK (is_primary IN (0, 1)),
    connected_at         TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    last_sync_at         TEXT,
    UNIQUE (admin_id, provider, provider_account_id)
);

-- ──────────────────────────────────────────
-- working_hours
-- ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS working_hours (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    admin_id     INTEGER NOT NULL REFERENCES admins(id) ON DELETE CASCADE,
    day_of_week  INTEGER NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),  -- 0=Monday, 6=Sunday
    start_time   TEXT NOT NULL,   -- HH:MM local time (interpreted in admin's timezone)
    end_time     TEXT NOT NULL,
    UNIQUE (admin_id, day_of_week)
);

-- ──────────────────────────────────────────
-- calendar_events
-- ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS calendar_events (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    admin_id             INTEGER NOT NULL REFERENCES admins(id) ON DELETE CASCADE,
    calendar_account_id  INTEGER NOT NULL REFERENCES calendar_accounts(id) ON DELETE CASCADE,
    provider             TEXT NOT NULL,
    provider_event_id    TEXT NOT NULL,         -- external ID from Google/Outlook
    title                TEXT,
    start_time_utc       TEXT NOT NULL,          -- ISO-8601 UTC
    end_time_utc         TEXT NOT NULL,
    is_booking_event     INTEGER NOT NULL DEFAULT 0 CHECK (is_booking_event IN (0, 1)),
    provider_updated_at  TEXT,
    last_synced_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    UNIQUE (calendar_account_id, provider_event_id)
);

CREATE INDEX IF NOT EXISTS idx_calendar_events_admin_time
    ON calendar_events (admin_id, start_time_utc, end_time_utc);

-- ──────────────────────────────────────────
-- bookings
-- ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bookings (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    admin_id          INTEGER NOT NULL REFERENCES admins(id) ON DELETE CASCADE,
    calendar_event_id INTEGER REFERENCES calendar_events(id) ON DELETE SET NULL,
    visitor_name      TEXT NOT NULL,
    visitor_email     TEXT NOT NULL,
    visitor_phone     TEXT,
    notes             TEXT,
    status            TEXT NOT NULL DEFAULT 'CONFIRMED'
                          CHECK (status IN ('CONFIRMED', 'CANCELLED', 'RESCHEDULED')),
    cancel_token      TEXT NOT NULL UNIQUE,
    reschedule_token  TEXT NOT NULL UNIQUE,
    created_at        TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_bookings_cancel_token     ON bookings (cancel_token);
CREATE INDEX IF NOT EXISTS idx_bookings_reschedule_token ON bookings (reschedule_token);
```

### Notes on design choices

- `is_primary` is stored per `calendar_account` row (not as a separate table) because only one primary exists per admin. Enforcing this constraint in application code (see TASK-007/008) is sufficient for MVP.
- The `calendar_events` index on `(admin_id, start_time_utc, end_time_utc)` is the hot path for availability queries.
- `bookings.calendar_event_id` is nullable (`ON DELETE SET NULL`) so a booking record survives if its associated local event cache entry is purged during a resync.

### Seed data (optional, for development)

Create `V2__seed_dev_data.sql` only if a dev profile is active. Leave this out for now — the application creates the admin via signup (TASK-005).

---

## Acceptance Criteria

- Application starts and Flyway logs `Successfully applied 1 migration to schema "main"`.
- All five tables exist in the SQLite file (`sqlite3 data/unified-calendar.db .tables` lists them).
- All constraints and indexes are present (verify with `.schema table_name` in sqlite3 CLI).
- Dropping the database file and restarting recreates the schema cleanly (idempotent migration).
- A second restart does not re-run the migration (Flyway versioning works correctly).
