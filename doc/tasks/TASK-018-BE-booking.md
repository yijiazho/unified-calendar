# TASK-018 · BE · Booking API

## Context

The transactional core of Phase 2. A visitor submits a booking request; the backend validates the slot twice (SQLite cache + live provider check), creates the event in the primary calendar, persists the booking, then triggers emails asynchronously. Correctness and atomicity here are critical.

Depends on: TASK-004, TASK-007/008 (provider event creation), TASK-015 (availability engine), TASK-020 (email service, triggered async).

---

## Instructions

### Endpoint

```
POST /bookings
No auth required (public endpoint).

Request body:
{
  "slug": "jane-smith",
  "slotStart": "2024-03-15T14:00:00Z",
  "slotEnd":   "2024-03-15T14:30:00Z",
  "visitorName":  "John Doe",
  "visitorEmail": "john@example.com",
  "visitorPhone": "+1-555-0100",
  "notes": "First meeting"
}

Response 201:
{
  "bookingId": 42,
  "visitorName": "John Doe",
  "slotStart": "2024-03-15T14:00:00Z",
  "slotEnd":   "2024-03-15T14:30:00Z",
  "adminName": "jane-smith",
  "cancelToken": "uuid-here",
  "rescheduleToken": "uuid-here"
}

Response 409: { "error": "This time slot is no longer available." }
Response 400: { "error": "Validation error details" }
Response 404: { "error": "Admin not found" }
```

### Validation

**Request-level**:
- `slug` → must resolve to an admin.
- `slotStart` and `slotEnd` → valid ISO-8601 UTC; slotEnd must be exactly 30 minutes after slotStart.
- `visitorName` → required, max 200 chars.
- `visitorEmail` → required, valid email format.
- `visitorPhone` → optional, max 50 chars.
- `notes` → optional, max 2000 chars.

**Slot-level (double check)**:

1. **SQLite check**: call `AvailabilityService.isSlotAvailable(adminId, slotStart, slotEnd)`. Return 409 if false.
2. **Live provider check**: call the primary calendar's provider API and verify no event overlaps the requested slot.
   - Google: `events.list` with `timeMin=slotStart`, `timeMax=slotEnd`, `singleEvents=true`. If results are non-empty, return 409.
   - Outlook: `calendarView` with `startDateTime=slotStart`, `endDateTime=slotEnd`. If results are non-empty, return 409.

### Booking creation (happy path)

Execute the following steps. If any step fails, roll back previous steps:

```
1. Create event in primary calendar via provider API:
   Title: "Meeting with {visitorName}"
   Description: "Phone: {phone}\nNotes: {notes}"
   Start: slotStart, End: slotEnd, TimeZone: UTC

2. Insert into calendar_events:
   (is_booking_event = true, provider_event_id from step 1)

3. Insert into bookings:
   - Generate cancel_token = UUID.randomUUID().toString()
   - Generate reschedule_token = UUID.randomUUID().toString()
   - status = "CONFIRMED"

4. Return booking response

5. (async) EmailService.sendBookingEmails(booking)
```

Step 5 is fire-and-forget (`@Async`) — do not wait for it before responding.

### Rollback strategy

If the provider event was created (step 1) but the database insert (step 2 or 3) fails:
- Attempt to delete the provider event.
- Log the failure.
- Return 500 to the visitor.

Use a `@Transactional` annotation on the booking service method for database steps (2 and 3). Step 1 (external API) is outside the transaction.

### `BookingService`

```java
@Service
public class BookingService {

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) { ... }

    // Used by cancel and reschedule flows
    Booking findByToken(String token, TokenType type);  // TokenType enum: CANCEL, RESCHEDULE
}
```

### `BookingRepository`

```java
Booking save(Booking booking);
Optional<Booking> findByCancelToken(String token);
Optional<Booking> findByRescheduleToken(String token);
void updateStatus(Long id, String status);
```

---

## Acceptance Criteria

- A valid booking request creates a row in `bookings` and a row in `calendar_events`.
- The event appears in the primary provider's calendar (Google or Outlook) after booking.
- `POST /bookings` for an already-taken slot returns 409.
- `POST /bookings` with a missing required field returns 400.
- `POST /bookings` with a non-existent slug returns 404.
- `cancel_token` and `reschedule_token` in the response are UUIDs and unique.
- The email service is called asynchronously (booking response arrives before emails are sent).
- If the live provider check finds a conflict that the SQLite cache missed, 409 is returned.
