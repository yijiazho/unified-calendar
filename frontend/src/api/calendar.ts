import client from './client'
import type { CalendarAccount, CalendarEvent } from '../types'

export const getAccounts = () =>
  client.get<CalendarAccount[]>('/calendar/accounts')

export const getEvents = (start: string, end: string) =>
  client.get<CalendarEvent[]>('/calendar/events', { params: { start, end } })

/** Redirects the browser to initiate Google OAuth; backend handles the full redirect chain. */
export const connectGoogle = () => {
  window.location.href = '/api/calendar/google/connect'
}

/** Redirects the browser to initiate Outlook OAuth; backend handles the full redirect chain. */
export const connectOutlook = () => {
  window.location.href = '/api/calendar/outlook/connect'
}

export const disconnectAccount = (id: number) =>
  client.delete(`/calendar/accounts/${id}`)

/** Sets the primary calendar used for booking event creation. */
export const setPrimary = (accountId: number) =>
  client.put('/calendar/primary', { accountId })

/** Triggers a full sync of all connected calendar accounts. */
export const triggerSync = () =>
  client.post('/calendar/sync')
