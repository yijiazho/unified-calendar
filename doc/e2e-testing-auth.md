# Admin Auth — End-to-End Testing Guide (Docker)

## Prerequisites

| Requirement | Check |
|---|---|
| Docker and Docker Compose installed | `docker compose version` |
| `.env` file at repo root populated | `ENCRYPTION_SECRET_KEY`, `GOOGLE_*`, etc. already set |
| Port 80 free on the host | `lsof -i :80` returns nothing |

---

## Starting the Stack

```bash
# From repo root
docker compose up --build
```

Wait for the log line confirming the backend is healthy before testing:

```
unified-calendar-frontend-1  | /docker-entrypoint.sh: Configuration complete; ready for start up
```

(The frontend container only starts after the backend passes its healthcheck at `GET /actuator/health`.)

Open **http://localhost** in a browser.

---

## Test Cases

### 1 — Signup creates an account and redirects to login

1. Navigate to `http://localhost/signup`.
2. Fill in all fields:
   - **Email**: `admin@example.com`
   - **Password**: `password123`
   - **Slug**: `my-admin`
   - **Timezone**: leave as pre-selected (browser timezone)
3. Submit.

**Expected**: redirected to `/login` with a green banner "Account created — please sign in."

---

### 2 — Login with valid credentials navigates to dashboard

1. On `/login`, enter the credentials from step 1.
2. Submit.

**Expected**: navigated to `/dashboard`. The browser `Application > Cookies` panel shows a `JSESSIONID` cookie with `HttpOnly`, `SameSite=Strict`, and `Secure` flags.

---

### 3 — Session persists across page refresh

1. While on `/dashboard`, press `Cmd+R` / `F5`.

**Expected**: page reloads directly to `/dashboard` without a redirect to `/login` (auth state restored from `GET /auth/me`).

---

### 4 — Protected route redirects unauthenticated visitors

1. Open a new private/incognito window.
2. Navigate directly to `http://localhost/dashboard`.

**Expected**: immediately redirected to `/login`.

---

### 5 — Invalid credentials show inline error (no page reload)

1. On `/login`, enter `admin@example.com` + `wrongpassword`.
2. Submit.

**Expected**: an inline red banner "Invalid email or password" appears. URL stays `/login`. No browser alert or page navigation.

---

### 6 — Duplicate email shows inline error on signup

1. On `/signup`, use the same email from test 1 with a different slug.
2. Submit.

**Expected**: inline error under the email field: "Email already in use". No navigation.

---

### 7 — Invalid slug format shows inline error

1. On `/signup`, enter a slug containing uppercase or spaces (e.g., `My Admin`).
2. Submit.

**Expected**: inline error under slug: "Slug can only contain lowercase letters, numbers, and hyphens".

---

### 8 — Logout clears session and redirects to login

1. While logged in, trigger logout (call `POST /api/auth/logout` directly via DevTools, or add a temporary logout button to `DashboardPage`).
2. Attempt to navigate to `/dashboard`.

**Expected**: redirected to `/login`. `JSESSIONID` cookie is gone.

---

### 9 — No credentials in localStorage or sessionStorage

1. After login, open DevTools → Application → Local Storage / Session Storage for `http://localhost`.

**Expected**: both are empty. Auth state lives entirely in the session cookie.

---

## Caveats

### Secure cookie on plain HTTP

The session cookie is set with `Secure=true` (`application.properties`). All major browsers treat `localhost` as a secure context and will accept and send Secure cookies over HTTP, so this works correctly on the local Docker setup. If you deploy to a non-localhost host over plain HTTP (e.g., a VM IP), the browser will silently drop the Secure cookie and every request will appear unauthenticated.

### CORS

Not relevant in Docker: nginx proxies `/api/*` → `backend:8080/*` as a same-origin request from the browser's perspective. The `app.cors.allowed-origins=http://localhost:5173` setting in `application.properties` only matters for local dev (Vite on :5173 calling Spring on :8080 directly).

### EncryptionConfig is a stub

`EncryptionConfig.java` is empty (planned for TASK-007). The `ENCRYPTION_SECRET_KEY` env var is required by `application.properties` but not yet consumed by any bean, so startup succeeds. Calendar-account token encryption is not active yet — this has no impact on the auth flow.

---

## Teardown

```bash
docker compose down          # stop containers, keep the SQLite volume
docker compose down -v       # stop and delete the ./data volume (fresh DB next run)
```
