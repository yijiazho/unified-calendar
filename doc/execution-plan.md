# Execution Plan

## Dependency Graph

Each task lists what must be done before it can start.

| Task | Hard Dependencies | Status |
|---|---|---|
| TASK-001 INFRA docker-compose | 002, 003 | ✓ |
| TASK-002 BE project-scaffold | — | ✓ |
| TASK-003 FE project-scaffold | — | ✓ |
| TASK-004 BE database-schema | 002 | ✓ |
| TASK-005 BE admin-auth | 002, 004 | ✓ |
| TASK-006 FE admin-auth | 003, 005 | ✓ |
| TASK-007 BE google-oauth | 002, 004, 005 | ✓ |
| TASK-008 BE outlook-oauth | 002, 004, 005, 007 | ✓ |
| TASK-009 FE calendar-connect | 003, 006, 007, 008 | ✓ |
| TASK-010 BE working-hours | 002, 004, 005 | ✓ |
| TASK-011 FE working-hours | 003, 006, 010 | ✓ |
| TASK-012 BE calendar-sync | 002, 004, 007, 008 | ✓ |
| TASK-013 BE unified-calendar-api | 002, 004, 012 | ✓ |
| TASK-014 FE unified-calendar-view | 003, 006, 013 | ✓ |
| TASK-015 BE availability-engine | 004, 010, 012 | ✓ |
| TASK-016 BE public-api | 004, 005, 015 | ✓ |
| TASK-017 FE public-availability-page | 003, 016 | |
| TASK-018 BE booking | 004, 007, 008, 015, 020 | |
| TASK-019 FE booking-form | 003, 017, 018 | |
| TASK-020 BE email-service | 002, 021 | |
| TASK-021 BE ics-generation | 004 | |
| TASK-022 BE cancellation | 004, 018, 020 | |
| TASK-023 FE cancellation | 003, 022 | |
| TASK-024 BE rescheduling | 004, 015, 018, 020, 022 | |
| TASK-025 FE rescheduling | 003, 017, 024 | |

> Note: TASK-020 (email) and TASK-021 (ICS) are placed before TASK-018 (booking)
> because booking wires them in asynchronously. TASK-021 only needs the Booking
> entity shape, which is fully defined by the schema in TASK-004.

---

## Parallel Execution Waves

Tasks in the same wave have all their dependencies satisfied by the previous waves
and can be worked on concurrently.

```
Wave 1  ──────────────────────────────────────────────────────────────────
  ✓ 002 BE scaffold          ✓ 003 FE scaffold

Wave 2  ──────────────────────────────────────────────────────────────────
  ✓ 001 INFRA docker          ✓ 004 BE schema

Wave 3  ──────────────────────────────────────────────────────────────────
  ✓ 005 BE admin-auth

Wave 4  ──────────────────────────────────────────────────────────────────
  ✓ 006 FE admin-auth         ✓ 007 BE google-oauth
  ✓ 010 BE working-hours        021 BE ics-generation

Wave 5  ──────────────────────────────────────────────────────────────────
  ✓ 008 BE outlook-oauth      ✓ 011 FE working-hours
    020 BE email-service

Wave 6  ──────────────────────────────────────────────────────────────────
  ✓ 009 FE calendar-connect   ✓ 012 BE calendar-sync

Wave 7  ──────────────────────────────────────────────────────────────────
  ✓ 013 BE unified-calendar-api  ✓ 015 BE availability-engine

Wave 8  ──────────────────────────────────────────────────────────────────
  ✓ 014 FE unified-calendar-view ✓ 016 BE public-api
  018 BE booking

Wave 9  ──────────────────────────────────────────────────────────────────
  017 FE public-availability     022 BE cancellation

Wave 10 ──────────────────────────────────────────────────────────────────
  019 FE booking-form            024 BE rescheduling

Wave 11 ──────────────────────────────────────────────────────────────────
  023 FE cancellation            025 FE rescheduling
```

**Minimum waves (critical path):** 11
**Maximum parallelism:** Wave 4 and Wave 8 each have 4 tasks running concurrently.

---

## Solo Developer — Sequential Order with Milestones

When working alone, follow this order. Each milestone is a testable checkpoint
where a meaningful slice of the system works end-to-end.

### Milestone 0 — Runnable skeleton

Build the foundations so the full stack can start before any feature code is written.

```
1.  ✓ TASK-002  BE project scaffold
2.  ✓ TASK-003  FE project scaffold
3.  ✓ TASK-004  BE database schema
4.  ✓ TASK-001  INFRA docker-compose
```

**Verify:** `docker compose up --build` starts both services. Health check passes.
Frontend serves the placeholder React app at `localhost`.

---

### Milestone 1 — Admin can log in

