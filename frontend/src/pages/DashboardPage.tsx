import type React from 'react'
import { useRef, useState, useEffect, useCallback } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import type { EventSourceFunc, EventClickArg, EventContentArg } from '@fullcalendar/core'
import { getEvents, getAccounts, triggerSync } from '../api/calendar'
import { getWorkingHours } from '../api/workingHours'
import type { CalendarAccount } from '../types'
import EventPopover from '../components/EventPopover'
import type { PopoverState } from '../components/EventPopover'

const PALETTE = ['#4285F4', '#0F9D58', '#9C27B0', '#FF9800', '#F44336']
const BOOKING_COLOR = '#00897B'

/** FullCalendar businessHours slot — daysOfWeek uses FC convention: 0=Sun, 1=Mon…6=Sat. */
interface BusinessHourSlot {
  daysOfWeek: number[]
  startTime: string
  endTime: string
}

function renderEventContent(arg: EventContentArg) {
  return (
    <div style={{ padding: '1px 4px', overflow: 'hidden', fontSize: '0.85em', height: '100%' }}>
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
  const waitingForRefetch = useRef(false)
  const [accounts, setAccounts] = useState<CalendarAccount[]>([])
  const [accountColorMap, setAccountColorMap] = useState<Record<number, string>>({})
  const [businessHours, setBusinessHours] = useState<BusinessHourSlot[]>([])
  const [syncing, setSyncing] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [popover, setPopover] = useState<PopoverState | null>(null)

  useEffect(() => {
    getAccounts()
      .then(res => {
        setAccounts(res.data)
        const map: Record<number, string> = {}
        res.data.forEach((acc, idx) => {
          map[acc.id] = PALETTE[idx % PALETTE.length]
        })
        setAccountColorMap(map)
      })
      .catch(err => console.error('Failed to load calendar accounts', err))
  }, [])

  useEffect(() => {
    getWorkingHours()
      .then(res => {
        // Our dayOfWeek: 0=Mon…6=Sun. FullCalendar: 0=Sun, 1=Mon…6=Sat.
        setBusinessHours(
          res.data.map(wh => ({
            daysOfWeek: [(wh.dayOfWeek + 1) % 7],
            startTime: wh.startTime,
            endTime: wh.endTime,
          }))
        )
      })
      .catch(err => console.error('Failed to load working hours', err))
  }, [])

  const fetchEvents: EventSourceFunc = useCallback(async (fetchInfo, successCallback, failureCallback) => {
    try {
      const { data } = await getEvents(fetchInfo.startStr, fetchInfo.endStr)
      successCallback(
        data.map(event => {
          const color = event.isBookingEvent
            ? BOOKING_COLOR
            : (accountColorMap[event.calendarAccountId] ?? PALETTE[0])
          return {
            id: String(event.id),
            title: event.title,
            start: event.start,
            end: event.end,
            backgroundColor: color,
            borderColor: color,
            extendedProps: {
              provider: event.provider,
              email: event.calendarEmail,
              isBooking: event.isBookingEvent,
            },
          }
        })
      )
    } catch {
      failureCallback(new Error('Failed to load events'))
    }
  }, [accountColorMap])

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

  const handleLoading = useCallback((loading: boolean) => {
    setIsLoading(loading)
    if (!loading && waitingForRefetch.current) {
      waitingForRefetch.current = false
      setSyncing(false)
    }
  }, [])

  const handleSyncNow = async () => {
    setSyncing(true)
    try {
      await triggerSync()
    } catch {
      // sync is best-effort; calendar will refetch regardless
    }
    if (!calendarRef.current) {
      setSyncing(false)
      return
    }
    waitingForRefetch.current = true
    calendarRef.current.getApi().refetchEvents()
  }

  return (
    <div style={styles.page} onClick={() => setPopover(null)}>
      <header style={styles.header}>
        <h2 style={styles.title}>Calendar</h2>
        <button
          style={{ ...styles.syncBtn, opacity: syncing ? 0.7 : 1 }}
          onClick={e => { e.stopPropagation(); void handleSyncNow() }}
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
          businessHours={businessHours.length > 0 ? businessHours : false}
          eventContent={renderEventContent}
          eventClick={handleEventClick}
          loading={handleLoading}
          height="calc(100vh - var(--nav-h))"
        />
        {isLoading && <div style={styles.loadingBar} />}
      </div>

      {(accounts.length > 0 || businessHours.length > 0) && (
        <div style={styles.legend}>
          {accounts.map(acc => (
            <span key={acc.id} style={styles.legendItem}>
              <span
                style={{ ...styles.legendDot, background: accountColorMap[acc.id] ?? PALETTE[0] }}
              />
              {acc.email}
            </span>
          ))}
          {accounts.length > 0 && (
            <span style={styles.legendItem}>
              <span style={{ ...styles.legendDot, background: BOOKING_COLOR }} />
              Bookings
            </span>
          )}
          {businessHours.length > 0 && (
            <span style={styles.legendItem}>
              <span style={styles.legendUnavailableDot} />
              Unavailable
            </span>
          )}
        </div>
      )}

      {popover && (
        <EventPopover state={popover} onClose={() => setPopover(null)} />
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
  legendUnavailableDot: {
    width: 12,
    height: 12,
    borderRadius: 3,
    flexShrink: 0,
    background: 'var(--fc-non-business-color, rgba(0,0,0,0.07))',
    border: '1px solid var(--border)',
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
}
