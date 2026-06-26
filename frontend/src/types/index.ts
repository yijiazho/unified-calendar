export interface Admin {
  id: number
  email: string
  slug: string
  timezone: string
}

export interface CalendarAccount {
  id: number
  provider: 'GOOGLE' | 'OUTLOOK'
  email: string
  isPrimary: boolean
  connectedAt: string
}

export interface CalendarEvent {
  id: number
  title: string
  start: string
  end: string
  provider: 'GOOGLE' | 'OUTLOOK'
  calendarAccountId: number
  calendarEmail: string
  isBookingEvent: boolean
}

export interface WorkingHours {
  dayOfWeek: number
  startTime: string
  endTime: string
}

export interface TimeSlot {
  start: string
  end: string
}

export interface Booking {
  id: number
  visitorName: string
  visitorEmail: string
  status: string
  cancelToken: string
  rescheduleToken: string
}
