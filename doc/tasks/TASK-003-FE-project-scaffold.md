# TASK-003 · FE · React Frontend Scaffold

## Context

Establishes the frontend skeleton that all other frontend tasks build on. Uses Vite for fast local development with a proxy to the Spring Boot backend, eliminating CORS issues during development.

Must be completed before any other frontend task.

---

## Instructions

### Initialize the project

```bash
cd unified-calendar
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
```

### Additional dependencies

```bash
# Routing
npm install react-router-dom

# HTTP client
npm install axios

# Calendar UI
npm install @fullcalendar/react @fullcalendar/daygrid @fullcalendar/timegrid @fullcalendar/interaction

# Date handling
npm install dayjs

# Form state (lightweight)
npm install react-hook-form
```

### `vite.config.ts`

Add a dev proxy so API calls from the browser go to the backend without CORS issues:

```ts
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
```

All Axios calls in the frontend use `/api/...` as the base path. In Docker, nginx handles the same rewrite (see TASK-001).

### Folder structure

```
frontend/src/
├── api/            ← Axios instances and per-feature request functions
│   ├── client.ts   ← base Axios instance (baseURL = /api, withCredentials = true)
│   ├── auth.ts
│   ├── calendar.ts
│   ├── availability.ts
│   └── bookings.ts
├── components/     ← Shared UI components (Button, Modal, Spinner, etc.)
├── pages/
│   ├── LoginPage.tsx
│   ├── SignupPage.tsx
│   ├── DashboardPage.tsx      ← unified calendar view
│   ├── CalendarConnectPage.tsx
│   ├── WorkingHoursPage.tsx
│   ├── PublicSchedulePage.tsx ← /s/:slug
│   ├── BookingFormPage.tsx
│   ├── BookingConfirmPage.tsx
│   ├── CancelPage.tsx         ← /cancel/:token
│   └── ReschedulePage.tsx     ← /reschedule/:token
├── hooks/
│   ├── useAuth.ts
│   └── useBrowserTimezone.ts
├── types/
│   └── index.ts    ← shared TypeScript interfaces
├── App.tsx         ← router setup
└── main.tsx
```

### `src/api/client.ts`

```ts
import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
  withCredentials: true,  // send session cookie with every request
})

client.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default client
```

### `src/App.tsx` — route layout

```
/login              → LoginPage (public)
/signup             → SignupPage (public)
/dashboard          → DashboardPage (protected)
/settings/calendars → CalendarConnectPage (protected)
/settings/hours     → WorkingHoursPage (protected)
/s/:slug            → PublicSchedulePage (public)
/book/:slug         → BookingFormPage (public)
/booking/confirm    → BookingConfirmPage (public)
/cancel/:token      → CancelPage (public)
/reschedule/:token  → ReschedulePage (public)
/                   → redirect to /dashboard if logged in, else /login
```

Create a `<ProtectedRoute>` component that checks auth state and redirects to `/login` if unauthenticated.

### TypeScript interfaces (`src/types/index.ts`)

Define at minimum:

```ts
interface Admin { id: number; email: string; slug: string; timezone: string }
interface CalendarAccount { id: number; provider: 'GOOGLE' | 'OUTLOOK'; email: string; isPrimary: boolean }
interface CalendarEvent { id: number; title: string; start: string; end: string; provider: string }
interface WorkingHours { dayOfWeek: number; startTime: string; endTime: string }
interface TimeSlot { start: string; end: string }
interface Booking { id: number; visitorName: string; visitorEmail: string; status: string; cancelToken: string; rescheduleToken: string }
```

### Dockerfile

Multi-stage build (see TASK-001 for full specification):
- Stage 1: `node:20-alpine` → `npm ci && npm run build`
- Stage 2: `nginx:alpine` → copy `dist/`, copy `nginx.conf`

---

## Acceptance Criteria

- `npm run dev` starts at `http://localhost:5173` without errors.
- `npm run build` produces a `dist/` folder with no TypeScript errors.
- Navigating to `/login` renders without crashing.
- All defined routes render without a white screen.
- An Axios request to `/api/actuator/health` proxies to the backend and returns 200 during local dev (backend must be running).
- `npm run lint` (if configured) produces no errors.
