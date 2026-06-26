# Unified Calendar Scheduling Service

A self-hosted scheduling platform that aggregates Google Calendar and Microsoft Outlook accounts, computes real-time availability, and lets external visitors book appointments — similar to Calendly.

## Features

**Phase 1 (Connect & View)**
- Admin authentication (email/password)
- Connect multiple Google and Outlook accounts via OAuth
- Unified calendar view (month/week/day) across all connected accounts
- Working hours configuration per weekday
- Public availability page at `/{slug}` displayed in the visitor's browser timezone

**Phase 2 (Book & Manage)**
- Visitor booking form (name, email, phone, notes)
- Instant event creation in the designated primary calendar
- Email notifications for admin and visitor (via Resend)
- ICS calendar invite attachment
- Token-based cancellation and rescheduling flows

## Architecture

```
React + TypeScript (Frontend)
         │
Spring Boot Monolith (Backend)
         │
       SQLite
         │
Google Calendar API   Microsoft Graph API   Email Provider
```

SQLite is the authoritative local scheduling store. Availability calculations never hit provider APIs live — events are synced every 5 minutes into the local cache and all slot calculations operate against that cache. A live provider check is performed only at booking confirmation time.

## Tech Stack

| Layer      | Technology                          |
|------------|-------------------------------------|
| Frontend   | React, TypeScript, FullCalendar     |
| Backend    | Spring Boot (Java)                  |
| Database   | SQLite                              |
| Auth       | OAuth2 (Google, Microsoft)          |
| Email      | Resend                              |
| Deployment | Docker Compose on Ubuntu            |

## Project Structure

```
unified-calendar/
├── backend/        # Spring Boot application
├── frontend/       # React + TypeScript application
├── doc/            # Design documents and PRD
└── docker-compose.yml
```

## Getting Started

### Prerequisites

- Docker + Docker Compose
- Java 17+ and Node.js 20+ (only needed for the backend-only and frontend-only flows below)
- A Google Cloud project with Calendar API enabled
- A Microsoft Azure app registration with Calendars.ReadWrite scope

### Full Stack (Docker Compose)

Copy `.env.example` to `.env` and fill in your credentials, then:

```bash
docker compose up --build   # first run
docker compose up           # subsequent runs
```

Frontend: `http://localhost` · Backend: `http://localhost:8080`

### Environment Variables

Create `.env` at the project root (see `.env.example`):

```
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
MICROSOFT_CLIENT_ID=
MICROSOFT_CLIENT_SECRET=
MICROSOFT_TENANT_ID=common
ENCRYPTION_SECRET_KEY=
RESEND_API_KEY=
```

### Backend Only (unit/integration tests, no browser)

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

This creates `backend/data/unified-calendar.db` — a separate file from the Docker volume. Do not mix with a running Docker stack.

### Frontend Only (UI work against a running Docker backend)

```bash
cd frontend
npm install && npm run dev
```

Runs on `http://localhost:5173`; expects the backend at `http://localhost:8080`.

## Frontend Routes

```
/login                  — Admin login
/signup                 — Admin signup
/dashboard              — Unified calendar view (protected)
/settings/calendars     — Calendar account management (protected)
/settings/hours         — Working hours configuration (protected)
/s/:slug                — Public availability page
/book/:slug             — Visitor booking form
/booking/confirm        — Booking confirmation
/cancel/:token          — Token-based cancellation
/reschedule/:token      — Token-based rescheduling
```

## API Overview

### Admin APIs

```
POST   /auth/signup
POST   /auth/login
POST   /auth/logout
GET    /auth/me
GET    /calendar/accounts
GET    /calendar/google/connect
GET    /calendar/outlook/connect
DELETE /calendar/accounts/{id}
PUT    /calendar/primary
GET    /working-hours
PUT    /working-hours
GET    /calendar/events
POST   /calendar/sync
```

### Public APIs

```
GET    /s/{slug}
GET    /availability
POST   /bookings
POST   /bookings/{token}/cancel
POST   /bookings/{token}/reschedule
```

## License

MIT
