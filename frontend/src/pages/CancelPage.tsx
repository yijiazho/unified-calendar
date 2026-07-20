import { isAxiosError } from 'axios'
import { useState } from 'react'
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { cancelBooking } from '../api/bookings'
import type { CancellationErrorResponse } from '../api/bookings'

type CancellationState =
  | 'confirm'
  | 'loading'
  | 'success'
  | 'already-cancelled'
  | 'already-rescheduled'
  | 'past'
  | 'not-found'
  | 'error'

interface AppointmentRouteState {
  slotStart?: string
  slotEnd?: string
}

interface ResultContent {
  heading: string
  body: string
}

const resultContent: Record<Exclude<CancellationState, 'confirm' | 'loading' | 'success'>, ResultContent> = {
  'already-cancelled': {
    heading: 'Already Cancelled',
    body: 'This appointment has already been cancelled.',
  },
  'already-rescheduled': {
    heading: 'Appointment Rescheduled',
    body: 'This appointment has been rescheduled. Use your newer confirmation email to manage it.',
  },
  past: {
    heading: 'Cannot Cancel',
    body: 'This appointment has already passed and cannot be cancelled online.',
  },
  'not-found': {
    heading: 'Link Not Found',
    body: 'This cancellation link is invalid or has expired.',
  },
  error: {
    heading: 'Something Went Wrong',
    body: 'We could not cancel your appointment. Please try again.',
  },
}

/** Formats optional appointment details supplied by an in-app navigation. */
function formatAppointmentTime(slotStart?: string, slotEnd?: string): string | null {
  if (!slotStart || !slotEnd) return null

  const start = new Date(slotStart)
  const end = new Date(slotEnd)
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return null

  const dateFormatter = new Intl.DateTimeFormat('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  })
  const timeFormatter = new Intl.DateTimeFormat('en-US', {
    hour: 'numeric',
    minute: '2-digit',
  })

  return `${dateFormatter.format(start)} at ${timeFormatter.format(start)} – ${timeFormatter.format(end)}`
}

/** Token-based cancellation page for visitors (/cancel/:token). */
export default function CancelPage() {
  const { token } = useParams<{ token: string }>()
  const location = useLocation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [state, setState] = useState<CancellationState>('confirm')

  const routeState = location.state as AppointmentRouteState | null
  const appointmentTime = formatAppointmentTime(
    routeState?.slotStart ?? searchParams.get('slotStart') ?? undefined,
    routeState?.slotEnd ?? searchParams.get('slotEnd') ?? undefined,
  )

  async function handleCancel() {
    if (!token) {
      setState('not-found')
      return
    }

    setState('loading')
    try {
      await cancelBooking(token)
      setState('success')
    } catch (error) {
      if (isAxiosError<CancellationErrorResponse>(error)) {
        if (error.response?.status === 409) {
          if (error.response.data.code === 'ALREADY_CANCELLED') {
            setState('already-cancelled')
            return
          }
          if (error.response.data.code === 'ALREADY_RESCHEDULED') {
            setState('already-rescheduled')
            return
          }
          setState('error')
          return
        }
        if (error.response?.status === 410) {
          setState('past')
          return
        }
        if (error.response?.status === 404) {
          setState('not-found')
          return
        }
      }
      setState('error')
    }
  }

  function handleKeepAppointment() {
    const historyState = window.history.state as { idx?: number } | null
    if (typeof historyState?.idx === 'number' && historyState.idx > 0) {
      navigate(-1)
      return
    }

    navigate('/', { replace: true })
  }

  let content
  if (state === 'confirm' || state === 'loading') {
    content = (
      <>
        <h1 style={styles.title}>Cancel Your Appointment</h1>
        <p style={styles.body}>Are you sure you want to cancel your appointment?</p>
        {appointmentTime && (
          <p style={styles.appointmentTime}>{appointmentTime}</p>
        )}
        <div style={styles.actions}>
          <button
            type="button"
            style={{ ...styles.button, ...styles.cancelButton }}
            onClick={handleCancel}
            disabled={state === 'loading'}
          >
            {state === 'loading' ? 'Cancelling…' : 'Yes, Cancel Appointment'}
          </button>
          <button
            type="button"
            style={{ ...styles.button, ...styles.keepButton }}
            onClick={handleKeepAppointment}
            disabled={state === 'loading'}
          >
            Keep My Appointment
          </button>
        </div>
        {state === 'loading' && <p role="status" style={styles.statusText}>Cancelling your appointment…</p>}
      </>
    )
  } else if (state === 'success') {
    content = (
      <div role="status">
        <div aria-hidden="true" style={styles.successIcon}>✓</div>
        <h1 style={styles.title}>Appointment Cancelled</h1>
        <p style={styles.body}>
          Your appointment has been successfully cancelled.
          <br />
          You will receive a confirmation email shortly.
        </p>
      </div>
    )
  } else {
    const result = resultContent[state]
    content = (
      <div role={state === 'error' ? 'alert' : 'status'}>
        <h1 style={styles.title}>{result.heading}</h1>
        <p style={styles.body}>{result.body}</p>
        {state === 'error' && (
          <button
            type="button"
            style={{ ...styles.button, ...styles.cancelButton, ...styles.retryButton }}
            onClick={handleCancel}
          >
            Try Again
          </button>
        )}
      </div>
    )
  }

  return (
    <main style={styles.page}>
      <section style={styles.card} aria-live="polite">
        {content}
      </section>
    </main>
  )
}

const styles = {
  page: {
    minHeight: '100vh',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
    boxSizing: 'border-box' as const,
  },
  card: {
    width: '100%',
    maxWidth: 560,
    padding: 32,
    border: '1px solid var(--border)',
    borderRadius: 12,
    background: 'var(--bg)',
    boxShadow: 'var(--shadow)',
    boxSizing: 'border-box' as const,
  },
  title: {
    margin: '0 0 12px',
    fontSize: 28,
    letterSpacing: '-0.4px',
  },
  body: {
    color: 'var(--text)',
    fontSize: 16,
    lineHeight: 1.55,
  },
  appointmentTime: {
    margin: '20px 0 0',
    padding: '14px 16px',
    borderRadius: 8,
    background: 'var(--social-bg)',
    color: 'var(--text-h)',
    fontWeight: 600,
  },
  actions: {
    display: 'flex',
    justifyContent: 'center',
    gap: 12,
    flexWrap: 'wrap' as const,
    marginTop: 28,
  },
  button: {
    minHeight: 44,
    padding: '10px 18px',
    borderRadius: 8,
    border: '1px solid transparent',
    font: 'inherit',
    fontSize: 15,
    fontWeight: 600,
    cursor: 'pointer',
  },
  cancelButton: {
    background: '#dc2626',
    color: '#fff',
  },
  keepButton: {
    borderColor: 'var(--border)',
    background: 'var(--bg)',
    color: 'var(--text-h)',
  },
  retryButton: {
    marginTop: 24,
  },
  statusText: {
    marginTop: 16,
    fontSize: 14,
    color: 'var(--text)',
  },
  successIcon: {
    width: 48,
    height: 48,
    margin: '0 auto 16px',
    borderRadius: '50%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: '#16a34a',
    color: '#fff',
    fontSize: 24,
    fontWeight: 700,
  },
}
