# CLAUDE.md — Unified Calendar Scheduling Service

## Project Overview

Self-hosted scheduling service (Calendly-like). A single admin connects multiple Google/Outlook calendars; external visitors book appointments through a public availability page.

## Tech Stack

- **Backend**: Spring Boot (Java 21), SQLite via JDBC, Spring Scheduler
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
