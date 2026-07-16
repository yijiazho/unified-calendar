import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import axios from 'axios'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { createBooking } from '../api/bookings'
import { useBrowserTimezone } from '../hooks/useBrowserTimezone'

interface BookingFormValues {
  visitorName: string
  visitorEmail: string
  visitorPhone: string
  notes: string
}

interface BookingFormLocationState {
  slotStart?: string
  slotEnd?: string
  adminName?: string
}

/** Formats the selected UTC slot in the visitor's local timezone for display in the form header. */
function formatSelectedSlot(startUtc: string, endUtc: string, timezone: string): string {
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

  const timezoneName =
    new Intl.DateTimeFormat('en-US', {
      timeZone: timezone,
      timeZoneName: 'long',
    })
      .formatToParts(start)
      .find(part => part.type === 'timeZoneName')
      ?.value ?? timezone

  return `${datePart} at ${timeFormatter.format(start)} – ${timeFormatter.format(end)} (${timezoneName})`
}

/** Booking form page where a visitor confirms a selected time slot and submits contact details. */
export default function BookingFormPage() {
  const { slug } = useParams<{ slug: string }>()
  const location = useLocation()
  const navigate = useNavigate()
  const visitorTimezone = useBrowserTimezone()
  const [submitError, setSubmitError] = useState<'slot_taken' | 'generic' | null>(null)

  const { slotStart, slotEnd, adminName } =
    (location.state as BookingFormLocationState | null) ?? {}

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<BookingFormValues>({
    defaultValues: {
      visitorName: '',
      visitorEmail: '',
      visitorPhone: '',
      notes: '',
    },
  })

  const selectedSlotText = useMemo(() => {
    if (!slotStart || !slotEnd) return ''
    return formatSelectedSlot(slotStart, slotEnd, visitorTimezone)
  }, [slotEnd, slotStart, visitorTimezone])

  if (!slug) {
    return <Navigate to="/" replace />
  }

  if (!slotStart || !slotEnd) {
    return <Navigate to={`/s/${slug}`} replace />
  }

  const confirmedSlug = slug
  const confirmedSlotStart = slotStart
  const confirmedSlotEnd = slotEnd

  async function onSubmit(values: BookingFormValues) {
    setSubmitError(null)
    try {
      const response = await createBooking({
        slug: confirmedSlug,
        slotStart: confirmedSlotStart,
        slotEnd: confirmedSlotEnd,
        visitorName: values.visitorName,
        visitorEmail: values.visitorEmail,
        visitorPhone: values.visitorPhone || undefined,
        notes: values.notes || undefined,
      })

      navigate('/booking/confirm', {
        state: {
          ...response.data,
          visitorEmail: values.visitorEmail,
          adminName: response.data.adminName || adminName,
        },
      })
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 409) {
        setSubmitError('slot_taken')
      } else {
        setSubmitError('generic')
      }
    }
  }

  return (
    <div style={styles.page}>
      <div style={styles.card}>
        <h1 style={styles.title}>Confirm Your Details</h1>
        <p style={styles.slotText}>{selectedSlotText}</p>

        {submitError === 'slot_taken' && (
          <div role="alert" style={styles.errorBanner}>
            <p style={styles.errorMessage}>
              Sorry, this time slot was just taken. Please go back and choose another time.
            </p>
            <Link to={`/s/${slug}`}>Choose another time</Link>
          </div>
        )}

        {submitError === 'generic' && (
          <p role="alert" style={styles.errorBanner}>
            Something went wrong. Please try again.
          </p>
        )}

        <form onSubmit={handleSubmit(onSubmit)} noValidate style={styles.form}>
          <div style={styles.field}>
            <label htmlFor="visitorName" style={styles.label}>
              Name
            </label>
            <input
              id="visitorName"
              type="text"
              aria-invalid={!!errors.visitorName}
              style={styles.input}
              {...register('visitorName', {
                required: 'Name is required',
                maxLength: { value: 200, message: 'Name must be 200 characters or fewer' },
              })}
            />
            {errors.visitorName && (
              <p role="alert" style={styles.fieldError}>
                {errors.visitorName.message}
              </p>
            )}
          </div>

          <div style={styles.field}>
            <label htmlFor="visitorEmail" style={styles.label}>
              Email
            </label>
            <input
              id="visitorEmail"
              type="email"
              aria-invalid={!!errors.visitorEmail}
              style={styles.input}
              {...register('visitorEmail', {
                required: 'Email is required',
                pattern: {
                  value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                  message: 'Enter a valid email address',
                },
              })}
            />
            {errors.visitorEmail && (
              <p role="alert" style={styles.fieldError}>
                {errors.visitorEmail.message}
              </p>
            )}
          </div>

          <div style={styles.field}>
            <label htmlFor="visitorPhone" style={styles.label}>
              Phone (optional)
            </label>
            <input
              id="visitorPhone"
              type="tel"
              aria-invalid={!!errors.visitorPhone}
              style={styles.input}
              {...register('visitorPhone', {
                maxLength: { value: 50, message: 'Phone must be 50 characters or fewer' },
              })}
            />
            {errors.visitorPhone && (
              <p role="alert" style={styles.fieldError}>
                {errors.visitorPhone.message}
              </p>
            )}
          </div>

          <div style={styles.field}>
            <label htmlFor="notes" style={styles.label}>
              Notes (optional)
            </label>
            <textarea
              id="notes"
              rows={4}
              aria-invalid={!!errors.notes}
              style={{ ...styles.input, resize: 'vertical' as const }}
              {...register('notes', {
                maxLength: { value: 2000, message: 'Notes must be 2000 characters or fewer' },
              })}
            />
            {errors.notes && (
              <p role="alert" style={styles.fieldError}>
                {errors.notes.message}
              </p>
            )}
          </div>

          <button type="submit" disabled={isSubmitting} style={styles.submitBtn}>
            {isSubmitting ? <span style={styles.btnSpinner} aria-hidden /> : null}
            {isSubmitting ? 'Confirming…' : 'Confirm Booking'}
          </button>
        </form>
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
    padding: '32px',
    border: '1px solid var(--border)',
    borderRadius: 12,
    background: 'var(--bg)',
    boxShadow: 'var(--shadow)',
  },
  title: {
    margin: '0 0 8px',
    fontSize: 28,
    letterSpacing: '-0.4px',
  },
  slotText: {
    margin: '0 0 20px',
    color: 'var(--text)',
    fontSize: 15,
  },
  form: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 16,
  },
  field: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 6,
  },
  label: {
    fontSize: 14,
    fontWeight: 500,
    color: 'var(--text-h)',
  },
  input: {
    padding: '10px 12px',
    border: '1px solid var(--border)',
    borderRadius: 8,
    fontSize: 16,
    color: 'var(--text-h)',
    background: 'var(--bg)',
    outline: 'none',
    width: '100%',
    boxSizing: 'border-box' as const,
  },
  submitBtn: {
    marginTop: 6,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    padding: '11px 0',
    border: 'none',
    borderRadius: 8,
    background: 'var(--accent)',
    color: '#fff',
    fontSize: 16,
    fontWeight: 500,
    cursor: 'pointer',
  },
  btnSpinner: {
    display: 'inline-block',
    width: 16,
    height: 16,
    border: '2px solid rgba(255,255,255,0.35)',
    borderTopColor: '#fff',
    borderRadius: '50%',
    animation: 'spin 0.7s linear infinite',
  },
  fieldError: {
    margin: 0,
    color: '#dc2626',
    fontSize: 13,
  },
  errorBanner: {
    margin: '0 0 16px',
    padding: '10px 12px',
    borderRadius: 8,
    border: '1px solid rgba(239,68,68,0.3)',
    background: 'rgba(239,68,68,0.08)',
    color: '#dc2626',
    fontSize: 14,
  },
  errorMessage: {
    margin: '0 0 6px',
  },
}
