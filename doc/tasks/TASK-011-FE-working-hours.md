# TASK-011 · FE · Working Hours Configuration UI

## Context

Lets the admin define which weekdays and time ranges they are available for bookings. The form replaces all working hours in one PUT request (full replacement semantics).

Depends on: TASK-003 (scaffold), TASK-006 (auth), TASK-010 (backend working hours API).

---

## Instructions

### API calls (`src/api/workingHours.ts`)

```ts
import client from './client'
import { WorkingHours } from '../types'

export const getWorkingHours = () =>
  client.get<WorkingHours[]>('/working-hours')

export const saveWorkingHours = (hours: WorkingHours[]) =>
  client.put('/working-hours', hours)
```

### Page structure (`src/pages/WorkingHoursPage.tsx`)

Display a row for each day of the week (Monday through Sunday). Each row contains:

- A **checkbox** to enable/disable the day (unchecked = unavailable; checked = show time inputs).
- Day label: "Monday", "Tuesday", etc.
- **Start time** input (type=`time`, 30-minute increments or free-form).
- **End time** input (type=`time`).
- Inputs are hidden/disabled when the day checkbox is unchecked.

**Load state**

On mount, `GET /working-hours` and populate the form:
- Days present in the response → checkbox checked, times filled.
- Days absent → checkbox unchecked, times hidden.

**Default values for newly-checked days**: pre-fill 09:00–17:00.

**Save button**

On submit:
1. Collect all checked days as `WorkingHours[]` (dayOfWeek, startTime, endTime).
2. Client-side validate: for each enabled day, `startTime < endTime` (compare HH:MM strings lexicographically).
3. `PUT /working-hours` with the array.
4. Show success toast on 200; show inline error on 400 with the error message from the backend.

**Unsaved changes warning**

Track dirty state. If the admin navigates away with unsaved changes, show a browser `beforeunload` prompt or a custom dialog: "You have unsaved changes. Leave anyway?"

### UX details

- The day order is Monday → Sunday (ISO week order).
- Show the current saved state label: "Changes saved" / "Unsaved changes" as a subtle indicator below the save button.
- Disable the save button while the request is in flight.

### Types (already in `src/types/index.ts`)

```ts
interface WorkingHours {
  dayOfWeek: number  // 0=Monday … 6=Sunday
  startTime: string  // "HH:MM"
  endTime: string
}
```

---

## Acceptance Criteria

- On page load, the form reflects the saved working hours from the backend.
- Checking a day and saving adds it to the backend; unchecking and saving removes it.
- Saving Monday–Friday 09:00–17:00 results in 5 rows in the backend (GET returns 5 items).
- Attempting to set `startTime >= endTime` shows a validation error and does not submit.
- Unchecking all days and saving results in 0 configured hours (GET returns `[]`).
- Navigating away with unsaved changes prompts the user.
- The page is protected (requires session).
