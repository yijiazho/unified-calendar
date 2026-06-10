import publicClient from './publicClient'
import type { Booking } from '../types'

export const createBooking = (slug: string, data: {
  visitorName: string
  visitorEmail: string
  start: string
  end: string
}) => publicClient.post<Booking>(`/bookings/${slug}`, data)

export const cancelBooking = (token: string) => publicClient.post(`/bookings/cancel/${token}`)

export const rescheduleBooking = (token: string, start: string, end: string) =>
  publicClient.post<Booking>(`/bookings/reschedule/${token}`, { start, end })
