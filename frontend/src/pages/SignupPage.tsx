import { useNavigate, Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import axios from 'axios'
import { signup } from '../api/auth'
import { useBrowserTimezone } from '../hooks/useBrowserTimezone'

interface SignupFormData {
  email: string
  password: string
  slug: string
  timezone: string
}

const TIMEZONES: string[] =
  typeof Intl.supportedValuesOf === 'function'
    ? Intl.supportedValuesOf('timeZone')
    : [
        'Pacific/Honolulu',
        'America/Anchorage',
        'America/Los_Angeles',
        'America/Denver',
        'America/Chicago',
        'America/New_York',
        'America/Sao_Paulo',
        'Europe/London',
        'Europe/Paris',
        'Europe/Berlin',
        'Europe/Moscow',
        'Asia/Dubai',
        'Asia/Kolkata',
        'Asia/Bangkok',
        'Asia/Shanghai',
        'Asia/Tokyo',
        'Australia/Sydney',
        'Pacific/Auckland',
      ]

/** Public signup page for first-time admin registration. */
export default function SignupPage() {
  const navigate = useNavigate()
  const browserTimezone = useBrowserTimezone()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SignupFormData>({ defaultValues: { timezone: browserTimezone } })

  async function onSubmit(data: SignupFormData) {
    try {
      await signup(data.email, data.password, data.slug, data.timezone)
      navigate('/login', { state: { successMessage: 'Account created — please sign in.' } })
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status
        if (status === 409) {
          setError('email', { message: 'Email already in use' })
        } else if (status === 400) {
          setError('slug', {
            message: 'Slug can only contain lowercase letters, numbers, and hyphens',
          })
        } else {
          setError('root', { message: 'An unexpected error occurred. Please try again.' })
        }
      } else {
        setError('root', { message: 'An unexpected error occurred. Please try again.' })
      }
    }
  }

  return (
    <div style={styles.page}>
      <div style={styles.card}>
        <h1 style={styles.title}>Create account</h1>

        <form onSubmit={handleSubmit(onSubmit)} noValidate style={styles.form}>
          {errors.root && (
            <p role="alert" style={styles.rootError}>
              {errors.root.message}
            </p>
          )}

          <div style={styles.field}>
            <label htmlFor="email" style={styles.label}>
              Email
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              style={styles.input}
              aria-invalid={!!errors.email}
              {...register('email', {
                required: 'Email is required',
                pattern: {
                  value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                  message: 'Enter a valid email address',
                },
              })}
            />
            {errors.email && (
              <p role="alert" style={styles.fieldError}>
                {errors.email.message}
              </p>
            )}
          </div>

          <div style={styles.field}>
            <label htmlFor="password" style={styles.label}>
              Password
            </label>
            <input
              id="password"
              type="password"
              autoComplete="new-password"
              style={styles.input}
              aria-invalid={!!errors.password}
              {...register('password', {
                required: 'Password is required',
                minLength: { value: 8, message: 'Password must be at least 8 characters' },
              })}
            />
            {errors.password && (
              <p role="alert" style={styles.fieldError}>
                {errors.password.message}
              </p>
            )}
          </div>

          <div style={styles.field}>
            <label htmlFor="slug" style={styles.label}>
              Slug
            </label>
            <input
              id="slug"
              type="text"
              autoComplete="off"
              placeholder="your-name"
              style={styles.input}
              aria-invalid={!!errors.slug}
              {...register('slug', {
                required: 'Slug is required',
                minLength: { value: 3, message: 'Slug must be at least 3 characters' },
                maxLength: { value: 50, message: 'Slug must be 50 characters or fewer' },
                pattern: {
                  value: /^[a-z0-9-]+$/,
                  message: 'Slug can only contain lowercase letters, numbers, and hyphens',
                },
              })}
            />
            {errors.slug && (
              <p role="alert" style={styles.fieldError}>
                {errors.slug.message}
              </p>
            )}
          </div>

          <div style={styles.field}>
            <label htmlFor="timezone" style={styles.label}>
              Timezone
            </label>
            <select
              id="timezone"
              style={{ ...styles.input, ...styles.select }}
              aria-invalid={!!errors.timezone}
              {...register('timezone', { required: 'Timezone is required' })}
            >
              {TIMEZONES.map(tz => (
                <option key={tz} value={tz}>
                  {tz}
                </option>
              ))}
            </select>
            {errors.timezone && (
              <p role="alert" style={styles.fieldError}>
                {errors.timezone.message}
              </p>
            )}
          </div>

          <button type="submit" disabled={isSubmitting} style={styles.submitBtn}>
            {isSubmitting ? <span style={styles.btnSpinner} aria-hidden /> : null}
            {isSubmitting ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <p style={styles.switchLink}>
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  )
}

const styles = {
  page: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    minHeight: '100vh',
    padding: '24px',
    boxSizing: 'border-box' as const,
  },
  card: {
    width: '100%',
    maxWidth: 400,
    padding: '40px 32px',
    border: '1px solid var(--border)',
    borderRadius: 12,
    background: 'var(--bg)',
    boxShadow: 'var(--shadow)',
    textAlign: 'left' as const,
  },
  title: {
    margin: '0 0 28px',
    fontSize: 28,
    letterSpacing: '-0.5px',
  },
  form: { display: 'flex', flexDirection: 'column' as const, gap: 20 },
  field: { display: 'flex', flexDirection: 'column' as const, gap: 6 },
  label: { fontSize: 14, fontWeight: 500, color: 'var(--text-h)' },
  input: {
    padding: '10px 12px',
    border: '1px solid var(--border)',
    borderRadius: 8,
    fontSize: 16,
    background: 'var(--bg)',
    color: 'var(--text-h)',
    outline: 'none',
    width: '100%',
    boxSizing: 'border-box' as const,
  },
  select: { cursor: 'pointer' },
  rootError: {
    margin: 0,
    padding: '10px 12px',
    background: 'rgba(239,68,68,0.08)',
    border: '1px solid rgba(239,68,68,0.3)',
    borderRadius: 8,
    color: '#dc2626',
    fontSize: 14,
  },
  fieldError: { margin: 0, fontSize: 13, color: '#dc2626' },
  submitBtn: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    padding: '11px 0',
    background: 'var(--accent)',
    color: '#fff',
    border: 'none',
    borderRadius: 8,
    fontSize: 16,
    fontWeight: 500,
    cursor: 'pointer',
    marginTop: 4,
  },
  btnSpinner: {
    display: 'inline-block',
    width: 16,
    height: 16,
    border: '2px solid rgba(255,255,255,0.4)',
    borderTopColor: '#fff',
    borderRadius: '50%',
    animation: 'spin 0.7s linear infinite',
  },
  switchLink: { marginTop: 24, textAlign: 'center' as const, fontSize: 14 },
}
