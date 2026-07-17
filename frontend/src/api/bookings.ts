import publicClient from './publicClient'
import type { Booking } from '../types'

export interface CreateBookingRequest {
  slug: string
  slotStart: string
  slotEnd: string
  visitorName: string
  visitorEmail: string
  visitorPhone?: string
  notes?: string
}

export interface BookingConfirmation {
  bookingId: number
  visitorName: string
  slotStart: string
  slotEnd: string
  adminName: string
  cancelToken: string
  rescheduleToken: string
}

export const createBooking = (data: CreateBookingRequest) =>
  publicClient.post<BookingConfirmation>('/bookings', data)

export const cancelBooking = (token: string) => publicClient.post(`/bookings/cancel/${token}`)

export const rescheduleBooking = (token: string, start: string, end: string) =>
  publicClient.post<Booking>(`/bookings/reschedule/${token}`, { start, end })
