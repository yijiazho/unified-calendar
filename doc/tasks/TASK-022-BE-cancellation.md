# TASK-022 · BE · Booking Cancellation API

## Context

Allows a visitor to cancel their appointment using the unique cancel token sent in their confirmation email. No login required. The endpoint deletes the event from the primary calendar, updates the booking status, and triggers cancellation notification emails.

Depends on: TASK-004, TASK-018 (booking entity and repository), TASK-020 (email service).

---

## Instructions

### Endpoint

```
POST /bookings/{cancelToken}/cancel
No auth required.

Path param:
  cancelToken   The UUID from the booking confirmation

Response 200:
{
  "message": "Appointment cancelled successfully.",
  "slotStart": "2024-03-15T14:00:00Z",
  "slotEnd":   "2024-03-15T14:30:00Z"
}

Response 404: { "error": "Booking not found." }
Response 409: { "error": "This appointment has already been cancelled." }
Response 410: { "error": "This appointment is in the past and cannot be cancelled." }
```

### Business rules

- Look up booking by `cancel_token`. Return 404 if not found.
- If `booking.status == 'CANCELLED'` → return 409 (idempotency — do not attempt to cancel twice).
- If `booking.status == 'RESCHEDULED'` → the reschedule token is now active; treat the cancel as acting on the new booking. For MVP, return a 409 with a message: `"This appointment has been rescheduled. Use your reschedule confirmation email to manage it."`.
- If the slot is in the past (slotStart < now) → return 410. The admin can still cancel manually from their calendar.

### Cancellation flow

```
1. Load booking by cancel_token → 404 if not found
2. Validate status (see above)
3. Load the associated CalendarAccount (primary) from calendar_events.calendar_account_id
4. Delete the provider event:
   - Google: calendar.events().delete("primary", providerEventId).execute()
   - Outlook: DELETE https://graph.microsoft.com/v1.0/me/events/{providerEventId}
   Handle provider 404 gracefully (event already deleted) — continue anyway.
5. Delete or mark the calendar_events row:
   - For MVP: delete the row (the next sync cycle will not recreate it since it's gone from provider)
6. Update booking.status = 'CANCELLED'
7. Trigger cancellation emails async: EmailService.sendCancellationEmails(booking, admin)
8. Return 200
```

Use `@Transactional` on steps 5 and 6. Step 4 (provider API) is outside the transaction.

### `CancellationService`

```java
@Service
public class CancellationService {

    @Transactional
    public CancellationResponse cancel(String cancelToken) { ... }
}
```

### Error from provider delete

If the provider API returns a non-404 error when deleting the event (e.g. 401 token expired, 500 server error):
- Log the error.
- Still update the booking status to `CANCELLED` in the database (the admin can clean up the calendar manually).
- Still send cancellation emails.
- Return 200 to the visitor (the appointment is cancelled in our system).

---

## Acceptance Criteria

- `POST /bookings/{validToken}/cancel` removes the event from the primary provider calendar.
- Booking status in the database changes to `CANCELLED`.
- Cancellation emails are sent to visitor and admin.
- A second call with the same token returns 409 (not 404, not 500).
- A token that doesn't exist returns 404.
- Cancelling an already-past appointment returns 410.
- Provider API failure (event already gone) does not prevent the booking from being marked cancelled.
- The endpoint is accessible without a session cookie.
