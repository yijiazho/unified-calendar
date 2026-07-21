import { isAxiosError } from 'axios'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { getAdminInfo } from '../api/availability'
import { rescheduleBooking } from '../api/bookings'
import type { RescheduleErrorResponse, RescheduleResponse } from '../api/bookings'
import SlotPicker from '../components/SlotPicker'
import { useBrowserTimezone } from '../hooks/useBrowserTimezone'
import type { TimeSlot } from '../types'

type PageState = 'loading' | 'select' | 'confirm' | 'submitting' | 'success' | 'cancelled' | 'already-rescheduled' | 'past' | 'invalid' | 'error'

function formatSlot(slot: TimeSlot, timezone: string): string {
  const start = new Date(slot.start)
  const end = new Date(slot.end)
  const date = new Intl.DateTimeFormat('en-US', {
    weekday: 'long', month: 'long', day: 'numeric', year: 'numeric', timeZone: timezone,
  }).format(start)
  const timeFormatter = new Intl.DateTimeFormat('en-US', {
    hour: 'numeric', minute: '2-digit', timeZone: timezone,
  })
  return `${date} at ${timeFormatter.format(start)} – ${timeFormatter.format(end)}`
}

/** Token-based rescheduling page for visitors (/reschedule/:token). */
export default function ReschedulePage() {
  const { token } = useParams<{ token: string }>()
  const [searchParams] = useSearchParams()
  const slug = searchParams.get('slug')
  const timezone = useBrowserTimezone()
  const [state, setState] = useState<PageState>('loading')
  const [adminName, setAdminName] = useState('')
  const [selectedSlot, setSelectedSlot] = useState<TimeSlot | null>(null)
  const [result, setResult] = useState<RescheduleResponse | null>(null)
  const [pickerError, setPickerError] = useState<string | null>(null)
  const submissionInFlight = useRef(false)
  const pageState: PageState = !slug || !token ? 'invalid' : state

  useEffect(() => {
    if (!slug || !token) return

    let active = true
    getAdminInfo(slug)
      .then(response => {
        if (!active) return
        setAdminName(response.data.name)
        setState('select')
      })
      .catch(error => {
        if (!active) return
        setState(error?.response?.status === 404 ? 'invalid' : 'error')
      })
    return () => { active = false }
  }, [slug, token])

  useEffect(() => {
    document.title = pageState === 'success' ? 'Appointment Rescheduled' : 'Reschedule Appointment'
    return () => { document.title = 'Unified Calendar' }
  }, [pageState])

  function handleSlotSelect(slot: TimeSlot) {
    setSelectedSlot(slot)
    setPickerError(null)
    setState('confirm')
  }

  async function handleConfirm() {
    if (!token || !selectedSlot || submissionInFlight.current) return
    submissionInFlight.current = true
    setState('submitting')
    try {
      const response = await rescheduleBooking(token, {
        newSlotStart: selectedSlot.start,
        newSlotEnd: selectedSlot.end,
      })
      setResult(response.data)
      setState('success')
    } catch (error) {
      if (isAxiosError<RescheduleErrorResponse>(error)) {
        const status = error.response?.status
        const errorMessage = (error.response?.data?.error ?? '').toLowerCase()
        if (status === 404) setState('invalid')
        else if (status === 410) setState('past')
        else if (status === 409 && errorMessage.includes('cancel')) setState('cancelled')
        else if (status === 409 && errorMessage.includes('rescheduled')) setState('already-rescheduled')
        else if (status === 409) {
          setSelectedSlot(null)
          setPickerError('That slot was just taken. Please choose another.')
          setState('select')
        } else setState('error')
      } else setState('error')
    } finally {
      submissionInFlight.current = false
    }
  }

  let content: React.ReactNode
  if (pageState === 'loading') {
    content = <div style={styles.skeleton} aria-label="Loading" />
  } else if (pageState === 'select' && slug) {
    content = (
      <>
        <h1 style={styles.heading}>Reschedule Appointment</h1>
        <p style={styles.intro}>You are rescheduling your appointment with {adminName}. Select a new time below.</p>
        {pickerError && <p role="alert" style={styles.errorBanner}>{pickerError}</p>}
        <SlotPicker slug={slug} onSlotSelect={handleSlotSelect} />
      </>
    )
  } else if ((pageState === 'confirm' || pageState === 'submitting') && selectedSlot) {
    content = (
      <>
        <h1 style={styles.heading}>Confirm New Time</h1>
        <p style={styles.intro}>You are about to reschedule to:</p>
        <p style={styles.summary}>{formatSlot(selectedSlot, timezone)} <span style={styles.timezone}>(your timezone)</span></p>
        <div style={styles.actions}>
          <button type="button" style={{ ...styles.button, ...styles.primaryButton }} disabled={pageState === 'submitting'} onClick={() => void handleConfirm()}>
            {pageState === 'submitting' ? 'Rescheduling…' : 'Confirm New Time'}
          </button>
          <button type="button" style={{ ...styles.button, ...styles.secondaryButton }} disabled={pageState === 'submitting'} onClick={() => setState('select')}>
            Choose a different time
          </button>
        </div>
      </>
    )
  } else if (pageState === 'success' && result) {
    const successfulSlot = { start: result.newSlotStart, end: result.newSlotEnd }
    content = (
      <div role="status">
        <div aria-hidden="true" style={styles.successIcon}>✓</div>
        <h1 style={styles.heading}>Appointment Rescheduled</h1>
        <p style={styles.summary}>{formatSlot(successfulSlot, timezone)}</p>
        <p style={styles.intro}>You will receive a confirmation email with your updated calendar invite.</p>
        <Link to={`/cancel/${result.cancelToken}`} style={styles.cancelLink}>Cancel this appointment</Link>
      </div>
    )
  } else {
    const message = pageState === 'cancelled'
      ? 'This appointment has been cancelled and cannot be rescheduled.'
      : pageState === 'already-rescheduled'
        ? 'This appointment has already been rescheduled. Use your latest confirmation email to manage it.'
      : pageState === 'past'
        ? 'This appointment has already passed.'
        : pageState === 'invalid'
          ? 'This reschedule link is invalid.'
          : 'We could not reschedule your appointment. Please try again.'
    content = <div role="alert"><h1 style={styles.heading}>Unable to Reschedule</h1><p style={styles.intro}>{message}</p></div>
  }

  return <main style={styles.page}><section style={styles.card} aria-live="polite">{content}</section></main>
}

