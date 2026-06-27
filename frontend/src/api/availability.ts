import publicClient from './publicClient'
import type { TimeSlot } from '../types'

/** Public — fetches slots for a visitor; uses unauthenticated client to avoid /login redirect. */
export const getSlots = (slug: string, date: string) =>
  publicClient.get<TimeSlot[]>(`/availability/${slug}`, { params: { date } })
