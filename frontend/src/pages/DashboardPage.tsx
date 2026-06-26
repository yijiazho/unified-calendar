import type React from 'react'
import { useRef, useState, useEffect, useCallback } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import type { EventSourceFunc, EventClickArg, EventContentArg } from '@fullcalendar/core'
import { getEvents, getAccounts, triggerSync } from '../api/calendar'
import type { CalendarAccount } from '../types'

const PALETTE = ['#4285F4', '#0F9D58', '#9C27B0', '#FF9800', '#F44336']
const BOOKING_COLOR = '#00897B'

function colorForAccount(accountId: number): string {
  return PALETTE[accountId % PALETTE.length]
}

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

interface PopoverState {
  x: number
  y: number
  title: string
  start: string
  end: string
  email: string
  provider: string
  isBooking: boolean
}

function renderEventContent(arg: EventContentArg) {
  return (
    <div style={{ padding: '1px 4px', overflow: 'hidden', fontSize: '0.85em' }}>
      <span>{arg.event.title}</span>
      {arg.event.extendedProps.isBooking && (
        <span style={styles.bookingDot} title="Booking" />
      )}
    </div>
  )
}

/** Unified calendar view showing events from all connected providers. */
export default function DashboardPage() {
  const calendarRef = useRef<FullCalendar>(null)
  const syncTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [accounts, setAccounts] = useState<CalendarAccount[]>([])
  const [syncing, setSyncing] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [popover, setPopover] = useState<PopoverState | null>(null)

  useEffect(() => {
    getAccounts()
      .then(res => setAccounts(res.data))
      .catch(err => console.error('Failed to load calendar accounts', err))
  }, [])

  useEffect(() => () => {
    if (syncTimerRef.current) clearTimeout(syncTimerRef.current)
  }, [])

  const fetchEvents: EventSourceFunc = useCallback(async (fetchInfo, successCallback, failureCallback) => {
    try {
      const { data } = await getEvents(fetchInfo.startStr, fetchInfo.endStr)
      successCallback(
        data.map(event => ({
          id: String(event.id),
          title: event.title,
          start: event.start,
          end: event.end,
          backgroundColor: event.isBookingEvent ? BOOKING_COLOR : colorForAccount(event.calendarAccountId),
          borderColor: event.isBookingEvent ? BOOKING_COLOR : colorForAccount(event.calendarAccountId),
          extendedProps: {
            provider: event.provider,
            email: event.calendarEmail,
            isBooking: event.isBookingEvent,
          },
        }))
      )
    } catch {
      failureCallback(new Error('Failed to load events'))
    }
  }, [])

  const handleEventClick = useCallback((clickInfo: EventClickArg) => {
    const { title, start, end, extendedProps } = clickInfo.event
    const { clientX, clientY } = clickInfo.jsEvent
    setPopover({
      x: clientX,
      y: clientY,
      title,
      start: start?.toISOString() ?? '',
      end: end?.toISOString() ?? '',
      email: extendedProps.email as string,
      provider: extendedProps.provider as string,
      isBooking: extendedProps.isBooking as boolean,
    })
  }, [])

  const handleSyncNow = async () => {
    setSyncing(true)
    try {
      await triggerSync()
    } catch {
      // sync is best-effort; calendar will refetch regardless
    }
    syncTimerRef.current = setTimeout(() => {
      setSyncing(false)
      calendarRef.current?.getApi().refetchEvents()
    }, 3000)
  }

  return (
    <div style={styles.page} onClick={() => setPopover(null)}>
      <header style={styles.header}>
        <h2 style={styles.title}>Calendar</h2>
        <button
          style={{ ...styles.syncBtn, opacity: syncing ? 0.7 : 1 }}
          onClick={e => { e.stopPropagation(); handleSyncNow() }}
          disabled={syncing}
        >
          {syncing ? (
            <><span style={styles.btnSpinner} /> Syncing…</>
          ) : 'Sync Now'}
        </button>
      </header>

      <div style={styles.calendarWrap}>
        <FullCalendar
          ref={calendarRef}
          plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
          initialView="dayGridMonth"
          headerToolbar={{
            left: 'prev,next today',
            center: 'title',
            right: 'dayGridMonth,timeGridWeek,timeGridDay',
          }}
          events={fetchEvents}
          eventContent={renderEventContent}
          eventClick={handleEventClick}
          loading={setIsLoading}
          height="100vh"
        />
        {isLoading && <div style={styles.loadingBar} />}
      </div>

      {accounts.length > 0 && (
        <div style={styles.legend}>
          {accounts.map(acc => (
            <span key={acc.id} style={styles.legendItem}>
              <span
                style={{ ...styles.legendDot, background: colorForAccount(acc.id) }}
              />
              {acc.email}
            </span>
          ))}
          <span style={styles.legendItem}>
            <span style={{ ...styles.legendDot, background: BOOKING_COLOR }} />
            Bookings
          </span>
        </div>
      )}

      {popover && (
        <div
          style={{ ...styles.popover, left: popover.x, top: popover.y }}
          onClick={e => e.stopPropagation()}
        >
          <div style={styles.popoverTitle}>{popover.title}</div>
          {popover.isBooking && <span style={styles.bookingBadge}>Booking</span>}
          <div style={styles.popoverRow}>
            <strong>Start</strong> {formatLocalTime(popover.start)}
          </div>
          {popover.end && (
            <div style={styles.popoverRow}>
              <strong>End</strong> {formatLocalTime(popover.end)}
            </div>
          )}
          <div style={styles.popoverRow}>
            <strong>Calendar</strong> {popover.email}
          </div>
          <div style={styles.popoverRow}>
            <strong>Provider</strong> {providerLabel(popover.provider)}
          </div>
          <button style={styles.popoverClose} onClick={() => setPopover(null)}>✕</button>
        </div>
      )}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: {
    display: 'flex',
    flexDirection: 'column',
    flex: 1,
    width: '100%',
    boxSizing: 'border-box',
    position: 'relative',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '16px 24px',
    borderBottom: '1px solid var(--border)',
    flexShrink: 0,
  },
  title: {
    margin: 0,
    fontSize: 20,
    fontWeight: 600,
    color: 'var(--text-h)',
  },
  syncBtn: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    padding: '8px 16px',
    borderRadius: 6,
    border: '1px solid var(--border)',
    background: 'var(--accent)',
    color: '#fff',
    fontWeight: 500,
    cursor: 'pointer',
    fontSize: 14,
  },
  btnSpinner: {
    display: 'inline-block',
    width: 12,
    height: 12,
    border: '2px solid rgba(255,255,255,0.4)',
    borderTopColor: '#fff',
    borderRadius: '50%',
    animation: 'spin 0.7s linear infinite',
  },
  calendarWrap: {
    padding: '0 24px',
    position: 'relative',
  },
  loadingBar: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: 3,
    background: 'var(--accent)',
    opacity: 0.7,
    animation: 'pulse 1s infinite',
  },
  legend: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: 16,
    padding: '12px 24px',
    borderTop: '1px solid var(--border)',
    flexShrink: 0,
    fontSize: 13,
  },
  legendItem: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    color: 'var(--text)',
  },
  legendDot: {
    width: 12,
    height: 12,
    borderRadius: '50%',
    flexShrink: 0,
  },
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
  bookingDot: {
    display: 'inline-block',
    width: 6,
    height: 6,
    borderRadius: '50%',
    background: '#fff',
    marginLeft: 4,
    verticalAlign: 'middle',
    opacity: 0.8,
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
