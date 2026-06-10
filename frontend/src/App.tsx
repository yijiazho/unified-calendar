import type { ReactNode } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import Spinner from './components/Spinner'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import DashboardPage from './pages/DashboardPage'
import CalendarConnectPage from './pages/CalendarConnectPage'
import WorkingHoursPage from './pages/WorkingHoursPage'
import PublicSchedulePage from './pages/PublicSchedulePage'
import BookingFormPage from './pages/BookingFormPage'
import BookingConfirmPage from './pages/BookingConfirmPage'
import CancelPage from './pages/CancelPage'
import ReschedulePage from './pages/ReschedulePage'

/** Wraps a route and redirects to /login when no session is active. */
function ProtectedRoute({ children }: { children: ReactNode }) {
  const { admin, loading } = useAuth()
  if (loading) return <Spinner />
  if (!admin) return <Navigate to="/login" replace />
  return <>{children}</>
}

/** Redirects root to /dashboard if authenticated, otherwise to /login. */
function RootRedirect() {
  const { admin, loading } = useAuth()
  if (loading) return <Spinner />
  return <Navigate to={admin ? '/dashboard' : '/login'} replace />
}

function Router() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<RootRedirect />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <DashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/settings/calendars"
          element={
            <ProtectedRoute>
              <CalendarConnectPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/settings/hours"
          element={
            <ProtectedRoute>
              <WorkingHoursPage />
            </ProtectedRoute>
          }
        />
        <Route path="/s/:slug" element={<PublicSchedulePage />} />
        <Route path="/book/:slug" element={<BookingFormPage />} />
        <Route path="/booking/confirm" element={<BookingConfirmPage />} />
        <Route path="/cancel/:token" element={<CancelPage />} />
        <Route path="/reschedule/:token" element={<ReschedulePage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <Router />
    </AuthProvider>
  )
}
