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

- Java 17+
- Node.js 20+
- A Google Cloud project with Calendar API enabled
- A Microsoft Azure app registration with Calendars.ReadWrite scope

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

Runs on `http://localhost:8080` by default.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173` by default.

### Environment Variables

Create `backend/src/main/resources/application-local.properties`:

```properties
google.client-id=
google.client-secret=
microsoft.client-id=
microsoft.client-secret=
microsoft.tenant-id=
encryption.secret-key=
resend.api-key=
```

## API Overview

### Admin APIs

```
POST   /auth/login
GET    /calendar/accounts
POST   /calendar/google/connect
POST   /calendar/outlook/connect
DELETE /calendar/accounts/{id}
PUT    /calendar/primary
PUT    /working-hours
GET    /calendar/events
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
