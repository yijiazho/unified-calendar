# TASK-024 · BE · Booking Rescheduling API

## Context

Allows a visitor to move their appointment to a different available slot using the unique reschedule token. Reuses the availability engine and live validation from the booking flow. Updates the existing provider event rather than creating a new one.

Depends on: TASK-004, TASK-015 (availability engine), TASK-018 (booking entity), TASK-020 (email service), TASK-022 (shares cancel validation patterns).

---

## Instructions

### Endpoint

```
POST /bookings/{rescheduleToken}/reschedule
No auth required.

Path param:
  rescheduleToken   The UUID from the booking confirmation

Request body:
{
  "newSlotStart": "2024-03-16T10:00:00Z",
  "newSlotEnd":   "2024-03-16T10:30:00Z"
}

Response 200:
{
  "bookingId": 42,
  "visitorName": "John Doe",
  "newSlotStart": "2024-03-16T10:00:00Z",
  "newSlotEnd":   "2024-03-16T10:30:00Z",
  "cancelToken": "same-uuid-as-before",
  "rescheduleToken": "same-uuid-as-before"
}

Response 404: { "error": "Booking not found." }
Response 409: { "error": "This appointment has already been cancelled." }
         or:  { "error": "The selected time slot is no longer available." }
Response 400: { "error": "New slot must be exactly 30 minutes." }
Response 410: { "error": "Cannot reschedule a past appointment." }
```

### Business rules

- Look up booking by `reschedule_token`. Return 404 if not found.
- If `booking.status == 'CANCELLED'` → return 409.
- If the **current** slot is in the past (original slotStart < now) → return 410.
- `newSlotEnd` must be exactly 30 minutes after `newSlotStart` → 400 otherwise.
- `newSlotStart` must be in the future → 400 otherwise.

### Rescheduling flow

```
1. Load booking by reschedule_token → 404 if not found
2. Validate status and timing (see above)
3. Double-check new slot availability:
   a. SQLite check: AvailabilityService.isSlotAvailable(adminId, newSlotStart, newSlotEnd) → 409 if false
   b. Live provider check (same as TASK-018 booking) → 409 if conflict found
4. Update the existing provider event (PATCH/update, not delete+create):
   - Google: calendar.events().patch("primary", providerEventId, updatedEvent).execute()
     where updatedEvent has new start/end times
   - Outlook: PATCH https://graph.microsoft.com/v1.0/me/events/{providerEventId}
     Body: { "start": { "dateTime": newSlotStart, "timeZone": "UTC" },
             "end":   { "dateTime": newSlotEnd,   "timeZone": "UTC" } }
5. Update calendar_events row: new start_time_utc and end_time_utc
6. Update booking: no status change needed (stays CONFIRMED)
   — Keep cancel_token and reschedule_token the same (tokens are reusable)
7. Trigger reschedule emails async: EmailService.sendRescheduleEmails(booking, admin, newStart, newEnd)
8. Return 200 with updated booking details
```

Steps 5 and 6 are `@Transactional`. Step 4 is outside the transaction.

**Why update instead of delete+create?** Updating preserves the provider event ID, which means the visitor's calendar app can update the existing entry rather than creating a new one (important for the ICS reschedule email — same UID).

### `RescheduleService`

```java
@Service
public class RescheduleService {

    @Transactional
    public RescheduleResponse reschedule(String rescheduleToken, Instant newSlotStart, Instant newSlotEnd) { ... }
}
```

### Provider update failure

If the provider update call fails:
- Do not update the database.
- Return 500 to the visitor with: `"Failed to update calendar event. Please try again."`.
- Log the error with full context.

This is safer than updating the DB and leaving the provider out of sync.

---

## Acceptance Criteria

- `POST /bookings/{token}/reschedule` with a valid new slot updates the provider calendar event's time.
- The `calendar_events` row reflects the new start/end times.
- The booking record is unchanged except for the updated event reference.
- Reschedule emails are sent to visitor and admin with the new time.
- The cancel and reschedule tokens remain the same after rescheduling (visitor can reschedule again or cancel).
- Rescheduling to an already-taken slot returns 409.
- Rescheduling a cancelled booking returns 409.
- Rescheduling a past appointment returns 410.
- A new slot that isn't exactly 30 minutes returns 400.
- The endpoint works without a session cookie.
