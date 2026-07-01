import { useEffect, useState, useCallback, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getAdminInfo, getAvailableSlots } from '../api/availability'
import { useBrowserTimezone } from '../hooks/useBrowserTimezone'
import type { AdminPublicInfo, TimeSlot } from '../types'

type SlotState = 'idle' | 'loading' | 'loaded' | 'error'

/** Formats a UTC ISO string as a time in the given IANA timezone. */
function formatSlotTime(isoUtc: string, tz: string): string {
  return new Intl.DateTimeFormat('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    timeZone: tz,
  }).format(new Date(isoUtc))
}

/** Returns an array of YYYY-MM-DD strings for the next 30 days in the given timezone. */
function nextThirtyDays(tz: string): string[] {
  const days: string[] = []
  const formatter = new Intl.DateTimeFormat('en-CA', { timeZone: tz }) // en-CA gives YYYY-MM-DD
  const now = new Date()
  for (let i = 0; i < 30; i++) {
    const d = new Date(now)
    d.setDate(now.getDate() + i)
    days.push(formatter.format(d))
  }
  return days
}

/** Returns a human-friendly label for a date string relative to today in the visitor's timezone. */
function dateLabel(dateStr: string): string {
  return new Intl.DateTimeFormat('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  }).format(new Date(dateStr + 'T12:00:00')) // use noon to avoid DST edge-cases
}

/** Public-facing availability page for a given admin slug (/s/:slug). */
export default function PublicSchedulePage() {
  const { slug } = useParams<{ slug: string }>()
  const navigate = useNavigate()
  const visitorTz = useBrowserTimezone()

  const [adminInfo, setAdminInfo] = useState<AdminPublicInfo | null>(null)
  const [adminNotFound, setAdminNotFound] = useState(false)
  const [adminLoadError, setAdminLoadError] = useState(false)
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const [slots, setSlots] = useState<TimeSlot[]>([])
  const [slotState, setSlotState] = useState<SlotState>('idle')

  // Computed fresh each render so midnight-crossover disables stale dates correctly
  const todayStr = new Intl.DateTimeFormat('en-CA', { timeZone: visitorTz }).format(new Date())

  const days = useMemo(() => nextThirtyDays(visitorTz), [visitorTz])

  useEffect(() => {
    if (!slug) return
    getAdminInfo(slug)
      .then(res => setAdminInfo(res.data))
      .catch(err => {
        if (err?.response?.status === 404) setAdminNotFound(true)
        else setAdminLoadError(true)
      })
  }, [slug])

  const fetchSlots = useCallback(
    (date: string) => {
      if (!slug) return
      setSlotState('loading')
      setSlots([])
      getAvailableSlots(slug, date)
        .then(res => {
          setSlots(res.data.slots)
          setSlotState('loaded')
        })
        .catch(() => setSlotState('error'))
    },
    [slug],
  )

  function handleDateSelect(date: string) {
    if (date < todayStr) return
    setSelectedDate(date)
    fetchSlots(date)
  }

  function handleSlotClick(slot: TimeSlot) {
    navigate(`/book/${slug}`, {
      state: { slotStart: slot.start, slotEnd: slot.end, adminName: adminInfo!.name },
    })
  }

  useEffect(() => {
    if (adminInfo) document.title = `Book with ${adminInfo.name}`
    return () => { document.title = 'Unified Calendar' }
  }, [adminInfo])

  if (adminLoadError) {
    return (
      <div style={styles.centered}>
        <p style={styles.errorText}>Something went wrong. Please try again.</p>
      </div>
    )
  }

  if (adminNotFound) {
    return (
      <div style={styles.centered}>
        <p style={styles.errorText}>This scheduling page doesn&apos;t exist.</p>
      </div>
    )
  }

  if (!adminInfo) {
    return (
      <div style={styles.centered}>
        <div style={styles.skeleton} aria-label="Loading" />
      </div>
    )
  }

  return (
    <div style={styles.page}>
      <h1 style={styles.heading}>{adminInfo.name}</h1>
      <p style={styles.subtitle}>Select a date and time to book an appointment.</p>

      {/* Date picker */}
      <section aria-label="Date picker">
        <h2 style={styles.sectionTitle}>Choose a date</h2>
        <div style={styles.dateGrid}>
          {days.map(day => {
            const isPast = day < todayStr
            return (
              <button
                key={day}
                onClick={() => handleDateSelect(day)}
                disabled={isPast}
                style={{
                  ...styles.dateBtn,
                  ...(selectedDate === day ? styles.dateBtnActive : {}),
                  ...(isPast ? styles.dateBtnDisabled : {}),
                }}
                aria-pressed={selectedDate === day}
              >
                {dateLabel(day)}
              </button>
            )
          })}
        </div>
      </section>

      {/* Slot list */}
      {selectedDate && (
        <section aria-label="Available time slots" style={styles.slotSection}>
          <h2 style={styles.sectionTitle}>Available times</h2>

          {slotState === 'loading' && (
            <div style={styles.skeletonList}>
              {[1, 2, 3].map(n => (
                <div key={n} style={styles.skeletonSlot} aria-hidden />
              ))}
            </div>
          )}

          {slotState === 'error' && (
            <div>
              <p style={styles.errorText}>Something went wrong. Please try again.</p>
              <button style={styles.retryBtn} onClick={() => fetchSlots(selectedDate)}>
                Retry
              </button>
            </div>
          )}

          {slotState === 'loaded' && slots.length === 0 && (
            <p style={styles.emptyText}>No available times on this date.</p>
          )}

          {slotState === 'loaded' && slots.length > 0 && (
            <div style={styles.slotGrid}>
              {slots.map(slot => (
                <button
                  key={slot.start}
                  onClick={() => handleSlotClick(slot)}
                  style={styles.slotBtn}
                >
                  {formatSlotTime(slot.start, visitorTz)}
                </button>
              ))}
            </div>
          )}
        </section>
      )}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: {
    maxWidth: 640,
    margin: '0 auto',
    padding: '2rem 1rem',
    fontFamily: 'system-ui, sans-serif',
  },
  centered: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '60vh',
    fontFamily: 'system-ui, sans-serif',
  },
  heading: {
    fontSize: '1.75rem',
    fontWeight: 700,
    margin: '0 0 0.5rem',
  },
  subtitle: {
    color: '#555',
    marginBottom: '2rem',
  },
  sectionTitle: {
    fontSize: '1rem',
    fontWeight: 600,
    marginBottom: '0.75rem',
  },
  dateGrid: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '0.5rem',
    marginBottom: '2rem',
  },
  dateBtn: {
    padding: '0.5rem 0.75rem',
    border: '1px solid #ccc',
    borderRadius: 6,
    background: '#fff',
    cursor: 'pointer',
    fontSize: '0.875rem',
  },
  dateBtnActive: {
    background: '#2563eb',
    color: '#fff',
    borderColor: '#2563eb',
  },
  dateBtnDisabled: {
    opacity: 0.4,
    cursor: 'not-allowed',
  },
  slotSection: {
    marginTop: '0.5rem',
  },
  slotGrid: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '0.5rem',
  },
  slotBtn: {
    padding: '0.5rem 1rem',
    border: '1px solid #2563eb',
    borderRadius: 6,
    background: '#fff',
    color: '#2563eb',
    cursor: 'pointer',
    fontSize: '0.875rem',
    fontWeight: 500,
  },
  skeletonList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
  },
  skeleton: {
    width: 200,
    height: 20,
    background: '#e5e7eb',
    borderRadius: 4,
    animation: 'pulse 1.5s ease-in-out infinite',
  },
  skeletonSlot: {
    width: 100,
    height: 36,
    background: '#e5e7eb',
    borderRadius: 6,
  },
  errorText: {
    color: '#dc2626',
  },
  emptyText: {
    color: '#6b7280',
  },
  retryBtn: {
    marginTop: '0.5rem',
    padding: '0.4rem 0.8rem',
    border: '1px solid #dc2626',
    borderRadius: 6,
    background: '#fff',
    color: '#dc2626',
    cursor: 'pointer',
    fontSize: '0.875rem',
  },
}
