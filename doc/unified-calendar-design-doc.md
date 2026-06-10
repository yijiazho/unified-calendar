# Unified Calendar Scheduling Service - Design Document

## 1. Overview

### Purpose
Unified scheduling platform that aggregates Google and Outlook calendars, computes availability, allows visitors to schedule appointments, and creates events in a designated primary calendar.

### Goals

#### Phase 1
- Connect calendars
- Sync events
- Show merged calendar
- Publish availability page

#### Phase 2
- Book appointments
- Send emails
- Generate ICS invites
- Support cancellation and rescheduling

---

## 2. High-Level Architecture

```text
React Frontend
      |
Spring Boot Monolith
      |
    SQLite
      |
Google Calendar API
Microsoft Graph API
Email Provider
```

### Components

#### Admin Portal
- Login
- Connect calendars
- Configure working hours
- View merged calendar

#### Public Scheduling Page
- Display availability
- Booking form
- Reschedule flow
- Cancellation flow

#### Backend Modules
- Auth Module
- Calendar Integration Module
- Availability Module
- Booking Module
- Email Module

---

## 3. Database Design

### Design Principle

SQLite is the source of truth for the local event cache.

All availability calculations operate against SQLite rather than directly querying provider APIs.

Google Calendar and Outlook are treated as external systems that synchronize into the local database.

### admins

```sql
id
email
password_hash
slug
timezone
created_at
updated_at
```

### calendar_accounts

```sql
id
admin_id
provider
provider_account_id
email
encrypted_access_token
encrypted_refresh_token
is_primary
connected_at
last_sync_at
```

### working_hours

```sql
id
admin_id
day_of_week
start_time
end_time
```

### calendar_events

```sql
id
admin_id
calendar_account_id
provider
provider_event_id
title
start_time_utc
end_time_utc
is_booking_event
provider_updated_at
last_synced_at
```

Notes:
- Stores normalized events from all providers
- Used by the availability engine
- Rebuilt incrementally through synchronization jobs
- Serves as the source of truth for scheduling calculations

### bookings

```sql
id
admin_id
calendar_event_id
visitor_name
visitor_email
visitor_phone
notes
status
cancel_token
reschedule_token
created_at
```

---

## 4. OAuth Integration Design

### Google Calendar

Scopes:
- calendar.readonly
- calendar.events

OAuth Authorization Code Flow.

Store access token and refresh token encrypted at rest.

### Microsoft Outlook

Scopes:
- Calendars.Read
- Calendars.ReadWrite

Store access token and refresh token encrypted at rest.

---

## 5. Synchronization Engine

### Strategy

Polling every 5 minutes.

### Implementation

Spring Scheduler:

```java
@Scheduled
```

### Sync Flow

```text
Refresh token if needed
        ↓
Fetch events
        ↓
Normalize provider event
        ↓
Store in calendar_events
```

### Rationale

Pros:
- Simple
- Reliable
- Easy to debug

Cons:
- Up to 5 minutes of staleness

Mitigation:
- Live provider validation before booking confirmation

### Normalized Event Model

All provider-specific events are converted into a common internal model:

```java
CalendarEvent
```

---

## 6. Availability Engine

### Inputs

Working hours:
- Monday–Friday
- 09:00–17:00

Busy intervals:
- calendar_events

### Algorithm

```text
Load working window
        ↓
Load busy events
        ↓
Merge overlapping intervals
        ↓
Subtract from working window
        ↓
Split into 30-minute slots
        ↓
Return available slots
```

---

## 7. Booking Flow

### Visitor Books Slot

```text
Select Slot
    ↓
Submit Details
    ↓
POST /bookings
```

### Validation

1. Check SQLite availability cache
2. Check primary provider directly

### Success Path

```text
Create provider event
        ↓
Persist booking
        ↓
Persist local event
        ↓
Send email
        ↓
Return success
```

### Event Creation Strategy

Bookings are immediately created in the primary calendar.

No approval workflow is included in MVP.

---

## 8. Email Design

### Provider Recommendation

Resend

Alternatives:
- SMTP
- Mailgun
- SendGrid

### Emails

Visitor:
- Booking confirmation
- Cancellation confirmation
- Reschedule confirmation

Admin:
- New booking notification
- Cancellation notification
- Reschedule notification

---

## 9. ICS Design

Generate ICS files locally.

Include:
- Title
- Description
- Start Time
- End Time
- Timezone

Attach to confirmation emails.

---

## 10. API Design

### Admin APIs

```http
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

```http
GET    /s/{slug}
GET    /availability
POST   /bookings
POST   /bookings/{token}/cancel
POST   /bookings/{token}/reschedule
```

---

## 11. Deployment

### MVP Deployment

```text
Ubuntu
Docker Compose
Spring Boot
React
SQLite
```

### Hosting Options

- DigitalOcean
- Hetzner
- AWS EC2

### Rationale

- Low cost
- Minimal infrastructure
- Easy deployment

---

## 12. Future Evolution

Designed to support:
- Multi-admin support
- Team scheduling
- Round-robin assignment
- Recurring events
- Webhook synchronization
- PostgreSQL migration
- Approval workflows

without major architectural rewrites.

---

## Final Architectural Decisions

- Spring Boot monolith
- React + TypeScript frontend
- SQLite database
- OAuth integrations only
- Polling synchronization every 5 minutes
- Local event cache stored in SQLite
- SQLite is source of truth for scheduling calculations
- Direct event creation in primary calendar
- FullCalendar for admin calendar UI
- UTC storage with local timezone rendering
- Browser timezone detection
- Resend for transactional email
- ICS generation in application

### Key Principle

SQLite serves as the authoritative local scheduling store.

Availability calculations never depend on live provider calls.

Provider APIs are used only for:
- Synchronization
- Booking validation
- Event creation
- Event updates
- Event deletion
