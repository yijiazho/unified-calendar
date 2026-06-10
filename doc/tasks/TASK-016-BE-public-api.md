# TASK-016 · BE · Public Availability API

## Context

Exposes unauthenticated endpoints for the public scheduling page. The visitor provides the admin's slug and a date; the server returns available 30-minute slots. No session is required.

Depends on: TASK-004, TASK-005 (admin lookup by slug), TASK-015 (availability engine).

---

## Instructions

### Endpoints

```
GET /s/{slug}
No auth required.

Response 200:
{
  "slug": "jane-smith",
  "name": "Jane Smith",
  "timezone": "America/New_York"
}

Response 404: { "error": "Admin not found" }
```

```
GET /availability?slug=&date=
No auth required.

Query params:
  slug   admin's URL slug (e.g. "jane-smith")
  date   ISO-8601 date in the visitor's local date (e.g. "2024-03-15")

Response 200:
{
  "date": "2024-03-15",
  "adminTimezone": "America/New_York",
  "slots": [
    { "start": "2024-03-15T14:00:00Z", "end": "2024-03-15T14:30:00Z" },
    { "start": "2024-03-15T14:30:00Z", "end": "2024-03-15T15:00:00Z" }
  ]
}

Response 404: admin slug not found
Response 400: invalid date format
```

### Controller

```java
@RestController
public class PublicController {

    @GetMapping("/s/{slug}")
    AdminPublicInfoResponse getAdminInfo(@PathVariable String slug);

    @GetMapping("/availability")
    AvailabilityResponse getAvailability(
        @RequestParam String slug,
        @RequestParam String date   // parsed as LocalDate
    );
}
```

### `AdminPublicInfoResponse`

```java
public record AdminPublicInfoResponse(
    String slug,
    String name,     // admin's email username part or full name if added later
    String timezone
) {}
```

For MVP, `name` can be derived from the admin's email (part before `@`). A dedicated `display_name` column can be added in a future migration.

### `AvailabilityResponse`

```java
public record AvailabilityResponse(
    String date,
    String adminTimezone,
    List<TimeSlotResponse> slots
) {}

public record TimeSlotResponse(
    String start,  // ISO-8601 UTC
    String end
) {}
```

### Availability lookup flow

```
1. Load admin by slug → 404 if not found
2. Parse date as LocalDate
3. Call AvailabilityService.getAvailableSlots(adminId, date)
   (uses admin's timezone from the admins table)
4. Map TimeSlot list to TimeSlotResponse (Instant → ISO-8601 string)
5. Return AvailabilityResponse
```

### Caching (optional, MVP)

No caching needed for MVP — SQLite queries are fast enough for a single-admin deployment. Add a short cache-control header to limit hammering:

```
Cache-Control: public, max-age=60
```

### Rate limiting (out of scope for MVP)

Noted for future: add a per-IP rate limiter on `/availability` to prevent scraping.

---

## Acceptance Criteria

- `GET /s/jane-smith` returns the admin's public info if the slug exists.
- `GET /s/nonexistent` returns 404.
- `GET /availability?slug=jane-smith&date=2024-03-15` returns correctly computed slots for that day.
- On a day with no working hours configured, `slots` is an empty array.
- These endpoints are accessible without any session cookie.
- An authenticated admin request also works (session is ignored, not required).
- Invalid `date` format returns 400.
