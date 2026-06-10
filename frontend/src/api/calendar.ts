import client from './client'
import type { CalendarAccount, CalendarEvent } from '../types'

export const getAccounts = () => client.get<CalendarAccount[]>('/calendars/accounts')

export const getEvents = (start: string, end: string) =>
  client.get<CalendarEvent[]>('/calendars/events', { params: { start, end } })

export const deleteAccount = (id: number) => client.delete(`/calendars/accounts/${id}`)
