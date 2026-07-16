-- Atomic slot reservations to prevent booking race conditions.
-- Before creating a provider event, a reservation is inserted and locked by unique constraint.
-- If provider event creation fails, the reservation is deleted (rolled back in application).
-- On successful persistence, the reservation ID becomes part of the booking record.
CREATE TABLE IF NOT EXISTS slot_reservations (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    admin_id     INTEGER NOT NULL REFERENCES admins(id) ON DELETE CASCADE,
    slot_start   TEXT NOT NULL,    -- ISO-8601 UTC, e.g., 2024-03-15T14:00:00Z
    slot_end     TEXT NOT NULL,
    reserved_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    -- Unique constraint: no two reservations for the same admin in overlapping slots
    UNIQUE (admin_id, slot_start, slot_end)
);

CREATE INDEX IF NOT EXISTS idx_slot_reservations_admin_time
    ON slot_reservations (admin_id, slot_start, slot_end);
