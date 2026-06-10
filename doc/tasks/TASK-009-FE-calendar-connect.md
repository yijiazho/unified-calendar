# TASK-009 · FE · Calendar Connect UI

## Context

Lets the admin connect and manage Google and Outlook calendar accounts. Initiates OAuth by redirecting to the backend, which then redirects to the provider. On return, the frontend reflects the updated account list.

Depends on: TASK-003 (scaffold), TASK-006 (auth/protected routes), TASK-007 (Google OAuth BE), TASK-008 (Outlook OAuth BE, includes DELETE and PUT /calendar/primary).

---

## Instructions

### API calls (`src/api/calendar.ts`)

```ts
import client from './client'
import { CalendarAccount } from '../types'

export const getAccounts = () =>
  client.get<CalendarAccount[]>('/calendar/accounts')

export const connectGoogle = () => {
  // Redirect the browser to the backend OAuth initiation endpoint.
  // The backend will redirect to Google, and Google will redirect back to the backend,
  // which will redirect the browser to /settings/calendars on success.
  window.location.href = '/api/calendar/google/connect'
}

export const connectOutlook = () => {
  window.location.href = '/api/calendar/outlook/connect'
}

export const disconnectAccount = (id: number) =>
  client.delete(`/calendar/accounts/${id}`)

export const setPrimary = (accountId: number) =>
  client.put('/calendar/primary', { accountId })
```

### Page structure (`src/pages/CalendarConnectPage.tsx`)

Layout: sidebar nav (shared with other settings pages) + main content area.

**Connected accounts list**

For each account, show a card with:
- Provider icon (Google or Outlook logo/icon)
- Account email
- "Primary" badge (if `isPrimary: true`)
- Last synced relative time (e.g. "Synced 3 minutes ago")
- "Set as Primary" button (disabled if already primary)
- "Disconnect" button

On clicking "Disconnect": show a confirmation dialog before calling `disconnectAccount()`. Re-fetch accounts on success.

On clicking "Set as Primary": call `setPrimary()` then re-fetch accounts.

**Connect new account section**

Two buttons:
- "Connect Google Calendar" → calls `connectGoogle()`
- "Connect Microsoft Outlook" → calls `connectOutlook()`

**OAuth return handling**

On mount, check `window.location.search` for `?error=` parameters set by the backend on OAuth failure. If present, show an error banner (e.g. "Failed to connect Google Calendar. Please try again.") and remove the query string from the URL.

```ts
useEffect(() => {
  const params = new URLSearchParams(window.location.search)
  const error = params.get('error')
  if (error) {
    setErrorMessage('Failed to connect calendar. Please try again.')
    window.history.replaceState({}, '', window.location.pathname)
  }
}, [])
```

### Primary calendar constraint

Enforce in the UI: at least one account must remain connected if any bookings exist (note: MVP can skip this enforcement — just show the disconnect button without the guard).

Show a tooltip on the "Set as Primary" button: "Bookings will be created in this calendar."

### Empty state

If no accounts are connected, show:
> "No calendars connected yet. Connect a Google or Outlook account to get started."

---

## Acceptance Criteria

- The page lists all connected accounts with correct provider labels and primary badge.
- Clicking "Connect Google Calendar" redirects to Google's OAuth consent screen.
- After completing OAuth, the new account appears in the list without requiring a manual page refresh.
- Clicking "Set as Primary" updates the badge immediately (optimistic update or re-fetch).
- Clicking "Disconnect" shows a confirmation dialog before deleting.
- After disconnecting, the account is removed from the list.
- OAuth failure displays the error banner and clears the query parameter from the URL.
- The page is inaccessible without a session (redirects to `/login`).