```
5.  ✓ TASK-005  BE admin authentication
6.  ✓ TASK-006  FE admin authentication
```

**Verify:** Admin can sign up, log in, refresh the page and stay logged in, log out.
Protected routes redirect to `/login` without a session.

---

### Milestone 2 — Admin can connect calendars and set availability

```
7.  ✓ TASK-007  BE Google OAuth
8.  ✓ TASK-010  BE working hours API
9.  ✓ TASK-008  BE Outlook OAuth
10. ✓ TASK-009  FE calendar connect
11. ✓ TASK-011  FE working hours
```

**Verify:** Admin connects a Google account via OAuth. Admin connects an Outlook account.
Admin configures Monday–Friday 9–5. Both saved states survive a page refresh.

---

### Milestone 3 — Unified calendar view (Phase 1 complete)

```
12. ✓ TASK-012  BE calendar sync engine
13. ✓ TASK-013  BE unified calendar API
14. ✓ TASK-014  FE unified calendar view
```

**Verify:** Events from both Google and Outlook appear in the FullCalendar dashboard.
Events are color-coded by source. Switching views (month/week/day) works.
Manual "Sync Now" refreshes the calendar.

---

### Milestone 4 — Public availability page (Phase 1 end-to-end)

```
15. ✓ TASK-015  BE availability engine
16. ✓ TASK-016  BE public API
17. TASK-017  FE public availability page
```

**Verify:** Visiting `/{slug}` shows available 30-minute slots in the visitor's browser
timezone. Days with no working hours show no slots. A day fully blocked by events
shows no slots.

---

### Milestone 5 — Booking infrastructure

```
18. TASK-021  BE ICS generation
19. TASK-020  BE email service
20. TASK-018  BE booking API
```

**Verify:** `POST /bookings` with a valid slot creates an event in the primary
calendar and a booking row in the database. Emails land (check Resend dashboard
or use a test email). ICS attaches correctly to the confirmation email.

---

### Milestone 6 — Visitor can book (Phase 2 core complete)

```
21. TASK-019  FE booking form
```

**Verify:** Full visitor flow works end-to-end:
pick a slot → fill in details → confirm → receive email with ICS → event appears
in the admin's calendar view.

---

### Milestone 7 — Cancel and reschedule (Phase 2 complete)

```
22. TASK-022  BE cancellation
23. TASK-023  FE cancellation
24. TASK-024  BE rescheduling
25. TASK-025  FE rescheduling
```

**Verify:** Visitor clicks cancel link from email → confirms → event disappears from
admin's calendar. Visitor clicks reschedule link → picks new slot → event updates
in the admin's calendar → both receive updated emails with new ICS.

---

## Summary Table

| # | Task | Layer | Milestone | Status |
|---|---|---|---|---|
| 1 | TASK-002 BE project-scaffold | BE | 0 | ✓ |
| 2 | TASK-003 FE project-scaffold | FE | 0 | ✓ |
| 3 | TASK-004 BE database-schema | BE | 0 | ✓ |
| 4 | TASK-001 INFRA docker-compose | INFRA | 0 | ✓ |
| 5 | TASK-005 BE admin-auth | BE | 1 | ✓ |
| 6 | TASK-006 FE admin-auth | FE | 1 | ✓ |
| 7 | TASK-007 BE google-oauth | BE | 2 | ✓ |
| 8 | TASK-010 BE working-hours | BE | 2 | ✓ |
| 9 | TASK-008 BE outlook-oauth | BE | 2 | ✓ |
| 10 | TASK-009 FE calendar-connect | FE | 2 | ✓ |
| 11 | TASK-011 FE working-hours | FE | 2 | ✓ |
| 12 | TASK-012 BE calendar-sync | BE | 3 | ✓ |
| 13 | TASK-013 BE unified-calendar-api | BE | 3 | ✓ |
| 14 | TASK-014 FE unified-calendar-view | FE | 3 | ✓ |
| 15 | TASK-015 BE availability-engine | BE | 4 | ✓ |
| 16 | TASK-016 BE public-api | BE | 4 | ✓ |
| 17 | TASK-017 FE public-availability-page | FE | 4 | |
| 18 | TASK-021 BE ics-generation | BE | 5 | |
| 19 | TASK-020 BE email-service | BE | 5 | |
| 20 | TASK-018 BE booking | BE | 5 | |
| 21 | TASK-019 FE booking-form | FE | 6 | |
| 22 | TASK-022 BE cancellation | BE | 7 | |
| 23 | TASK-023 FE cancellation | FE | 7 | |
| 24 | TASK-024 BE rescheduling | BE | 7 | |
| 25 | TASK-025 FE rescheduling | FE | 7 | |
