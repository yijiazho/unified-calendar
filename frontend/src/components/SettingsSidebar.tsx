import type * as React from 'react'
import { NavLink } from 'react-router-dom'

/** Shared sidebar navigation for admin settings pages. */
export default function SettingsSidebar() {
  return (
    <nav style={styles.sidebar}>
      <p style={styles.label}>Settings</p>
      <NavLink to="/settings/calendars" style={navStyle}>
        Calendars
      </NavLink>
      <NavLink to="/settings/hours" style={navStyle}>
        Working Hours
      </NavLink>
      <NavLink to="/dashboard" style={navStyle}>
        Dashboard
      </NavLink>
    </nav>
  )
}

function navStyle({ isActive }: { isActive: boolean }): React.CSSProperties {
  return {
    display: 'block',
    padding: '8px 12px',
    borderRadius: 6,
    textDecoration: 'none',
    color: isActive ? 'var(--accent)' : 'var(--text)',
    background: isActive ? 'var(--accent-bg)' : 'transparent',
    fontWeight: isActive ? 500 : 400,
  }
}

const styles: Record<string, React.CSSProperties> = {
  sidebar: {
    width: 200,
    borderRight: '1px solid var(--border)',
    padding: '32px 16px',
    flexShrink: 0,
    display: 'flex',
    flexDirection: 'column',
    gap: 4,
  },
  label: {
    fontSize: 12,
    fontWeight: 600,
    letterSpacing: '0.08em',
    textTransform: 'uppercase',
    color: 'var(--text)',
    margin: '0 0 8px',
  },
}
