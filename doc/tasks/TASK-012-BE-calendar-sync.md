# TASK-012 · BE · Calendar Sync Engine

## Context

Imports events from all connected Google and Outlook accounts into `calendar_events` every 5 minutes. The local cache is the sole input to the availability engine, so correctness here is critical. Recurring events are skipped in MVP.

Depends on: TASK-002, TASK-004, TASK-007 (Google token refresh), TASK-008 (Outlook token refresh).

---

## Instructions

### Normalized event model

All provider events convert to this before being stored:

```java
public record CalendarEvent(
    Long id,
    Long adminId,
    Long calendarAccountId,
    String provider,
    String providerEventId,
    String title,
    Instant startTimeUtc,
    Instant endTimeUtc,
    boolean isBookingEvent,
    Instant providerUpdatedAt,
    Instant lastSyncedAt
) {}
```

### Repository

```java
// Upsert: INSERT OR REPLACE based on (calendar_account_id, provider_event_id)
void upsert(CalendarEvent event);

// Remove events for an account that were not seen in the latest sync
// (handles deletions on the provider side)
void deleteByAccountIdNotIn(Long calendarAccountId, List<String> seenProviderEventIds);

List<CalendarEvent> findByAdminIdAndTimeRange(Long adminId, Instant from, Instant to);
```

### Sync service

```java
@Service
public class CalendarSyncService {

    @Scheduled(fixedDelay = 300_000)  // 5 minutes
    public void syncAll() {
        // For each admin, for each connected calendar_account:
        //   syncAccount(account)
    }

    private void syncAccount(CalendarAccount account) {
        // 1. Get a valid access token (refresh if needed via Google/OutlookTokenRefresher)
        // 2. Fetch events from the provider for a rolling window:
        //    from = now - 1 day (catch recently started events)
        //    to   = now + 60 days
        // 3. For each raw provider event:
        //    a. Skip if it is a recurring event instance (Google: recurringEventId present;
        //       Outlook: type == "occurrence" or "exception")
        //    b. Normalize to CalendarEvent
        //    c. Upsert into calendar_events
        // 4. Delete local events for this account whose provider_event_id was not in the response
        //    (this handles provider-side deletions)
        // 5. Update calendar_accounts.last_sync_at = now
    }
}
```

### Google adapter

```java
@Component
public class GoogleSyncAdapter {

    List<CalendarEvent> fetchEvents(CalendarAccount account, String accessToken,
                                    Instant from, Instant to);
    // Use the Google Calendar Java client:
    //   calendar.events().list("primary")
    //     .setTimeMin(from)
    //     .setTimeMax(to)
    //     .setSingleEvents(true)   // expand recurring; we then filter them out
    //     .execute()
    // Note: setSingleEvents(true) is needed to get concrete times,
    //   but then skip any event that has a recurringEventId field set.
    //
    // Normalize:
    //   providerEventId = event.getId()
    //   title = event.getSummary()
    //   startTimeUtc = parse event.getStart().getDateTime() (or getDate() for all-day → skip)
    //   endTimeUtc   = parse event.getEnd().getDateTime()
    //   providerUpdatedAt = event.getUpdated()
}
```

### Outlook adapter

```java
@Component
public class OutlookSyncAdapter {

    List<CalendarEvent> fetchEvents(CalendarAccount account, String accessToken,
                                    Instant from, Instant to);
    // HTTP GET https://graph.microsoft.com/v1.0/me/calendarView
    //   ?startDateTime={from}&endDateTime={to}
    //   Header: Authorization: Bearer {accessToken}
    // calendarView automatically expands recurring events as instances.
    // Skip instances where type == "occurrence" or type == "exception".
    //
    // Normalize:
    //   providerEventId = event.id
    //   title = event.subject
    //   startTimeUtc = parse event.start.dateTime (UTC) or event.start.timeZone
    //   endTimeUtc   = parse event.end.dateTime
    //   providerUpdatedAt = event.lastModifiedDateTime
}
```

### Error handling

- If a token refresh fails (e.g. revoked refresh token): log the error, mark the account with `last_sync_at = null`, skip that account, continue with others. Do not crash the scheduler.
- If the provider API returns a transient error (5xx): log and skip — the next 5-minute cycle will retry.
- Individual account failures must not prevent other accounts from syncing.

### Timezone note

All-day events from Google have a `date` field instead of `dateTime`. For MVP, skip all-day events (they have no definite UTC instant and are complex to handle). Log a debug message when skipping.

---

## Acceptance Criteria

- On startup, the sync runs once within the first 5 minutes.
- Events from a connected Google account appear in `calendar_events` after a sync cycle.
- Events from a connected Outlook account appear in `calendar_events` after a sync cycle.
- Deleting an event in Google/Outlook is reflected (row removed) after the next sync cycle.
- Recurring event instances are not inserted into `calendar_events`.
- All-day events are skipped.
- A sync cycle completes even if one account has an expired/revoked token (other accounts still sync).
- `calendar_accounts.last_sync_at` is updated after a successful account sync.
