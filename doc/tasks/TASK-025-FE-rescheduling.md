# TASK-025 · FE · Rescheduling UI

## Context

A standalone public page the visitor lands on from the reschedule link in their confirmation email. Lets them pick a new slot from the admin's availability and confirm the change. Reuses the slot-picker UI pattern from the public availability page (TASK-017).

Depends on: TASK-003 (scaffold), TASK-017 (availability slot-picker pattern), TASK-024 (backend rescheduling API).

---

## Instructions

### API calls (extend `src/api/bookings.ts`)

```ts
interface RescheduleRequest {
  newSlotStart: string
  newSlotEnd: string
}

interface RescheduleResponse {
  bookingId: number
  visitorName: string
  newSlotStart: string
  newSlotEnd: string
  cancelToken: string
  rescheduleToken: string
}

export const rescheduleBooking = (rescheduleToken: string, data: RescheduleRequest) =>
  client.post<RescheduleResponse>(`/bookings/${rescheduleToken}/reschedule`, data)
```

The reschedule page needs the admin's slug to fetch availability. The slug is not in the token — the backend doesn't expose it via a dedicated endpoint in MVP. Store the slug as a query parameter appended to the reschedule link in the confirmation email:

`/reschedule/{rescheduleToken}?slug={adminSlug}`

Update TASK-020 (email service) to append `?slug={admin.slug}` to the reschedule link.

### Page structure (`src/pages/ReschedulePage.tsx`)

Route: `/reschedule/:token`

**Step 1 — Load**

On mount:
- Extract `rescheduleToken` from URL params.
- Extract `slug` from `window.location.search`.
- Call `getAdminInfo(slug)` to verify the admin exists and get display name.
- If slug is missing or admin 404 → show error: "This reschedule link is invalid."

**Step 2 — Date and slot picker** (same component as TASK-017)

Extract the slot picker into a shared `<SlotPicker slug={slug} onSlotSelect={handleSlotSelect} />` component in `src/components/SlotPicker.tsx`. Both TASK-017 and this page use it.

`SlotPicker` props:
```ts
interface SlotPickerProps {
  slug: string
  onSlotSelect: (slot: TimeSlot) => void
  excludeSlot?: TimeSlot   // optionally highlight current slot as "current booking"
}
```

Display above the picker:
> "You are rescheduling your appointment with {adminName}. Select a new time below."

**Step 3 — Confirm**

After slot selection, show a confirmation summary before submitting:

```
"You are about to reschedule to:"
Thursday, March 16, 2024 at 10:00 AM – 10:30 AM (your timezone)

[Confirm New Time]  [Choose a different time]
```

On "Confirm New Time":
- POST `/bookings/{rescheduleToken}/reschedule` with `newSlotStart`, `newSlotEnd`.
- On 200: show success state.
- On 409 (slot taken): go back to step 2 with error: "That slot was just taken. Please choose another."
- On 409 (booking cancelled): show "This appointment has been cancelled and cannot be rescheduled."
- On 410 (past appointment): show "This appointment has already passed."
- On 404: show "This reschedule link is invalid."

**Success state**:

```
Green checkmark
Heading: "Appointment Rescheduled"
Date and time: {newSlotStart formatted in visitor timezone}
Body: "You will receive a confirmation email with your updated calendar invite."

Links:
  "Cancel this appointment" → /cancel/{cancelToken}
```

---

## Acceptance Criteria

- Visiting `/reschedule/{validToken}?slug={slug}` shows the admin name and slot picker.
- Selecting a slot and confirming calls the backend and shows the success state.
- The success state shows the new slot time in the visitor's browser timezone.
- A 409 "slot taken" response goes back to the slot picker with an error message.
- A 409 "already cancelled" response shows the appropriate message.
- A 410 response shows the "already passed" message.
- A 404 response shows the "invalid link" message.
- The `<SlotPicker>` component is shared with the public availability page (no duplication).
- The page loads without a session cookie.
- Missing `?slug` query parameter shows the "invalid link" error.
