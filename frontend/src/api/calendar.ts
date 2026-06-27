import client from './client'
import type { CalendarAccount, CalendarEvent } from '../types'

export const getAccounts = () =>
  client.get<CalendarAccount[]>('/calendar/accounts')

export const getEvents = (start: string, end: string) =>
  client.get<CalendarEvent[]>('/calendar/events', { params: { start, end } })

/** Returns the URL to initiate Google OAuth; caller is responsible for navigation. */
export const connectGoogle = (): string => '/api/calendar/google/connect'

/** Returns the URL to initiate Outlook OAuth; caller is responsible for navigation. */
export const connectOutlook = (): string => '/api/calendar/outlook/connect'

export const disconnectAccount = (id: number) =>
  client.delete(`/calendar/accounts/${id}`)

/** Sets the primary calendar used for booking event creation. */
export const setPrimary = (accountId: number) =>
  client.put('/calendar/primary', { accountId })

/** Triggers a full sync of all connected calendar accounts. */
export const triggerSync = () =>
  client.post('/calendar/sync')
