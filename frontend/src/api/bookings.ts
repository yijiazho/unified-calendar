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

export interface CancellationResponse {
  message: string
  slotStart: string
  slotEnd: string
}

export type CancellationErrorCode = 'ALREADY_CANCELLED' | 'ALREADY_RESCHEDULED'

export interface CancellationErrorResponse {
  error: string
  code?: CancellationErrorCode
}

export const createBooking = (data: CreateBookingRequest) =>
  publicClient.post<BookingConfirmation>('/bookings', data)

export const cancelBooking = (cancelToken: string) =>
  publicClient.post<CancellationResponse>(`/bookings/${cancelToken}/cancel`)

export const rescheduleBooking = (token: string, start: string, end: string) =>
  publicClient.post<Booking>(`/bookings/reschedule/${token}`, { start, end })
