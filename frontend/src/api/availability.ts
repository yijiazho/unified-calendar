import client from './client'
import publicClient from './publicClient'
import type { WorkingHours, TimeSlot } from '../types'

export const getWorkingHours = () => client.get<WorkingHours[]>('/working-hours')

export const saveWorkingHours = (hours: WorkingHours[]) =>
  client.put<WorkingHours[]>('/working-hours', hours)

/** Public — fetches slots for a visitor; uses unauthenticated client to avoid /login redirect. */
export const getSlots = (slug: string, date: string) =>
  publicClient.get<TimeSlot[]>(`/availability/${slug}`, { params: { date } })
