# CLAUDE.md — Unified Calendar Scheduling Service

## Project Overview

Self-hosted scheduling service (Calendly-like). A single admin connects multiple Google/Outlook calendars; external visitors book appointments through a public availability page.

## Tech Stack

- **Backend**: Spring Boot (Java 17), SQLite via JDBC, Spring Scheduler
- **Frontend**: React + TypeScript, FullCalendar, Vite
- **Email**: Resend
- **Auth**: Session-based admin auth (email/password) + OAuth2 for calendar providers
- **Deployment**: Docker Compose on Ubuntu

## Repository Layout

```
backend/     Spring Boot application (src/main/java/...)
frontend/    React + TypeScript (src/...)
doc/         PRD and design document
```

## Architecture Principles

- **SQLite is the source of truth for scheduling.** Availability calculations always query `calendar_events` in SQLite — never call provider APIs directly for slot computation.
- **Provider APIs are used only for**: initial sync, booking validation (live check before confirming), event creation, update, and deletion.
- **5-minute polling sync** via `@Scheduled` — acceptable staleness for MVP. Live validation before booking prevents double-booking.
- **UTC storage everywhere.** All timestamps stored as UTC; convert to browser timezone on the frontend.
- **No approval workflow in MVP.** Bookings are auto-confirmed and written directly to the primary calendar.

## Key Database Tables

| Table | Purpose |
|---|---|
| `admins` | Admin accounts (email, password hash, slug, timezone) |
| `calendar_accounts` | Connected Google/Outlook accounts with encrypted tokens |
| `working_hours` | Per-weekday availability windows |
| `calendar_events` | Normalized event cache from all providers (source of truth) |
| `bookings` | Visitor bookings with cancel/reschedule tokens |

## Availability Algorithm

```
Load working hours window for the day
  → Load busy calendar_events in that window
  → Merge overlapping intervals
  → Subtract busy from working hours
  → Split remaining free time into 30-minute slots
  → Return slots
```

## OAuth Scopes

- **Google**: `calendar.readonly`, `calendar.events`
- **Microsoft**: `Calendars.Read`, `Calendars.ReadWrite`
- Store access and refresh tokens **encrypted at rest**.

## API Conventions

- Admin endpoints require session auth.
- Public endpoints (`/s/{slug}`, `/availability`, `/bookings`, cancel/reschedule) are unauthenticated.
- Booking cancel/reschedule is token-based (no login required for visitors).

## Phase Boundaries

**Phase 1** (current focus): auth, calendar connect, sync, unified view, working hours, public availability page.

**Phase 2**: booking form, event creation in primary calendar, email + ICS, cancellation, rescheduling.

## What Is Out of Scope (MVP)

- Multi-admin / team scheduling / round-robin
- Recurring event handling
- Approval workflows
- Webhook-based sync (polling only for MVP)
- Mobile applications

## Instructions for AI Agents

### Starting a Task

Before writing any code, read the task file under `doc/tasks/`. Each task file has three sections — **Context**, **Instructions**, and **Acceptance Criteria**. Use them as follows:

1. Read the task file fully.
2. Create a `TaskCreate` entry for each top-level instruction step and each acceptance criterion that requires active implementation work.
3. Mark each task `in_progress` before starting it; mark it `completed` immediately after finishing — do not batch.
4. Verify every acceptance criterion is met before declaring the task done.

### Code and Comments

- Write a one-line Javadoc or JSDoc comment on every public method explaining **why** it exists or any non-obvious contract. Do not describe what the code mechanically does.
- Do not add inline comments that restate logic; do not write multi-line comment blocks.
- Do not introduce abstractions, error handling, or features beyond what the task explicitly requires.

### Backend Strategy (Spring Boot / Java)

- Use plain Spring JDBC (no JPA). SQL lives in the repository class, never scattered elsewhere.
- All timestamps stored and returned as UTC (`Instant` / `OffsetDateTime`).
- Token encryption must use `EncryptionConfig` — never roll ad-hoc crypto.
- For each new feature, write unit tests for the service layer and integration tests that hit the real SQLite database. Do not mock the database.
- Run `./mvnw test` and confirm all tests pass before marking tasks complete.

### Frontend Strategy (React / TypeScript)

- All types must be explicit — no `any`.
- Convert UTC timestamps to browser timezone at the display layer only; never store local time.
- For each new UI feature, test the golden path and at least one edge case in the browser before marking tasks complete. State this explicitly in your completion note.
- Run `npm run build` (type-check) and confirm it passes before marking tasks complete.

### Architecture Rules

- Availability calculations must query `calendar_events` in SQLite — never call provider APIs for slot computation.
- Respect phase boundaries: do not implement Phase 2 features while working on Phase 1.
- Read every file with the `Read` tool before editing it.
- Confirm with the user before any destructive action (deleting files, dropping tables, force-pushing).

### Knowledge Base

After completing any code changes (backend or frontend), invoke `/update-kb` to keep the knowledge base current. This ensures future agents start with accurate patterns, fixed risks are removed, and new watch points are recorded.

---

## Running Locally

```bash
# Backend
cd backend && ./mvnw spring-boot:run

# Frontend
cd frontend && npm install && npm run dev
```

Backend: `http://localhost:8080`
Frontend: `http://localhost:5173`

## Environment / Secrets

All secrets go in `backend/src/main/resources/application-local.properties` (gitignored).
Required keys: `google.client-id`, `google.client-secret`, `microsoft.client-id`, `microsoft.client-secret`, `microsoft.tenant-id`, `encryption.secret-key`, `resend.api-key`.
