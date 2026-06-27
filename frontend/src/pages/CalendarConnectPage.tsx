import type * as React from 'react'
import { useState, useEffect, useCallback } from 'react'
import type { CalendarAccount } from '../types'
import {
  getAccounts,
  connectGoogle,
  connectOutlook,
  disconnectAccount,
  setPrimary,
} from '../api/calendar'
import Modal from '../components/Modal'
import Button from '../components/Button'
import SettingsSidebar from '../components/SettingsSidebar'
import Spinner from '../components/Spinner'

/** Formats an ISO timestamp as a relative time string (e.g. "3 minutes ago"). */
function relativeTime(iso: string): string {
  const ms = new Date(iso).getTime()
  if (isNaN(ms)) return 'unknown'
  const diffMs = Math.max(0, Date.now() - ms)
  const minutes = Math.floor(diffMs / 60_000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? '' : 's'} ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`
  const days = Math.floor(hours / 24)
  return `${days} day${days === 1 ? '' : 's'} ago`
}

function ProviderIcon({ provider }: { provider: 'GOOGLE' | 'OUTLOOK' }) {
  if (provider === 'GOOGLE') {
    return (
      <svg width="20" height="20" viewBox="0 0 24 24" aria-label="Google" role="img">
        <path
          d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
          fill="#4285F4"
        />
        <path
          d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
          fill="#34A853"
        />
        <path
          d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z"
          fill="#FBBC05"
        />
        <path
          d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
          fill="#EA4335"
        />
      </svg>
    )
  }
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" aria-label="Microsoft Outlook" role="img">
      <path
        d="M7 2h10a5 5 0 0 1 5 5v10a5 5 0 0 1-5 5H7a5 5 0 0 1-5-5V7a5 5 0 0 1 5-5z"
        fill="#0078D4"
      />
      <path
        d="M7 8.5c0-1.38 1.12-2.5 2.5-2.5S12 7.12 12 8.5v7C12 16.88 10.88 18 9.5 18S7 16.88 7 15.5v-7z"
        fill="#fff"
      />
      <path d="M13 9h5v2h-5V9zm0 4h5v2h-5v-2z" fill="#fff" />
    </svg>
  )
}

function AccountCard({
  account,
  onDisconnect,
  onSetPrimary,
}: {
  account: CalendarAccount
  onDisconnect: (account: CalendarAccount) => void
  onSetPrimary: (id: number) => void
}) {
  return (
    <div style={styles.card}>
      <div style={styles.cardHeader}>
        <ProviderIcon provider={account.provider} />
        <div style={styles.cardInfo}>
          <span style={styles.email}>{account.email}</span>
          {account.lastSyncError ? (
            <span style={styles.syncError}>Sync failed: {account.lastSyncError}</span>
          ) : account.lastSyncAt ? (
            <span style={styles.syncTime}>Last synced {relativeTime(account.lastSyncAt)}</span>
          ) : (
            <span style={styles.syncTime}>Connected {relativeTime(account.connectedAt)}</span>
          )}
        </div>
        {account.isPrimary && <span style={styles.primaryBadge}>Primary</span>}
      </div>
      <div style={styles.cardActions}>
        <div style={{ position: 'relative', display: 'inline-block' }}>
          <Button
            variant="secondary"
            disabled={account.isPrimary}
            onClick={() => onSetPrimary(account.id)}
            title="Bookings will be created in this calendar."
          >
            Set as Primary
          </Button>
        </div>
        <Button variant="secondary" onClick={() => onDisconnect(account)}>
          Disconnect
        </Button>
      </div>
    </div>
  )
}

/** Settings page for connecting Google/Outlook calendar accounts. */
export default function CalendarConnectPage() {
  const [accounts, setAccounts] = useState<CalendarAccount[]>([])
  const [initialLoading, setInitialLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [pendingDisconnect, setPendingDisconnect] = useState<CalendarAccount | null>(null)
  const [actionInProgress, setActionInProgress] = useState(false)

  const fetchAccounts = useCallback(async () => {
    try {
      const res = await getAccounts()
      setAccounts(res.data)
    } catch {
      setErrorMessage('Failed to load calendar accounts. Please refresh.')
    } finally {
      setInitialLoading(false)
    }
  }, [])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const error = params.get('error')
    if (error) {
      setErrorMessage('Failed to connect calendar. Please try again.')
      window.history.replaceState({}, '', window.location.pathname)
    }
    fetchAccounts()
  }, [fetchAccounts])

  async function handleSetPrimary(accountId: number) {
    if (actionInProgress) return
    setActionInProgress(true)
    try {
      await setPrimary(accountId)
      await fetchAccounts()
    } catch {
      setErrorMessage('Failed to update primary calendar. Please try again.')
    } finally {
      setActionInProgress(false)
    }
  }

  async function handleConfirmDisconnect() {
    if (!pendingDisconnect || actionInProgress) return
    setActionInProgress(true)
    try {
      await disconnectAccount(pendingDisconnect.id)
      setPendingDisconnect(null)
      await fetchAccounts()
    } catch {
      setErrorMessage('Failed to disconnect account. Please try again.')
      setPendingDisconnect(null)
    } finally {
      setActionInProgress(false)
    }
  }

  return (
    <div style={styles.layout}>
      <SettingsSidebar />

      <main style={styles.main}>
        <h2 style={{ marginBottom: 24 }}>Connected Calendars</h2>

        {errorMessage && (
          <div style={styles.errorBanner}>
            {errorMessage}
            <button type="button" aria-label="Dismiss error" style={styles.dismissBtn} onClick={() => setErrorMessage(null)}>
              ✕
            </button>
          </div>
        )}

        {initialLoading ? (
          <Spinner />
        ) : accounts.length === 0 ? (
          <p style={styles.emptyState}>
            No calendars connected yet. Connect a Google or Outlook account to get started.
          </p>
        ) : (
          <div style={styles.accountList}>
            {accounts.map((account) => (
              <AccountCard
                key={account.id}
                account={account}
                onDisconnect={setPendingDisconnect}
                onSetPrimary={handleSetPrimary}
              />
            ))}
          </div>
        )}

        <div style={styles.connectSection}>
          <h3 style={styles.connectHeading}>Connect a new account</h3>
          <div style={styles.connectButtons}>
            <Button onClick={() => { window.location.href = connectGoogle() }}>Connect Google Calendar</Button>
            <Button onClick={() => { window.location.href = connectOutlook() }}>Connect Microsoft Outlook</Button>
          </div>
        </div>
      </main>

      <Modal open={!!pendingDisconnect} onClose={() => setPendingDisconnect(null)}>
        <div style={styles.dialog}>
          <h3 style={{ marginTop: 0 }}>Disconnect account?</h3>
          <p>
            Are you sure you want to disconnect{' '}
            <strong>{pendingDisconnect?.email}</strong>? This cannot be undone.
          </p>
          <div style={styles.dialogActions}>
            <Button variant="secondary" onClick={() => setPendingDisconnect(null)} disabled={actionInProgress}>
              Cancel
            </Button>
            <Button onClick={handleConfirmDisconnect} disabled={actionInProgress}>
              {actionInProgress ? 'Disconnecting…' : 'Disconnect'}
            </Button>
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
  emptyState: {
    color: 'var(--text)',
    marginBottom: 32,
  },
  accountList: {
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
    marginBottom: 40,
  },
  card: {
    border: '1px solid var(--border)',
    borderRadius: 10,
    padding: '16px 20px',
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
  },
  cardHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
  },
  cardInfo: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
  },
  email: {
    fontWeight: 500,
    color: 'var(--text-h)',
    fontSize: 15,
  },
  syncTime: {
    fontSize: 13,
    color: 'var(--text)',
  },
  syncError: {
    fontSize: 13,
    color: '#dc2626',
  },
  primaryBadge: {
    fontSize: 12,
    fontWeight: 600,
    color: 'var(--accent)',
    background: 'var(--accent-bg)',
    border: '1px solid var(--accent-border)',
    borderRadius: 20,
    padding: '2px 10px',
    whiteSpace: 'nowrap',
  },
  cardActions: {
    display: 'flex',
    gap: 8,
  },
  connectSection: {
    borderTop: '1px solid var(--border)',
    paddingTop: 32,
    marginTop: 8,
  },
  connectHeading: {
    fontSize: 16,
    fontWeight: 500,
    color: 'var(--text-h)',
    margin: '0 0 16px',
  },
  connectButtons: {
    display: 'flex',
    gap: 12,
    flexWrap: 'wrap',
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
}
