import type * as React from 'react'
import { useState, useEffect, useCallback, useRef } from 'react'
import { useBlocker } from 'react-router-dom'
import type { WorkingHours } from '../types'
import { getWorkingHours, saveWorkingHours } from '../api/workingHours'
import Button from '../components/Button'
import Modal from '../components/Modal'
import SettingsSidebar from '../components/SettingsSidebar'
import Spinner from '../components/Spinner'

const DAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

interface DayRow {
  enabled: boolean
  startTime: string
  endTime: string
}

type FormState = DayRow[]

function buildFormState(saved: WorkingHours[]): FormState {
  return DAY_NAMES.map((_, i) => {
    const match = saved.find(h => h.dayOfWeek === i)
    return match
      ? { enabled: true, startTime: match.startTime, endTime: match.endTime }
      : { enabled: false, startTime: '09:00', endTime: '17:00' }
  })
}

function stateToPayload(form: FormState): WorkingHours[] {
  return form.flatMap((row, i) =>
    row.enabled ? [{ dayOfWeek: i, startTime: row.startTime, endTime: row.endTime }] : []
  )
}

/** Validates that every enabled day has startTime < endTime (lexicographic HH:MM). */
function validate(form: FormState): string | null {
  for (let i = 0; i < form.length; i++) {
    const row = form[i]
    if (row.enabled && row.startTime >= row.endTime) {
      return `${DAY_NAMES[i]}: start time must be before end time.`
    }
  }
  return null
}

