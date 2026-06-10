# TASK-019 · FE · Booking Form UI

## Context

The visitor fills in their details after selecting a slot on the public availability page. Submits to the booking API and shows a confirmation page on success. Handles the double-booking conflict case gracefully.

Depends on: TASK-003 (scaffold), TASK-017 (public page — navigates here with slot data), TASK-018 (backend booking API).

---

## Instructions

### API calls (`src/api/bookings.ts`)

```ts
import client from './client'

interface CreateBookingRequest {
  slug: string
  slotStart: string
  slotEnd: string
  visitorName: string
  visitorEmail: string
  visitorPhone?: string
  notes?: string
}

interface BookingConfirmation {
  bookingId: number
  visitorName: string
  slotStart: string
  slotEnd: string
  adminName: string
  cancelToken: string
  rescheduleToken: string
}

export const createBooking = (data: CreateBookingRequest) =>
  client.post<BookingConfirmation>('/bookings', data)
```

### Booking form page (`src/pages/BookingFormPage.tsx`)

Route: `/book/:slug`

**Receive slot data** from the navigation state (set by TASK-017):

```ts
const location = useLocation()
const { slotStart, slotEnd, adminName } = location.state ?? {}
```

If `slotStart` is not present in state (e.g. user navigated directly), redirect back to `/s/:slug`.

**Display selected slot** at the top of the form (non-editable), formatted in the visitor's browser timezone:

```
"Thursday, March 15, 2024 at 2:00 PM – 2:30 PM (Eastern Time)"
```

**Form fields** (use `react-hook-form`):

| Field | Type | Validation |
|---|---|---|
| Name | text | Required, max 200 chars |
| Email | email | Required, valid format |
| Phone | tel | Optional, max 50 chars |
| Notes | textarea | Optional, max 2000 chars, 4 rows |

**Submit button**: "Confirm Booking". Show a spinner while the request is in flight. Disable the button to prevent double-submission.

**On success (201)**: navigate to `/booking/confirm` with the `BookingConfirmation` data in route state.

**On 409 (slot taken)**: do not navigate. Show an inline alert:
> "Sorry, this time slot was just taken. Please go back and choose another time."
Include a "Choose another time" link back to `/s/:slug`.

**On other errors**: show a generic `"Something went wrong. Please try again."` alert.

### Booking confirmation page (`src/pages/BookingConfirmPage.tsx`)

Route: `/booking/confirm`

Receive `BookingConfirmation` from route state. If state is missing, redirect to `/`.

Display:
- Green checkmark icon / success heading: "Appointment Confirmed"
- Date and time (formatted in visitor's browser timezone)
- Visitor name
- Two links:
  - "Cancel this appointment" → `/cancel/{cancelToken}`
  - "Reschedule" → `/reschedule/{rescheduleToken}`
- Note: "A confirmation email with a calendar invite has been sent to {visitorEmail}."

This page has no backend call — all data comes from the booking response stored in route state.

---

## Acceptance Criteria

- Navigating to `/book/:slug` without slot state in the route redirects back to `/s/:slug`.
- The selected slot is displayed at the top of the form in the visitor's local timezone.
- Submitting with all required fields and a valid slot returns the confirmation page.
- The confirmation page shows the correct date, time, and visitor name.
- The cancel and reschedule links contain the correct tokens.
- Submitting with a missing required field shows a field-level validation error (no network call).
- A 409 response shows the "slot taken" message and a link back to the scheduler.
- The submit button is disabled while the request is in flight (prevents double submission).
- The page loads without authentication.
