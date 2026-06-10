/** Full-page loading indicator shown while auth state resolves. */
export default function Spinner() {
  return (
    <div
      role="status"
      aria-label="Loading"
      style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}
    >
      <div
        style={{
          width: 32,
          height: 32,
          border: '3px solid #e5e7eb',
          borderTopColor: '#6366f1',
          borderRadius: '50%',
          animation: 'spin 0.7s linear infinite',
        }}
      />
    </div>
  )
}
