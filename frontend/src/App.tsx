import { createBrowserRouter, RouterProvider, Navigate, Outlet } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import AppLayout from './components/AppLayout'
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

/** Layout component that provides auth state; must be inside the router for useNavigate to work. */
function AuthLayout() {
  return (
    <AuthProvider>
      <Outlet />
    </AuthProvider>
  )
}

/** Redirects unauthenticated visitors to /login; renders child routes otherwise. */
function ProtectedRoute() {
  const { admin, loading } = useAuth()
  if (loading) return <Spinner />
  if (!admin) return <Navigate to="/login" replace />
  return <Outlet />
}

/** Redirects root to /dashboard if authenticated, otherwise to /login. */
function RootRedirect() {
  const { admin, loading } = useAuth()
  if (loading) return <Spinner />
  return <Navigate to={admin ? '/dashboard' : '/login'} replace />
}

const router = createBrowserRouter([
  {
    element: <AuthLayout />,
    children: [
      { path: '/', element: <RootRedirect /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupPage /> },
      {
        element: <ProtectedRoute />,
        children: [
          {
            element: <AppLayout />,
            children: [
              { path: '/dashboard', element: <DashboardPage /> },
              { path: '/settings/calendars', element: <CalendarConnectPage /> },
              { path: '/settings/hours', element: <WorkingHoursPage /> },
            ],
          },
        ],
      },
    ],
  },
  { path: '/s/:slug', element: <PublicSchedulePage /> },
  { path: '/book/:slug', element: <BookingFormPage /> },
  { path: '/booking/confirm', element: <BookingConfirmPage /> },
  { path: '/cancel/:token', element: <CancelPage /> },
  { path: '/reschedule/:token', element: <ReschedulePage /> },
])

export default function App() {
  return <RouterProvider router={router} />
}
