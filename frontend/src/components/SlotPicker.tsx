import { useCallback, useMemo, useRef, useState } from 'react'
import { getAvailableSlots } from '../api/availability'
import { useBrowserTimezone } from '../hooks/useBrowserTimezone'
import type { TimeSlot } from '../types'

export interface SlotPickerProps {
  slug: string
  onSlotSelect: (slot: TimeSlot) => void
  excludeSlot?: TimeSlot
}

type SlotState = 'idle' | 'loading' | 'loaded' | 'error'

function nextThirtyDays(timezone: string): string[] {
  const formatter = new Intl.DateTimeFormat('en-CA', { timeZone: timezone })
  const now = new Date()

  return Array.from({ length: 30 }, (_, offset) => {
    const date = new Date(now)
    date.setDate(now.getDate() + offset)
    return formatter.format(date)
  })
}

function dateLabel(date: string): string {
  return new Intl.DateTimeFormat('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  }).format(new Date(`${date}T12:00:00`))
}

function formatSlotTime(isoUtc: string, timezone: string): string {
  return new Intl.DateTimeFormat('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    timeZone: timezone,
  }).format(new Date(isoUtc))
}

/** Shared public date and availability picker. */
export default function SlotPicker({ slug, onSlotSelect, excludeSlot }: SlotPickerProps) {
  const visitorTimezone = useBrowserTimezone()
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const [slots, setSlots] = useState<TimeSlot[]>([])
  const [slotState, setSlotState] = useState<SlotState>('idle')
  const requestSequence = useRef(0)
  const days = useMemo(() => nextThirtyDays(visitorTimezone), [visitorTimezone])
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: visitorTimezone }).format(new Date())

  const fetchSlots = useCallback(async (date: string) => {
    const requestId = ++requestSequence.current
    setSlotState('loading')
    setSlots([])
    try {
      const response = await getAvailableSlots(slug, date)
      if (requestId !== requestSequence.current) return
      setSlots(response.data.slots)
      setSlotState('loaded')
    } catch {
      if (requestId !== requestSequence.current) return
      setSlotState('error')
    }
  }, [slug])

  function handleDateSelect(date: string) {
    if (date < today) return
    setSelectedDate(date)
    void fetchSlots(date)
  }

  return (
    <div>
      <section aria-label="Date picker">
        <h2 style={styles.sectionTitle}>Choose a date</h2>
        <div style={styles.dateGrid}>
          {days.map(day => {
            const isPast = day < today
            return (
              <button
                key={day}
                type="button"
                onClick={() => handleDateSelect(day)}
                disabled={isPast}
                style={{
                  ...styles.dateButton,
                  ...(selectedDate === day ? styles.dateButtonActive : {}),
                  ...(isPast ? styles.disabled : {}),
                }}
                aria-pressed={selectedDate === day}
              >
                {dateLabel(day)}
              </button>
            )
          })}
        </div>
      </section>

      {selectedDate && (
        <section aria-label="Available time slots" style={styles.slotSection}>
          <h2 style={styles.sectionTitle}>Available times</h2>

          {slotState === 'loading' && (
            <div style={styles.skeletonList} aria-label="Loading available times">
              {[1, 2, 3].map(item => <div key={item} style={styles.skeletonSlot} />)}
            </div>
          )}

          {slotState === 'error' && (
            <div role="alert">
              <p style={styles.errorText}>Something went wrong. Please try again.</p>
              <button type="button" style={styles.retryButton} onClick={() => void fetchSlots(selectedDate)}>
                Retry
              </button>
            </div>
          )}

          {slotState === 'loaded' && slots.length === 0 && (
            <p style={styles.emptyText}>No available times on this date.</p>
          )}

          {slotState === 'loaded' && slots.length > 0 && (
            <div style={styles.slotGrid}>
              {slots.map(slot => {
                const isCurrent = excludeSlot?.start === slot.start && excludeSlot.end === slot.end
                return (
                  <button
                    key={slot.start}
                    type="button"
                    onClick={() => onSlotSelect(slot)}
                    style={{ ...styles.slotButton, ...(isCurrent ? styles.currentSlot : {}) }}
                    title={isCurrent ? 'Current booking' : undefined}
                  >
                    {formatSlotTime(slot.start, visitorTimezone)}
                    {isCurrent && <span style={styles.currentLabel}>Current booking</span>}
                  </button>
                )
              })}
            </div>
          )}
        </section>
      )}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  sectionTitle: { fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' },
  dateGrid: { display: 'flex', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '2rem' },
  dateButton: { padding: '0.5rem 0.75rem', border: '1px solid var(--border)', borderRadius: 6, background: 'var(--bg)', color: 'var(--text-h)', cursor: 'pointer', fontSize: '0.875rem' },
  dateButtonActive: { background: '#2563eb', color: '#fff', borderColor: '#2563eb' },
  disabled: { opacity: 0.4, cursor: 'not-allowed' },
  slotSection: { marginTop: '0.5rem' },
  slotGrid: { display: 'flex', flexWrap: 'wrap', gap: '0.5rem' },
  slotButton: { padding: '0.5rem 1rem', border: '1px solid #2563eb', borderRadius: 6, background: 'var(--bg)', color: '#2563eb', cursor: 'pointer', fontSize: '0.875rem', fontWeight: 500 },
  currentSlot: { borderStyle: 'dashed' },
  currentLabel: { display: 'block', marginTop: 2, fontSize: '0.7rem' },
  skeletonList: { display: 'flex', flexDirection: 'column', gap: '0.5rem' },
  skeletonSlot: { width: 100, height: 36, background: 'var(--border)', borderRadius: 6 },
  errorText: { color: '#dc2626' },
  emptyText: { color: 'var(--text)' },
  retryButton: { marginTop: '0.5rem', padding: '0.4rem 0.8rem', border: '1px solid #dc2626', borderRadius: 6, background: 'var(--bg)', color: '#dc2626', cursor: 'pointer', fontSize: '0.875rem' },
}
