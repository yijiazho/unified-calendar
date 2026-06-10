# TASK-017 · FE · Public Availability Page

## Context

The visitor-facing scheduling page. No login required. The visitor selects a date, sees available 30-minute slots in their local timezone, and proceeds to the booking form. This is the entry point for all bookings.

Depends on: TASK-003 (scaffold), TASK-016 (backend public API), TASK-019 (booking form — this page links to it).

---

## Instructions

### API calls (`src/api/availability.ts`)

```ts
import client from './client'

export const getAdminInfo = (slug: string) =>
  client.get<AdminPublicInfo>(`/s/${slug}`)

export const getAvailableSlots = (slug: string, date: string) =>
  client.get<AvailabilityResponse>('/availability', { params: { slug, date } })
```

Types:

```ts
interface AdminPublicInfo {
  slug: string
  name: string
  timezone: string
}

interface TimeSlot {
  start: string  // ISO-8601 UTC
  end: string
}

interface AvailabilityResponse {
  date: string
  adminTimezone: string
  slots: TimeSlot[]
}
```

### Page structure (`src/pages/PublicSchedulePage.tsx`)

Route: `/s/:slug`

**Step 1 — Load admin info**

On mount, call `getAdminInfo(slug)`. If 404, show:
> "This scheduling page doesn't exist."

If success, display:
- Admin's display name in the page heading
- A subtitle: "Select a date and time to book an appointment."

**Step 2 — Date picker**

A simple calendar date picker (or a set of `<button>` elements for the next 30 days in a grid). Do not show past dates.

On date selection, call `getAvailableSlots(slug, selectedDate)`.

Represent the selected date in the **visitor's browser timezone** when deciding what "today" and "tomorrow" mean for the display — but send it as an ISO date string (`YYYY-MM-DD`) derived from the visitor's local time.

```ts
const localDate = dayjs().tz(userTimezone).format('YYYY-MM-DD')
```

Use `dayjs` with the `timezone` plugin, or `Intl.DateTimeFormat` directly.

**Step 3 — Slot list**

For each slot returned by the API, display a button showing the time in the **visitor's browser timezone**:

```ts
// Convert UTC ISO string to visitor local time
const formatted = new Intl.DateTimeFormat('en-US', {
  hour: 'numeric',
  minute: '2-digit',
  timeZone: visitorTimezone,
}).format(new Date(slot.start))
```

Show "No available times on this date" when the slots array is empty.

Show a loading skeleton while fetching slots.

**Step 4 — Slot selection → navigate to booking form**

On clicking a slot, navigate to `/book/:slug` with the selected slot passed as URL state or query params:

```ts
navigate(`/book/${slug}`, {
  state: { slotStart: slot.start, slotEnd: slot.end, adminName: adminInfo.name }
})
```

### Page layout

- Keep the design clean and minimal (no admin nav).
- Show the admin's slug-based URL in the browser tab title: `"Book with {name}"`.
- The page is fully public — no session, no auth UI.

### Error states

| Condition | UI |
|---|---|
| Admin slug not found | "This scheduling page doesn't exist." |
| Date has no slots | "No available times on this date." |
| API error fetching slots | "Something went wrong. Please try again." with a retry button |

---

## Acceptance Criteria

- Visiting `/s/jane-smith` shows Jane's scheduling page if the slug exists.
- Visiting `/s/nonexistent` shows the "doesn't exist" message.
- Slot times display in the visitor's detected browser timezone (not the admin's timezone).
- Selecting a date with available slots shows the slot buttons.
- Selecting a date with no slots shows the "no available times" message.
- Clicking a slot navigates to the booking form page with the correct slot data.
- Past dates are not selectable.
- The page loads and renders correctly without any session cookie.
