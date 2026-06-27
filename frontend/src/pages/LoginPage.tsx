import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import axios from 'axios'
import { useAuth } from '../context/AuthContext'
import Spinner from '../components/Spinner'

interface LoginFormData {
  email: string
  password: string
}

/** Public login page for admin authentication. */
export default function LoginPage() {
  const { admin, loading, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormData>()

  // Covers the case where the user navigates to /login while already holding a valid session.
  // Post-login navigation is handled inside AuthContext.login() via useNavigate.
  useEffect(() => {
    if (!loading && admin) navigate('/dashboard', { replace: true })
  }, [admin, loading, navigate])

  const successMessage = (location.state as { successMessage?: string } | null)?.successMessage

  async function onSubmit(data: LoginFormData) {
    try {
      await login(data.email, data.password)
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        setError('root', { message: 'Invalid email or password' })
      } else {
        setError('root', { message: 'An unexpected error occurred. Please try again.' })
      }
    }
  }

  if (loading) return <Spinner />

  return (
    <div style={styles.page}>
      <div style={styles.card}>
        <h1 style={styles.title}>Sign in</h1>

        {successMessage && (
          <p role="status" style={styles.successBanner}>
            {successMessage}
          </p>
        )}

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
              autoComplete="current-password"
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

          <button type="submit" disabled={isSubmitting} style={styles.submitBtn}>
            {isSubmitting ? <span style={styles.btnSpinner} aria-hidden /> : null}
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p style={styles.switchLink}>
          Don&apos;t have an account? <Link to="/signup">Sign up</Link>
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
  successBanner: {
    margin: '0 0 20px',
    padding: '10px 12px',
    background: 'rgba(34,197,94,0.08)',
    border: '1px solid rgba(34,197,94,0.3)',
    borderRadius: 8,
    color: '#16a34a',
    fontSize: 14,
  },
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
