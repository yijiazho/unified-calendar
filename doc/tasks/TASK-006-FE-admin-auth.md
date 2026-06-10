# TASK-006 · FE · Admin Authentication UI

## Context

Provides the login and signup pages, auth state management, and route protection for the admin portal. Uses the session cookie set by the backend — the frontend never handles tokens directly.

Depends on: TASK-003 (scaffold), TASK-005 (backend auth endpoints).

---

## Instructions

### Auth API calls (`src/api/auth.ts`)

```ts
import client from './client'

export const signup = (data: { email: string; password: string; slug: string; timezone: string }) =>
  client.post('/auth/signup', data)

export const login = (data: { email: string; password: string }) =>
  client.post('/auth/login', data)

export const logout = () =>
  client.post('/auth/logout')

export const getMe = () =>
  client.get<Admin>('/auth/me')
```

### Auth context (`src/hooks/useAuth.ts`)

Create an `AuthProvider` + `useAuth` hook using React Context:

```ts
interface AuthContextValue {
  admin: Admin | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
}
```

On mount, `AuthProvider` calls `GET /auth/me`:
- Success → set `admin` state.
- 401 → set `admin = null` (user is not logged in).

After `login()` succeeds, update `admin` from the response and navigate to `/dashboard`.
After `logout()`, clear `admin` and navigate to `/login`.

### `<ProtectedRoute>` component

```tsx
function ProtectedRoute({ children }: { children: ReactNode }) {
  const { admin, loading } = useAuth()
  if (loading) return <Spinner />
  if (!admin)  return <Navigate to="/login" replace />
  return <>{children}</>
}
```

Wrap all admin-only routes in `App.tsx` with this component.

### Login page (`src/pages/LoginPage.tsx`)

Form fields: `email` (type=email), `password` (type=password).

Use `react-hook-form` for validation:
- `email`: required, valid email format.
- `password`: required, min 8 chars.

On submit: call `login()` from `useAuth`.
On error (401): display `"Invalid email or password"` inline (not alert).
Show a loading spinner on the submit button while the request is in flight.
Include a link to `/signup`.

### Signup page (`src/pages/SignupPage.tsx`)

Form fields: `email`, `password`, `slug`, `timezone`.

- `slug`: pattern `^[a-z0-9-]+$`, min 3 chars, max 50.
- `timezone`: a `<select>` pre-populated with common IANA timezone strings (use `Intl.supportedValuesOf('timeZone')` if available, otherwise a hardcoded list). Pre-select the browser's current timezone via `Intl.DateTimeFormat().resolvedOptions().timeZone`.
- On 409 (email taken): show `"Email already in use"`.
- On 400 (invalid slug): show `"Slug can only contain lowercase letters, numbers, and hyphens"`.
- On success: navigate to `/login` with a success message.

### Browser timezone hook (`src/hooks/useBrowserTimezone.ts`)

```ts
export function useBrowserTimezone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}
```

Reuse this hook in the signup form and the public scheduling page.

### Error handling

The base Axios interceptor in `client.ts` redirects to `/login` on 401. On other errors (4xx, 5xx), display a generic error message in the form.

---

## Acceptance Criteria

- Navigating to `/login` shows the login form; submitting with valid credentials navigates to `/dashboard`.
- Submitting with wrong credentials shows an inline error message (no page reload).
- Navigating to `/dashboard` without a session redirects to `/login`.
- After refreshing the page, the session persists (auth state restored via `GET /auth/me`).
- The signup page pre-selects the browser's timezone.
- A successful signup redirects to `/login`.
- Logout clears the session and redirects to `/login`.
- No credentials or tokens are stored in `localStorage` or `sessionStorage`.
