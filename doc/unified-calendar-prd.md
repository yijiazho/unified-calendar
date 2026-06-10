# Product Requirements Document (PRD)

## Product Name

Unified Calendar Scheduling Service

## Problem Statement

Admins often manage availability across multiple calendar providers (Google Calendar, Outlook, multiple accounts per provider). Existing availability is fragmented across calendars, making it difficult for external users to determine when an admin is available.

The system provides:

1. A unified calendar view across connected calendar providers.
2. Availability computation across all connected calendars.
3. A public scheduling page for external users.
4. Appointment creation into a designated primary calendar.

---

# Goals

## Primary Goals

- Connect multiple Google and Outlook accounts.
- Aggregate calendar events into a unified calendar.
- Calculate availability using all connected calendars.
- Allow anonymous users to view available slots.
- Allow users to book appointments.
- Create bookings in a designated primary calendar.

## Non-Goals (MVP)

- Multi-admin scheduling.
- Team scheduling.
- Round-robin scheduling.
- Group appointments.
- Approval workflows.
- Recurring event handling.
- Audit trails.
- Mobile applications.
- Scalability optimization.

---

# Users

## Admin

A person who owns one or more calendars and wants others to schedule appointments.

Examples:

- Consultant
- Recruiter
- Freelancer
- Small business owner

## Visitor

Anonymous external user who wants to schedule an appointment.

No account required.

---

# Phase 1 Requirements

## Feature 1: Admin Authentication

### Description

Admin can log into the system.

### MVP Scope

Simple email/password authentication.

### Acceptance Criteria

- Admin can sign up.
- Admin can sign in.
- Admin can sign out.

---

## Feature 2: Calendar Provider Connection

### Description

Admin can connect external calendars.

### Supported Providers

- Google Calendar
- Microsoft Outlook

### Acceptance Criteria

- Admin can connect Google account via OAuth.
- Admin can connect Outlook account via OAuth.
- Admin can connect multiple accounts from same provider.
- Admin can disconnect accounts.

---

## Feature 3: Primary Calendar Selection

### Description

Admin selects one calendar account as booking destination.

### Acceptance Criteria

- Only one primary calendar exists.
- Admin can change primary calendar.
- Primary calendar must be connected.

---

## Feature 4: Working Hours Configuration

### Description

Admin defines availability boundaries.

### Example

Monday–Friday

9:00 AM – 5:00 PM

### Acceptance Criteria

- Working hours configurable per weekday.
- Availability calculations respect working hours.

---

## Feature 5: Calendar Synchronization

### Description

System imports events from connected calendars.

### Rules

- All events block availability.
- Recurring events ignored in MVP.
- Events remain associated with source provider.

### Acceptance Criteria

- Events appear in unified calendar.
- Events from multiple providers merge correctly.

---

## Feature 6: Unified Calendar View

### Description

Admin sees one merged calendar.

### Acceptance Criteria

- Month view.
- Week view.
- Day view.
- Event source visible.
- Events from all providers displayed.

---

## Feature 7: Public Availability Page

### Description

Anonymous visitors can see available appointment slots.

### Availability Rules

Available Slot =

Working Hours

− Union of all Busy Events

### Acceptance Criteria

- Public URL available.
- Browser timezone used.
- Available slots displayed.
- Unavailable slots hidden.

---

# Phase 2 Requirements

## Feature 8: Appointment Booking

### Description

Visitor can book an appointment.

### Required Fields

- Name
- Email
- Phone
- Notes

### Acceptance Criteria

- User selects available slot.
- User submits booking information.
- Booking request stored.

---

## Feature 9: Appointment Creation

### Description

Booking creates an event in primary calendar.

### Event Data

- Title
- Visitor name
- Visitor email
- Phone
- Notes

### Acceptance Criteria

- Event created successfully.
- Event appears in unified calendar.

---

## Feature 10: Email Notifications

### Admin Notification

New appointment booked.

### User Notification

Booking confirmation.

### Acceptance Criteria

- Emails sent after booking succeeds.

---

## Feature 11: ICS Invite

### Description

User receives calendar invite.

### Acceptance Criteria

- ICS attachment generated.
- Included in confirmation email.

---

## Feature 12: Cancellation

### Description

User cancels appointment.

### Acceptance Criteria

- Unique cancellation link.
- Event removed from primary calendar.
- Confirmation email sent.

---

## Feature 13: Rescheduling

### Description

User changes appointment time.

### Acceptance Criteria

- Unique reschedule link.
- User selects new slot.
- Calendar event updated.

---

# Functional Requirements

## Availability Computation

Availability must be calculated as:

Working Hours − All Busy Events Across Connected Calendars

Example:

Working Hours: 09:00–17:00

Google Busy: 10:00–11:00

Outlook Busy: 13:00–14:00

Available:

- 09:00–10:00
- 11:00–13:00
- 14:00–17:00

Then split into 30-minute slots.

---

## Time Zone Handling

### Admin

Use browser timezone.

### Visitor

Use browser timezone.

### Storage

Store timestamps in UTC.

### Display

Convert to local browser timezone.

---

# Success Metrics

## Phase 1

- Admin can connect calendars.
- Unified calendar displays correctly.
- Availability matches connected calendars.
- Public page loads availability successfully.

## Phase 2

- Visitor books successfully.
- Event appears in primary calendar.
- Emails delivered.
- Cancellation works.
- Rescheduling works.

---

# Open Questions Before Design Doc

## 1. Booking Creation Strategy

### Option A (Recommended)

- System directly creates event in primary calendar via API.
- Booking instantly confirmed.

### Option B

- System emails admin an ICS invite.
- Admin manually accepts.

Recommendation: Option A.

---

## 2. Synchronization Strategy

### Option A

- Pull calendars every N minutes (e.g. every 5 minutes).

### Option B

- Use Google/Outlook webhook subscriptions.

Recommendation: Option A for MVP simplicity.

---

## 3. Public Scheduling URL

### Option A

- `/schedule`

### Option B (Recommended)

- `/{admin-slug}`

Recommendation: Option B because it naturally supports future multi-admin expansion.
