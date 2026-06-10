# TASK-014 · FE · Unified Calendar Dashboard

## Context

The admin's primary view after login. Displays all synced calendar events in a FullCalendar UI with month, week, and day views. Events are color-coded by source account so the admin can see at a glance which calendar each event came from.

Depends on: TASK-003 (scaffold — FullCalendar installed), TASK-006 (auth), TASK-013 (backend calendar events API).

---

## Instructions

### API calls (`src/api/calendar.ts` — extend existing file)

```ts
export const getEvents = (start: string, end: string) =>
  client.get<CalendarEventResponse[]>('/calendar/events', { params: { start, end } })

export const triggerSync = () =>
  client.post('/calendar/sync')
```

### Page structure (`src/pages/DashboardPage.tsx`)

Render a full-page FullCalendar component with:

```tsx
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
```

**FullCalendar config:**

```tsx
<FullCalendar
  plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
  initialView="dayGridMonth"
  headerToolbar={{
    left: 'prev,next today',
    center: 'title',
    right: 'dayGridMonth,timeGridWeek,timeGridDay',
  }}
  events={fetchEvents}   // callback form (see below)
  eventContent={renderEventContent}
  height="100vh"
/>
```

**Event fetching callback** — FullCalendar calls this when the visible date range changes:

```ts
const fetchEvents: EventSourceFunc = async (fetchInfo, successCallback, failureCallback) => {
  try {
    const { data } = await getEvents(fetchInfo.startStr, fetchInfo.endStr)
    successCallback(data.map(event => ({
      id: String(event.id),
      title: event.title,
      start: event.start,
      end: event.end,
      backgroundColor: colorForAccount(event.calendarAccountId),
      borderColor: colorForAccount(event.calendarAccountId),
      extendedProps: { provider: event.provider, email: event.calendarEmail, isBooking: event.isBookingEvent },
    })))
  } catch {
    failureCallback(new Error('Failed to load events'))
  }
}
```

**Color assignment**

```ts
const PALETTE = ['#4285F4', '#0F9D58', '#9C27B0', '#FF9800', '#F44336']

// Assign a color from the palette based on calendarAccountId (stable across re-renders)
function colorForAccount(accountId: number): string {
  return PALETTE[accountId % PALETTE.length]
}
```

Booking events (isBookingEvent = true) should always render in a distinct color, e.g. teal `#00897B`, regardless of which account they belong to.

**Event tooltip / popover**

On event click, show a small popover with:
- Title
- Start and end time (formatted in the browser's local timezone)
- Source calendar email
- Provider label ("Google Calendar" or "Microsoft Outlook")
- "Booking" badge if `isBooking: true`

Use a simple CSS-positioned popover or a lightweight library (e.g. Floating UI). Do not navigate away on click.

**Legend**

Below or above the calendar, render a small legend showing the color → account email mapping. Fetch the account list on mount from `GET /calendar/accounts`.

**Manual sync button**

Add a "Sync Now" button in the page header. On click:
1. Call `triggerSync()`.
2. Disable the button with a spinner for 3 seconds (approximate sync time).
3. Refetch events by calling `FullCalendar.refetchEvents()` via a calendar ref.

**Loading state**

While events are loading (initial render or view change), show FullCalendar's built-in loading indicator (set `loading` prop).

---

## Acceptance Criteria

- The calendar renders in month view on `/dashboard` after login.
- Events from both Google and Outlook appear, each in a distinct color.
- Switching to week view and day view shows events correctly.
- Navigating to a different month fetches events for that date range (not cached from the initial load).
- Clicking an event shows the popover with correct details.
- Clicking "Sync Now" triggers a backend sync and refreshes the calendar.
- Booking events (isBookingEvent = true) render in the designated booking color.
- The legend shows the color-to-account mapping accurately.
- The page redirects to `/login` if no session is present.