/** Settings page for configuring per-weekday availability windows. */
export default function WorkingHoursPage() {
  const [form, setForm] = useState<FormState>(() => buildFormState([]))
  const [savedForm, setSavedForm] = useState<FormState>(() => buildFormState([]))
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [saving, setSaving] = useState(false)
  const [savedOnce, setSavedOnce] = useState(false)
  const [validationError, setValidationError] = useState<string | null>(null)
  const [apiError, setApiError] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const isDirty = JSON.stringify(form) !== JSON.stringify(savedForm)

  // Blocks React Router SPA navigation when there are unsaved changes.
  const blocker = useBlocker(isDirty)

  const showToast = useCallback((msg: string) => {
    setToast(msg)
    if (toastTimer.current) clearTimeout(toastTimer.current)
    toastTimer.current = setTimeout(() => setToast(null), 3000)
  }, [])

  useEffect(() => {
    getWorkingHours()
      .then(res => {
        const state = buildFormState(res.data)
        setForm(state)
        setSavedForm(state)
      })
      .catch(() => {
        setApiError('Failed to load working hours. Please refresh.')
        setLoadFailed(true)
      })
      .finally(() => setLoading(false))

    return () => {
      if (toastTimer.current) clearTimeout(toastTimer.current)
    }
  }, [])

  useEffect(() => {
    if (!isDirty) return
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [isDirty])

  function updateRow(index: number, patch: Partial<DayRow>) {
    setForm(prev => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)))
    setValidationError(null)
    setApiError(null)
  }

  function handleToggle(index: number, checked: boolean) {
    updateRow(index, { enabled: checked })
  }

  async function handleSave() {
    if (saving) return
    const err = validate(form)
    if (err) {
      setValidationError(err)
      return
    }
    setSaving(true)
    setApiError(null)
    try {
      await saveWorkingHours(stateToPayload(form))
      setSavedForm(form)
      setSavedOnce(true)
      showToast('Changes saved')
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { error?: string } } })?.response?.data?.error ??
        'Failed to save. Please try again.'
      setApiError(msg)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div style={styles.layout}>
      <SettingsSidebar />

      <main style={styles.main}>
        <h2 style={{ marginBottom: 24 }}>Working Hours</h2>

        {loading ? (
          <Spinner />
        ) : (
          <>
            {apiError && (
              <div style={styles.errorBanner}>
                {apiError}
                <button
                  type="button"
                  aria-label="Dismiss error"
                  style={styles.dismissBtn}
                  onClick={() => setApiError(null)}
                >
                  ✕
                </button>
              </div>
            )}

            <div style={styles.dayList}>
              {DAY_NAMES.map((name, i) => {
                const row = form[i]
                return (
                  <div key={name} style={styles.dayRow}>
                    <label style={styles.checkLabel}>
                      <input
                        type="checkbox"
                        checked={row.enabled}
                        onChange={e => handleToggle(i, e.target.checked)}
                        style={styles.checkbox}
                      />
                      <span style={styles.dayName}>{name}</span>
                    </label>

                    <div
                      style={{
                        ...styles.timeInputs,
                        opacity: row.enabled ? 1 : 0.35,
                        pointerEvents: row.enabled ? 'auto' : 'none',
                      }}
                    >
                      <input
                        type="time"
                        value={row.startTime}
                        disabled={!row.enabled}
                        aria-label={`${name} start time`}
                        onChange={e => updateRow(i, { startTime: e.target.value })}
                        style={styles.timeInput}
                      />
                      <span style={styles.timeSep}>–</span>
                      <input
                        type="time"
                        value={row.endTime}
                        disabled={!row.enabled}
                        aria-label={`${name} end time`}
                        onChange={e => updateRow(i, { endTime: e.target.value })}
                        style={styles.timeInput}
                      />
                    </div>
                  </div>
                )
              })}
            </div>

            {validationError && <p style={styles.inlineError}>{validationError}</p>}

            <div style={styles.saveRow}>
              <Button onClick={handleSave} disabled={saving || loadFailed}>
                {saving ? 'Saving…' : 'Save'}
              </Button>
              {(isDirty || savedOnce) && (
                <span style={{ ...styles.statusLabel, color: isDirty ? '#b45309' : '#16a34a' }}>
                  {isDirty ? 'Unsaved changes' : 'Changes saved'}
                </span>
              )}
            </div>
          </>
        )}
      </main>

      {toast && <div style={styles.toast}>{toast}</div>}

      <Modal open={blocker.state === 'blocked'} onClose={() => blocker.reset?.()}>
        <div style={styles.dialog}>
          <h3 style={{ marginTop: 0 }}>Unsaved changes</h3>
          <p>You have unsaved changes. Leave anyway?</p>
          <div style={styles.dialogActions}>
            <Button variant="secondary" onClick={() => blocker.reset?.()}>
              Stay
            </Button>
            <Button onClick={() => blocker.proceed?.()}>Leave</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  layout: {
    display: 'flex',
    minHeight: '100svh',
    textAlign: 'left',
  },
  main: {
    flex: 1,
    padding: '32px 40px',
    maxWidth: 680,
  },
  errorBanner: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    background: '#fef2f2',
    border: '1px solid #fecaca',
    color: '#dc2626',
    borderRadius: 8,
    padding: '12px 16px',
    marginBottom: 24,
    fontSize: 14,
  },
  dismissBtn: {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    color: '#dc2626',
    fontSize: 16,
    lineHeight: 1,
    padding: 0,
    marginLeft: 12,
  },
  dayList: {
    display: 'flex',
    flexDirection: 'column',
    gap: 0,
    marginBottom: 24,
    border: '1px solid var(--border)',
    borderRadius: 10,
    overflow: 'hidden',
  },
  dayRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 24,
    padding: '14px 20px',
    borderBottom: '1px solid var(--border)',
    background: 'var(--bg)',
  },
  checkLabel: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    cursor: 'pointer',
    width: 140,
    flexShrink: 0,
  },
  checkbox: {
    width: 16,
    height: 16,
    cursor: 'pointer',
    accentColor: 'var(--accent)',
    flexShrink: 0,
  },
  dayName: {
    fontSize: 15,
    fontWeight: 500,
    color: 'var(--text-h)',
    userSelect: 'none',
  },
  timeInputs: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    transition: 'opacity 0.15s',
  },
  timeSep: {
    color: 'var(--text)',
    fontSize: 15,
  },
  timeInput: {
    padding: '6px 10px',
    border: '1px solid var(--border)',
    borderRadius: 6,
    fontSize: 14,
    background: 'var(--bg)',
    color: 'var(--text-h)',
    fontFamily: 'inherit',
  },
  inlineError: {
    color: '#dc2626',
    fontSize: 14,
    margin: '0 0 16px',
  },
  saveRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 16,
  },
  statusLabel: {
    fontSize: 13,
    fontWeight: 500,
  },
  dialog: {
    background: 'var(--bg)',
    border: '1px solid var(--border)',
    borderRadius: 12,
    padding: '28px 32px',
    width: 420,
    maxWidth: '90vw',
    boxShadow: 'var(--shadow)',
  },
  dialogActions: {
    display: 'flex',
    gap: 8,
    justifyContent: 'flex-end',
    marginTop: 24,
  },
  toast: {
    position: 'fixed',
    bottom: 32,
    left: '50%',
    transform: 'translateX(-50%)',
    background: '#16a34a',
    color: '#fff',
    borderRadius: 8,
    padding: '10px 20px',
    fontSize: 14,
    fontWeight: 500,
    boxShadow: '0 4px 16px rgba(0,0,0,0.15)',
    zIndex: 1000,
    whiteSpace: 'nowrap',
  },
}
