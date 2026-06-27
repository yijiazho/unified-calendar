import type React from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const NAV_LINKS = [
  { to: '/dashboard', label: 'Dashboard', end: true },
  { to: '/settings/calendars', label: 'Calendars' },
  { to: '/settings/hours', label: 'Working Hours' },
]

/** Persistent top navigation bar for all protected pages. */
export default function AppLayout() {
  const { admin, logout } = useAuth()

  return (
    <div style={styles.root}>
      <nav style={styles.nav} aria-label="Main navigation">
        <div style={styles.navLinks}>
          {NAV_LINKS.map(({ to, label, end }) => (
            <NavLink key={to} to={to} end={end} style={navLinkStyle}>
              {label}
            </NavLink>
          ))}
        </div>
        <div style={styles.navEnd}>
          {admin && <span style={styles.userEmail}>{admin.email}</span>}
          <button style={styles.signOutBtn} onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </nav>
      <div style={styles.content}>
        <Outlet />
      </div>
    </div>
  )
}

function navLinkStyle({ isActive }: { isActive: boolean }): React.CSSProperties {
  return {
    padding: '6px 12px',
    borderRadius: 6,
    textDecoration: 'none',
    fontSize: 14,
    fontWeight: isActive ? 600 : 400,
    color: isActive ? 'var(--accent)' : 'var(--text)',
    background: isActive ? 'var(--accent-bg)' : 'transparent',
    transition: 'background 0.15s, color 0.15s',
  }
}

const styles: Record<string, React.CSSProperties> = {
  root: {
    display: 'flex',
    flexDirection: 'column',
    flex: 1,
    minHeight: 0,
  },
  nav: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '0 24px',
    height: 'var(--nav-h)',
    borderBottom: '1px solid var(--border)',
    flexShrink: 0,
    gap: 8,
  },
  navLinks: {
    display: 'flex',
    alignItems: 'center',
    gap: 4,
  },
  navEnd: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
  },
  userEmail: {
    fontSize: 13,
    color: 'var(--text)',
    maxWidth: 220,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  signOutBtn: {
    background: 'none',
    border: '1px solid var(--border)',
    borderRadius: 6,
    padding: '5px 12px',
    fontSize: 13,
    color: 'var(--text)',
    cursor: 'pointer',
    fontFamily: 'inherit',
  },
  content: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    minHeight: 0,
  },
}
