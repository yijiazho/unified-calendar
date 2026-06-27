import type React from 'react'

export interface PopoverState {
  x: number
  y: number
  title: string
  start: string
  end: string
  email: string
  provider: string
  isBooking: boolean
}

const BOOKING_COLOR = '#00897B'

function providerLabel(provider: string): string {
  if (provider === 'GOOGLE') return 'Google Calendar'
  if (provider === 'OUTLOOK') return 'Microsoft Outlook'
  return provider
}

function formatLocalTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

interface Props {
  state: PopoverState
  onClose: () => void
}

/** Floating event detail card shown when a calendar event is clicked. */
export default function EventPopover({ state, onClose }: Props) {
  return (
    <div
      style={{ ...styles.popover, left: state.x, top: state.y }}
      onClick={e => e.stopPropagation()}
    >
      <div style={styles.popoverTitle}>{state.title}</div>
      {state.isBooking && <span style={styles.bookingBadge}>Booking</span>}
      <div style={styles.popoverRow}>
        <strong>Start</strong> {formatLocalTime(state.start)}
      </div>
      {state.end && (
        <div style={styles.popoverRow}>
          <strong>End</strong> {formatLocalTime(state.end)}
        </div>
      )}
      <div style={styles.popoverRow}>
        <strong>Calendar</strong> {state.email}
      </div>
      <div style={styles.popoverRow}>
        <strong>Provider</strong> {providerLabel(state.provider)}
      </div>
      <button style={styles.popoverClose} onClick={onClose}>✕</button>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  popover: {
    position: 'fixed',
    zIndex: 1000,
    background: 'var(--bg)',
    border: '1px solid var(--border)',
    borderRadius: 8,
    padding: '14px 16px',
    boxShadow: 'var(--shadow)',
    minWidth: 240,
    maxWidth: 320,
    fontSize: 14,
    lineHeight: 1.5,
  },
  popoverTitle: {
    fontWeight: 600,
    fontSize: 15,
    color: 'var(--text-h)',
    marginBottom: 6,
    paddingRight: 20,
  },
  popoverRow: {
    color: 'var(--text)',
    marginTop: 4,
    display: 'flex',
    gap: 6,
  },
  bookingBadge: {
    display: 'inline-block',
    background: BOOKING_COLOR,
    color: '#fff',
    fontSize: 11,
    fontWeight: 600,
    padding: '2px 6px',
    borderRadius: 4,
    marginBottom: 6,
  },
  popoverClose: {
    position: 'absolute',
    top: 10,
    right: 10,
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    color: 'var(--text)',
    fontSize: 14,
    padding: '2px 4px',
  },
}