const styles: Record<string, React.CSSProperties> = {
  page: { minHeight: '100vh', display: 'flex', justifyContent: 'center', alignItems: 'flex-start', padding: '48px 24px', boxSizing: 'border-box' },
  card: { width: '100%', maxWidth: 680, padding: 32, border: '1px solid var(--border)', borderRadius: 12, background: 'var(--bg)', boxShadow: 'var(--shadow)', boxSizing: 'border-box', textAlign: 'left' },
  heading: { margin: '0 0 12px', fontSize: 28, fontWeight: 600, letterSpacing: '-0.4px' },
  intro: { color: 'var(--text)', fontSize: 16, lineHeight: 1.55, marginBottom: 28 },
  errorBanner: { margin: '0 0 24px', padding: '12px 14px', borderRadius: 8, background: 'rgba(220, 38, 38, 0.1)', color: '#dc2626' },
  summary: { margin: '20px 0', padding: '16px', borderRadius: 8, background: 'var(--social-bg)', color: 'var(--text-h)', fontWeight: 600, lineHeight: 1.5 },
  timezone: { color: 'var(--text)', fontWeight: 400 },
  actions: { display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 28 },
  button: { minHeight: 44, padding: '10px 18px', borderRadius: 8, font: 'inherit', fontSize: 15, fontWeight: 600, cursor: 'pointer' },
  primaryButton: { border: '1px solid #2563eb', background: '#2563eb', color: '#fff' },
  secondaryButton: { border: '1px solid var(--border)', background: 'var(--bg)', color: 'var(--text-h)' },
  skeleton: { width: 220, height: 22, margin: '80px auto', background: 'var(--border)', borderRadius: 4, animation: 'pulse 1.5s ease-in-out infinite' },
  successIcon: { width: 48, height: 48, margin: '0 0 16px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#16a34a', color: '#fff', fontSize: 24, fontWeight: 700 },
  cancelLink: { display: 'inline-block', marginTop: 8, color: '#dc2626', fontWeight: 600 },
}
