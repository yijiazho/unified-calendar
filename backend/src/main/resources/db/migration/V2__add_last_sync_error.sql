-- Separate the "last sync failed" state from the "never synced" state.
-- last_sync_at now records only the last *successful* sync; last_sync_error holds the
-- most recent failure message (NULL when the last sync succeeded or no sync has run).
ALTER TABLE calendar_accounts ADD COLUMN last_sync_error TEXT;
