# TASK-015 · BE · Availability Engine

## Context

The core scheduling algorithm. Given an admin and a date, computes which 30-minute slots are free by subtracting all busy calendar events from the configured working hours window. This is the authoritative source for availability — no live provider calls.

Depends on: TASK-004, TASK-010 (working hours), TASK-012 (calendar_events cache).

---

## Instructions

### Algorithm (pure Java, no DB writes)

```
Input: adminId, date (LocalDate), adminTimezone (ZoneId)

1. Load working hours for that day of week (from working_hours table)
   → If no working hours for that day → return empty list

2. Convert working hours window to UTC Instants
   working_start_utc = LocalDateTime.of(date, LocalTime.parse(startTime))
                         .atZone(adminTimezone).toInstant()
   working_end_utc   = LocalDateTime.of(date, LocalTime.parse(endTime))
                         .atZone(adminTimezone).toInstant()

3. Load busy events from calendar_events where:
     admin_id = adminId
     start_time_utc < working_end_utc
     end_time_utc   > working_start_utc

4. Merge overlapping busy intervals:
   - Sort by start_time_utc
   - Sweep: if next interval starts before current merged interval ends, extend
   - Result: list of non-overlapping busy intervals

5. Subtract busy intervals from working window:
   - Start with free = [ (working_start_utc, working_end_utc) ]
   - For each busy interval, punch a hole in the free list
   - Result: list of free intervals

6. Split free intervals into 30-minute slots:
   - For each free interval, generate slots at 0, 30, 60... minutes from start
   - Include slot only if slot_end <= interval_end (slot must fit completely)
   - Discard any slot that starts in the past (Instant.now())

7. Return list of TimeSlot { Instant start, Instant end }
```

### `AvailabilityService`

```java
@Service
public class AvailabilityService {

    // Returns available 30-minute slots for a given date
    List<TimeSlot> getAvailableSlots(Long adminId, LocalDate date);

    // Used at booking time: is a specific slot still free?
    boolean isSlotAvailable(Long adminId, Instant slotStart, Instant slotEnd);
    // Calls getAvailableSlots and checks if the given interval appears in the result
}
```

### `TimeSlot` DTO

```java
public record TimeSlot(Instant start, Instant end) {}
```

### Interval arithmetic helpers

Implement `IntervalUtils` with static methods:

```java
// Merge a list of potentially overlapping intervals into a sorted, non-overlapping list
List<Interval> merge(List<Interval> intervals);

// Subtract a list of busy intervals from a free window
// Returns the remaining free intervals
List<Interval> subtract(Interval window, List<Interval> busyIntervals);
```

Keep these pure (no I/O) so they are unit-testable in isolation.

### Edge cases to handle

| Scenario | Expected behavior |
|---|---|
| No working hours configured for the day | Return `[]` |
| All day is busy (fully covered by events) | Return `[]` |
| Busy interval starts before working window | Clip to working window start |
| Busy interval ends after working window | Clip to working window end |
| Overlapping busy events | Merge before subtracting |
| Slot starts in the past | Exclude |
| Free interval shorter than 30 minutes | No slot generated from it |

---

## Acceptance Criteria

- For a day with 09:00–17:00 working hours and no events: returns 16 slots (09:00, 09:30 … 16:30).
- A busy event from 10:00–11:00 removes the 10:00 and 10:30 slots, leaving 14 slots.
- Two overlapping busy events (10:00–11:00 and 10:30–12:00) are merged before subtraction.
- `isSlotAvailable` returns false when the slot overlaps a busy event.
- A day with no configured working hours returns an empty list.
- Slots that started before `now()` are excluded.
- `IntervalUtils.merge` and `IntervalUtils.subtract` are covered by unit tests.
