import { Link, Navigate, useLocation } from 'react-router-dom'
import type { BookingConfirmation } from '../api/bookings'
import { useBrowserTimezone } from '../hooks/useBrowserTimezone'

interface BookingConfirmState extends BookingConfirmation {
  visitorEmail: string
}

/** Formats the booked slot in the visitor's browser timezone for the confirmation summary. */
function formatSlotSummary(startUtc: string, endUtc: string, timezone: string): string {
  const start = new Date(startUtc)
  const end = new Date(endUtc)
  const datePart = new Intl.DateTimeFormat('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    timeZone: timezone,
  }).format(start)

  const timeFormatter = new Intl.DateTimeFormat('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    timeZone: timezone,
  })

  return `${datePart} at ${timeFormatter.format(start)} – ${timeFormatter.format(end)}`
}

/** Confirmation page shown after a booking is successfully created. */
export default function BookingConfirmPage() {
  const location = useLocation()
  const visitorTimezone = useBrowserTimezone()
  const state = location.state as BookingConfirmState | null

  if (!state) {
    return <Navigate to="/" replace />
  }

  return (
    <div style={styles.page}>
      <div style={styles.card}>
        <div aria-hidden style={styles.checkIcon}>
          ✓
        </div>
        <h1 style={styles.title}>Appointment Confirmed</h1>

        <p style={styles.summaryText}>
          {formatSlotSummary(state.slotStart, state.slotEnd, visitorTimezone)}
        </p>

        <p style={styles.metaText}>
          Visitor: <strong>{state.visitorName}</strong>
        </p>

        <div style={styles.linkGroup}>
          <Link to={`/cancel/${state.cancelToken}`}>Cancel this appointment</Link>
          <Link to={`/reschedule/${state.rescheduleToken}`}>Reschedule</Link>
        </div>

        <p style={styles.noteText}>
          A confirmation email with a calendar invite has been sent to {state.visitorEmail}.
        </p>
      </div>
    </div>
  )
}

const styles = {
  page: {
    minHeight: '100vh',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    padding: '24px',
    boxSizing: 'border-box' as const,
  },
  card: {
    width: '100%',
    maxWidth: 560,
    border: '1px solid var(--border)',
    borderRadius: 12,
    background: 'var(--bg)',
    boxShadow: 'var(--shadow)',
    padding: '32px',
  },
  checkIcon: {
    width: 42,
    height: 42,
    borderRadius: '50%',
    background: '#16a34a',
    color: '#fff',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontWeight: 700,
    fontSize: 20,
    marginBottom: 12,
  },
  title: {
    margin: '0 0 10px',
    fontSize: 28,
    letterSpacing: '-0.4px',
  },
  summaryText: {
    margin: '0 0 12px',
    color: 'var(--text-h)',
    fontSize: 16,
  },
  metaText: {
    margin: '0 0 16px',
    color: 'var(--text)',
    fontSize: 15,
  },
  linkGroup: {
    display: 'flex',
    gap: 16,
    marginBottom: 14,
    flexWrap: 'wrap' as const,
  },
  noteText: {
    margin: 0,
    color: 'var(--text)',
    fontSize: 14,
  },
}
