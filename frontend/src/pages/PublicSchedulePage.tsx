import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getAdminInfo } from '../api/availability'
import SlotPicker from '../components/SlotPicker'
import type { AdminPublicInfo, TimeSlot } from '../types'

/** Public-facing availability page for a given admin slug (/s/:slug). */
export default function PublicSchedulePage() {
  const { slug } = useParams<{ slug: string }>()
  const navigate = useNavigate()
  const [adminInfo, setAdminInfo] = useState<AdminPublicInfo | null>(null)
  const [adminNotFound, setAdminNotFound] = useState(false)
  const [adminLoadError, setAdminLoadError] = useState(false)

  useEffect(() => {
    if (!slug) return
    getAdminInfo(slug)
      .then(response => setAdminInfo(response.data))
      .catch(error => {
        if (error?.response?.status === 404) setAdminNotFound(true)
        else setAdminLoadError(true)
      })
  }, [slug])

  useEffect(() => {
    if (adminInfo) document.title = `Book with ${adminInfo.name}`
    return () => { document.title = 'Unified Calendar' }
  }, [adminInfo])

  function handleSlotSelect(slot: TimeSlot) {
    navigate(`/book/${slug}`, {
      state: { slotStart: slot.start, slotEnd: slot.end, adminName: adminInfo!.name },
    })
  }

  if (adminLoadError) return <CenteredMessage message="Something went wrong. Please try again." />
  if (adminNotFound) return <CenteredMessage message="This scheduling page doesn't exist." />
  if (!adminInfo || !slug) return <div style={styles.centered}><div style={styles.skeleton} aria-label="Loading" /></div>

  return (
    <main style={styles.page}>
      <h1 style={styles.heading}>{adminInfo.name}</h1>
      <p style={styles.subtitle}>Select a date and time to book an appointment.</p>
      <SlotPicker slug={slug} onSlotSelect={handleSlotSelect} />
    </main>
  )
}

function CenteredMessage({ message }: { message: string }) {
  return <div style={styles.centered}><p style={styles.errorText}>{message}</p></div>
}

const styles: Record<string, React.CSSProperties> = {
  page: { maxWidth: 640, width: '100%', margin: '0 auto', padding: '2rem 1rem', boxSizing: 'border-box', fontFamily: 'system-ui, sans-serif', textAlign: 'left' },
  centered: { display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh', fontFamily: 'system-ui, sans-serif' },
  heading: { fontSize: '1.75rem', fontWeight: 700, margin: '0 0 0.5rem' },
  subtitle: { color: 'var(--text)', marginBottom: '2rem' },
  skeleton: { width: 200, height: 20, background: 'var(--border)', borderRadius: 4, animation: 'pulse 1.5s ease-in-out infinite' },
  errorText: { color: '#dc2626' },
}
