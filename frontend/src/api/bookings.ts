import publicClient from './publicClient'

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

export interface RescheduleRequest {
  newSlotStart: string
  newSlotEnd: string
}

export interface RescheduleResponse {
  bookingId: number
  visitorName: string
  newSlotStart: string
  newSlotEnd: string
  cancelToken: string
  rescheduleToken: string
}

export interface RescheduleErrorResponse {
  error: string
}

export const createBooking = (data: CreateBookingRequest) =>
  publicClient.post<BookingConfirmation>('/bookings', data)

export const cancelBooking = (cancelToken: string) =>
  publicClient.post<CancellationResponse>(`/bookings/${cancelToken}/cancel`)

export const rescheduleBooking = (rescheduleToken: string, data: RescheduleRequest) =>
  publicClient.post<RescheduleResponse>(`/bookings/${rescheduleToken}/reschedule`, data)
