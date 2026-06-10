# TASK-023 · FE · Cancellation UI

## Context

A standalone public page that the visitor lands on when they click the cancel link from their confirmation email. Confirms the cancellation and calls the backend cancel endpoint. No login required.

Depends on: TASK-003 (scaffold), TASK-022 (backend cancellation API).

---

## Instructions

### API calls (extend `src/api/bookings.ts`)

```ts
interface CancellationResponse {
  message: string
  slotStart: string
  slotEnd: string
}

export const cancelBooking = (cancelToken: string) =>
  client.post<CancellationResponse>(`/bookings/${cancelToken}/cancel`)
```

### Page structure (`src/pages/CancelPage.tsx`)

Route: `/cancel/:token`

**States the page can be in**:

1. **Confirm state** (initial) — ask the visitor to confirm before cancelling.
2. **Loading state** — request in flight.
3. **Success state** — cancellation confirmed.
4. **Already cancelled (409)** — show informational message.
5. **Past appointment (410)** — explain it cannot be cancelled.
6. **Not found (404)** — token is invalid.
7. **Error state** — generic error with retry.

**Confirm state UI**:

```
Heading: "Cancel Your Appointment"
Body: "Are you sure you want to cancel your appointment?"

[Show the appointment time if available — load it from route state or URL param set
 by the previous page. If not available, skip the time display.]

Buttons:
  "Yes, Cancel Appointment" (primary/destructive)
  "Keep My Appointment" (secondary) → navigates back or to the schedule page
```

**Success state UI**:

```
Green checkmark icon
Heading: "Appointment Cancelled"
Body: "Your appointment has been successfully cancelled.
       You will receive a confirmation email shortly."
```

**Already cancelled (409)**:

```
Heading: "Already Cancelled"
Body: "This appointment has already been cancelled."
```

**Past appointment (410)**:

```
Heading: "Cannot Cancel"
Body: "This appointment has already passed and cannot be cancelled online."
```

**Not found (404)**:

```
Heading: "Link Not Found"
Body: "This cancellation link is invalid or has expired."
```

### Do not auto-cancel on page load

Only trigger the cancel API call when the visitor explicitly clicks "Yes, Cancel Appointment". This prevents accidental cancellation from email link prefetching.

### Deep link considerations

The cancel link in the email is `/cancel/{token}`. The visitor may arrive directly at this URL (not via navigation state). The page must work standalone with only the token from the URL params.

---

## Acceptance Criteria

- Visiting `/cancel/{validToken}` shows the confirmation prompt (does not auto-cancel).
- Clicking "Yes, Cancel Appointment" calls the backend and shows the success state.
- Clicking "Keep My Appointment" navigates away without cancelling.
- Visiting `/cancel/{alreadyCancelledToken}` after cancellation shows "Already Cancelled" (on confirm click, or proactively on load — handle the 409 from the POST).
- Visiting `/cancel/{invalidToken}` shows "Link Not Found" after clicking confirm.
- Visiting `/cancel/{pastAppointmentToken}` shows "Cannot Cancel" after clicking confirm.
- The page loads without a session cookie.
- No network call is made until the visitor clicks "Yes, Cancel Appointment".
