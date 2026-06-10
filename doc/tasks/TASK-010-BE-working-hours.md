# TASK-010 · BE · Working Hours API

## Context

Allows the admin to define per-weekday availability windows. These windows are the outer boundary within which free slots are computed (TASK-015). Stored as local times (HH:MM) interpreted in the admin's configured timezone.

Depends on: TASK-002, TASK-004 (schema — `working_hours` table), TASK-005 (session).

---

## Instructions

### Entity

```java
public record WorkingHours(
    Long id,
    Long adminId,
    int dayOfWeek,    // 0=Monday … 6=Sunday (ISO-8601 convention)
    String startTime, // "HH:MM" local time in admin's timezone
    String endTime    // "HH:MM"
) {}
```

### Repository

```java
// Returns all 7 days; days with no row are absent (treated as unavailable)
List<WorkingHours> findAllByAdminId(Long adminId);

// Replace all working hours for this admin atomically
void replaceAll(Long adminId, List<WorkingHours> hours);
// Implementation: DELETE WHERE admin_id=?, then batch INSERT
```

### Service

```java
public class WorkingHoursService {

    List<WorkingHoursDto> getWorkingHours(Long adminId);

    // Validate then save
    void saveWorkingHours(Long adminId, List<WorkingHoursDto> hours);
    // Validation:
    // - dayOfWeek must be 0–6, no duplicates
    // - startTime and endTime must be valid HH:MM (00:00–23:59)
    // - startTime must be strictly before endTime
    // - Up to 7 entries (one per day)
}
```

### Controller

```
GET /working-hours
  Requires session
  Response 200:
  [
    { "dayOfWeek": 0, "startTime": "09:00", "endTime": "17:00" },
    { "dayOfWeek": 1, "startTime": "09:00", "endTime": "17:00" },
    ...
  ]
  Returns only days that have configured hours (absent days = unavailable).

PUT /working-hours
  Requires session
  Body: same structure as the GET response (array of day objects)
  Semantics: FULL REPLACEMENT — any day not included becomes unavailable.
  Response 200: saved working hours (same structure as GET)
  Response 400: validation error details
```

### Default hours

Do not pre-populate defaults in the database. If no working hours are configured, `GET /working-hours` returns an empty array and the availability engine returns no slots.

### DTO

```java
public record WorkingHoursDto(
    int dayOfWeek,
    String startTime,
    String endTime
) {}
```

---

## Acceptance Criteria

- `GET /working-hours` returns 200 with an empty array when no hours are configured.
- `PUT /working-hours` with valid Monday–Friday 09:00–17:00 saves 5 rows and returns them.
- `PUT /working-hours` with an empty array deletes all working hours.
- `PUT /working-hours` with a duplicate `dayOfWeek` returns 400.
- `PUT /working-hours` where `startTime >= endTime` returns 400.
- `PUT /working-hours` with `dayOfWeek=7` returns 400.
- `GET /working-hours` without a session returns 401.
