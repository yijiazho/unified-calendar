import publicClient from './publicClient'
import type { AdminPublicInfo, AvailabilityResponse } from '../types'

/** Fetches the public admin profile for the scheduling page; 404 if slug does not exist. */
export const getAdminInfo = (slug: string) =>
  publicClient.get<AdminPublicInfo>(`/s/${slug}`)

/** Fetches available 30-minute slots for the given slug and date (YYYY-MM-DD). */
export const getAvailableSlots = (slug: string, date: string) =>
  publicClient.get<AvailabilityResponse>('/availability', { params: { slug, date } })
