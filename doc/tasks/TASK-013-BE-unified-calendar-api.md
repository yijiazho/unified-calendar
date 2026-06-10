# TASK-013 · BE · Unified Calendar Events API

## Context

Exposes the local `calendar_events` cache to the admin's FullCalendar dashboard (TASK-014). The frontend sends a date range; this endpoint returns all normalized events for that admin within that range. Events are read directly from SQLite — no live provider calls.

Depends on: TASK-002, TASK-004, TASK-012 (events must exist in DB to be returned).

---

## Instructions

### Endpoint

```
GET /calendar/events?start=&end=
Requires: session (adminId)

Query params:
  start  ISO-8601 date or datetime (e.g. "2024-03-01" or "2024-03-01T00:00:00Z")
  end    ISO-8601 date or datetime (e.g. "2024-03-31" or "2024-03-31T23:59:59Z")

Response 200:
[
  {
    "id": 42,
    "title": "Team standup",
    "start": "2024-03-12T09:00:00Z",
    "end": "2024-03-12T09:30:00Z",
    "provider": "GOOGLE",
    "calendarAccountId": 1,
    "calendarEmail": "user@gmail.com",
    "isBookingEvent": false
  }
]
```

### Query logic

```sql
SELECT ce.*, ca.email AS calendar_email
FROM calendar_events ce
JOIN calendar_accounts ca ON ca.id = ce.calendar_account_id
WHERE ce.admin_id = :adminId
  AND ce.start_time_utc < :end      -- event starts before range end
  AND ce.end_time_utc   > :start    -- event ends after range start
ORDER BY ce.start_time_utc ASC
```

This is an overlap query: returns any event that intersects the requested range, not just events that start within it.

### Date range parsing

Accept both date-only (`2024-03-01`) and datetime strings. If date-only is received, interpret as the start of that day UTC:
- `"2024-03-01"` → `2024-03-01T00:00:00Z`
- `"2024-03-31"` → `2024-03-31T23:59:59Z` (for `end`)

Use `java.time` classes (`LocalDate`, `Instant`, `ZoneOffset.UTC`).

### Response DTO

```java
public record CalendarEventResponse(
    Long id,
    String title,
    String start,        // ISO-8601 UTC string
    String end,
    String provider,
    Long calendarAccountId,
    String calendarEmail,
    boolean isBookingEvent
) {}
```

### Performance

The index `idx_calendar_events_admin_time` on `(admin_id, start_time_utc, end_time_utc)` (created in TASK-004) ensures this query is fast even with thousands of events.

### Manual sync trigger (optional)

Add a convenience endpoint for the admin to force an immediate sync without waiting for the next polling cycle:

```
POST /calendar/sync
Requires: session
Response 202 Accepted
```

Calls `CalendarSyncService.syncAll()` asynchronously (`@Async`). Returns immediately; the sync runs in a background thread.

---

## Acceptance Criteria

- `GET /calendar/events?start=2024-03-01&end=2024-03-31` returns all synced events in March.
- Events from both Google and Outlook accounts appear in the same response.
- An event that spans the start of the range (started before `start`, ends after) is included.
- `calendarEmail` correctly reflects which account the event came from.
- Without a session, returns 401.
- With no events in the range, returns an empty array (not 404).
- `isBookingEvent: true` events (created by the booking flow) are included and distinguishable.
